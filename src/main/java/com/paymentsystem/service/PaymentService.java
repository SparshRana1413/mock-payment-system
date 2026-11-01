package com.paymentsystem.service;

import com.paymentsystem.bank.BankClient;
import com.paymentsystem.bank.BankRetryExhaustedException;
import com.paymentsystem.bank.BankRetryTimeoutException;
import com.paymentsystem.dto.CaptureRequest;
import com.paymentsystem.dto.CaptureResponse;
import com.paymentsystem.dto.CreditRequest;
import com.paymentsystem.dto.CreditResponse;
import com.paymentsystem.dto.HoldRequest;
import com.paymentsystem.dto.HoldResponse;
import com.paymentsystem.dto.PaymentRequest;
import com.paymentsystem.dto.ReleaseRequest;
import com.paymentsystem.dto.ReverseRequest;
import com.paymentsystem.dto.ReverseResponse;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.Transaction;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.repository.TransactionRepository;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Core orchestration service for two-leg payments across source and destination bank accounts.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Business Logic / Service layer. Orchestrates multi-phase payment settlement:
 * <ol>
 *   <li>Idempotency check against PostgreSQL.</li>
 *   <li>Initialization of a {@code PENDING} transaction entity.</li>
 *   <li>Source account fund authorization via {@link BankClient#hold}.</li>
 *   <li>Settlement of authorized funds via {@link BankClient#capture}.</li>
 *   <li>Destination account crediting via {@link BankClient#credit}.</li>
 *   <li>Automatic compensating reversal via {@link BankClient#reverse} if destination credit fails.</li>
 *   <li>Final transaction settlement as {@code COMPLETED}.</li>
 * </ol></p>
 *
 * <p><strong>Persistence & Remote Boundary Strategy:</strong>
 * In accordance with financial transaction safety rules, methods performing remote gRPC
 * calls do NOT hold open database transactions across network boundaries. Each state
 * transition is saved and flushed independently.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final long DEFAULT_HOLD_TTL_SECONDS = 30L;

    private final TransactionRepository transactionRepository;
    private final BankClient bankClient;

    @Autowired
    public PaymentService(TransactionRepository transactionRepository, BankClient bankClient) {
        this.transactionRepository = transactionRepository;
        this.bankClient = bankClient;
    }

    /**
     * Executes the end-to-end payment flow with idempotency and automatic compensation.
     *
     * @param request validated payment instruction details
     * @return the final persisted {@link Transaction}
     */
    public Transaction processPayment(PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PaymentRequest must not be null");
        }

        // 1. Idempotency check: Return existing record if already initiated/processed
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingTx.isPresent()) {
            log.info("Idempotent replay detected for key {}. Returning existing transaction {}.",
                    request.getIdempotencyKey(), existingTx.get().getId());
            return existingTx.get();
        }

        // 2. Create and persist initial transaction with status PENDING
        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .fromAccount(request.getFromAccount())
                .toAccount(request.getToAccount())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(TransactionStatus.PENDING)
                .build();

        try {
            transaction = transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate transaction attempt detected for key {}. Returning existing record.",
                    request.getIdempotencyKey());
            return transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> ex);
        }

        Money money = request.toMoney();

        // 3. Hold funds on source account
        UUID holdIdempKey = toDeterministicUuid(request.getIdempotencyKey() + ":hold");
        UUID fromAccountUuid = toDeterministicUuid(request.getFromAccount());

        HoldResponse holdResponse;
        try {
            holdResponse = bankClient.hold(new HoldRequest(
                    holdIdempKey,
                    fromAccountUuid,
                    money,
                    DEFAULT_HOLD_TTL_SECONDS,
                    transaction.getId()
            ));
        } catch (Exception ex) {
            log.error("Hold operation threw exception for transaction {}: {}", transaction.getId(), ex.getMessage());
            String failureReason = mapExceptionToFailureReason(ex);
            transaction.markStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(failureReason);
            return transactionRepository.saveAndFlush(transaction);
        }

        if (holdResponse == null || holdResponse.getStatus() != TransactionStatus.HELD || holdResponse.getHoldId() == null) {
            String failureReason = extractHoldFailureReason(holdResponse);
            log.warn("Hold rejected for transaction {}. Reason: {}", transaction.getId(), failureReason);
            transaction.markStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(failureReason);
            return transactionRepository.saveAndFlush(transaction);
        }

        transaction.markStatus(TransactionStatus.HELD);
        transaction.setHoldId(holdResponse.getHoldId().toString());
        transaction = transactionRepository.saveAndFlush(transaction);

        // 4. Capture funds
        UUID captureIdempKey = toDeterministicUuid(request.getIdempotencyKey() + ":capture");
        UUID holdId = toDeterministicUuid(transaction.getHoldId());

        CaptureResponse captureResponse;
        try {
            captureResponse = bankClient.capture(new CaptureRequest(captureIdempKey, holdId));
        } catch (Exception ex) {
            log.error("Capture operation threw exception for transaction {}: {}", transaction.getId(), ex.getMessage());
            return handleCaptureFailure(transaction, holdId, mapExceptionToFailureReason(ex), null);
        }

        if (captureResponse == null || captureResponse.getStatus() != TransactionStatus.CAPTURED || captureResponse.getTransactionId() == null) {
            String failureReason = extractCaptureFailureReason(captureResponse);
            log.warn("Capture rejected for transaction {}. Reason: {}", transaction.getId(), failureReason);
            return handleCaptureFailure(transaction, holdId, failureReason, captureResponse);
        }

        transaction.markStatus(TransactionStatus.CAPTURED);
        transaction.setCaptureTxnId(captureResponse.getTransactionId().toString());
        transaction = transactionRepository.saveAndFlush(transaction);

        // 5. Credit destination account (retries handled transparently by BankClient / BankRetryExecutor)
        UUID creditIdempKey = toDeterministicUuid(request.getIdempotencyKey() + ":credit");
        UUID toAccountUuid = toDeterministicUuid(request.getToAccount());

        CreditResponse creditResponse = null;
        boolean creditSucceeded = false;
        String creditFailureReason = null;

        try {
            creditResponse = bankClient.credit(new CreditRequest(
                    creditIdempKey,
                    toAccountUuid,
                    money,
                    transaction.getId()
            ));
            if (creditResponse != null && creditResponse.getStatus() == TransactionStatus.COMPLETED) {
                creditSucceeded = true;
            } else {
                creditFailureReason = extractCreditFailureReason(creditResponse);
            }
        } catch (Exception ex) {
            log.error("Credit operation failed after retries for transaction {}: {}", transaction.getId(), ex.getMessage());
            creditFailureReason = mapExceptionToFailureReason(ex);
        }

        if (creditSucceeded) {
            transaction.markStatus(TransactionStatus.COMPLETED);
            if (creditResponse != null && creditResponse.getTransactionId() != null) {
                transaction.setCreditTxnId(creditResponse.getTransactionId().toString());
            }
            return transactionRepository.saveAndFlush(transaction);
        }

        // 6. Automatic compensation: Destination credit failed, reverse captured funds
        log.warn("Destination credit failed for transaction {}. Reason: {}. Initiating automatic reversal...",
                transaction.getId(), creditFailureReason);

        UUID reverseIdempKey = toDeterministicUuid(request.getIdempotencyKey() + ":reverse");
        UUID captureTxnUuid = toDeterministicUuid(transaction.getCaptureTxnId());

        ReverseResponse reverseResponse = null;
        boolean reverseSucceeded = false;

        try {
            reverseResponse = bankClient.reverse(new ReverseRequest(
                    reverseIdempKey,
                    captureTxnUuid,
                    money,
                    "Automatic compensation: destination credit failed due to " + creditFailureReason
            ));
            if (reverseResponse != null && (reverseResponse.getStatus() == TransactionStatus.REVERSED
                    || reverseResponse.getStatus() == TransactionStatus.COMPLETED)) {
                reverseSucceeded = true;
            }
        } catch (Exception ex) {
            log.error("Automatic reversal operation threw exception for transaction {}: {}", transaction.getId(), ex.getMessage());
            reverseSucceeded = false;
        }

        if (reverseSucceeded) {
            log.info("Automatic reversal succeeded for transaction {}. Marking REVERSED.", transaction.getId());
            transaction.markStatus(TransactionStatus.REVERSED);
            if (reverseResponse != null && reverseResponse.getReversalTransactionId() != null) {
                transaction.setReversalTxnId(reverseResponse.getReversalTransactionId().toString());
            }
            // Preserve the original failure reason for the destination credit failure
            transaction.setFailureReason(creditFailureReason != null ? creditFailureReason : "CREDIT_FAILED");
            return transactionRepository.saveAndFlush(transaction);
        } else {
            log.error("CRITICAL: Automatic reversal FAILED for transaction {}. Capture ID: {}. Credit Failure: {}",
                    transaction.getId(), transaction.getCaptureTxnId(), creditFailureReason);
            transaction.markStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("CREDIT_FAILED: " + (creditFailureReason != null ? creditFailureReason : "UNKNOWN") + "; REVERSAL_FAILED");
            return transactionRepository.saveAndFlush(transaction);
        }
    }

    /**
     * Handles capture failure, branching between EXPIRED and FAILED status,
     * and releasing the hold if it remains active.
     */
    private Transaction handleCaptureFailure(Transaction transaction, UUID holdId, String failureReason, CaptureResponse response) {
        boolean isExpired = isExpiredFailure(failureReason, response);

        if (isExpired) {
            transaction.markStatus(TransactionStatus.EXPIRED);
            transaction.setFailureReason("HOLD_EXPIRED");
        } else {
            transaction.markStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(failureReason != null ? failureReason : "CAPTURE_FAILED");
            // Void / release active hold to free customer funds
            tryReleaseHold(transaction.getIdempotencyKey(), holdId);
        }

        return transactionRepository.saveAndFlush(transaction);
    }

    /**
     * Attempts to void/release an active fund hold, swallowing errors to prevent masking the primary failure.
     */
    private void tryReleaseHold(String idempotencyKey, UUID holdId) {
        try {
            UUID releaseIdempKey = toDeterministicUuid(idempotencyKey + ":release");
            bankClient.release(new ReleaseRequest(releaseIdempKey, holdId, "Release after capture failure"));
        } catch (Exception ex) {
            log.warn("Failed to release hold {} after capture failure: {}", holdId, ex.getMessage());
        }
    }

    private boolean isExpiredFailure(String failureReason, CaptureResponse response) {
        if (response != null && response.getFailureReason() == BankFailureReason.HOLD_EXPIRED) {
            return true;
        }
        if (failureReason != null && failureReason.toUpperCase().contains("EXPIRED")) {
            return true;
        }
        if (response != null && response.getErrorMessage() != null && response.getErrorMessage().toUpperCase().contains("EXPIRED")) {
            return true;
        }
        return false;
    }

    private String extractHoldFailureReason(HoldResponse response) {
        if (response == null) {
            return "HOLD_FAILED_NO_RESPONSE";
        }
        if (response.getFailureReason() != null) {
            return response.getFailureReason().name();
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isBlank()) {
            return sanitizeFailureMessage(response.getErrorMessage());
        }
        return "HOLD_FAILED";
    }

    private String extractCaptureFailureReason(CaptureResponse response) {
        if (response == null) {
            return "CAPTURE_FAILED_NO_RESPONSE";
        }
        if (response.getFailureReason() != null) {
            return response.getFailureReason().name();
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isBlank()) {
            return sanitizeFailureMessage(response.getErrorMessage());
        }
        return "CAPTURE_FAILED";
    }

    private String extractCreditFailureReason(CreditResponse response) {
        if (response == null) {
            return "CREDIT_FAILED_NO_RESPONSE";
        }
        if (response.getFailureReason() != null) {
            return response.getFailureReason().name();
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isBlank()) {
            return sanitizeFailureMessage(response.getErrorMessage());
        }
        return "CREDIT_FAILED";
    }

    private String mapExceptionToFailureReason(Exception ex) {
        if (ex == null) {
            return "UNKNOWN_ERROR";
        }
        String msg = ex.getMessage();
        if (msg != null) {
            String upper = msg.toUpperCase();
            if (upper.contains("INSUFFICIENT")) {
                return BankFailureReason.INSUFFICIENT_FUNDS.name();
            }
            if (upper.contains("LOCKED")) {
                return BankFailureReason.ACCOUNT_LOCKED.name();
            }
            if (upper.contains("EXPIRED")) {
                return BankFailureReason.HOLD_EXPIRED.name();
            }
            if (upper.contains("CHAOS")) {
                return BankFailureReason.CHAOS_INJECTED.name();
            }
        }
        if (ex instanceof BankRetryExhaustedException) {
            return "BANK_UNAVAILABLE";
        }
        if (ex instanceof BankRetryTimeoutException) {
            return "BANK_TIMEOUT";
        }
        if (ex instanceof StatusRuntimeException sre) {
            return switch (sre.getStatus().getCode()) {
                case UNAVAILABLE -> "BANK_UNAVAILABLE";
                case DEADLINE_EXCEEDED -> "BANK_TIMEOUT";
                default -> sanitizeFailureMessage(sre.getStatus().getDescription() != null
                        ? sre.getStatus().getDescription()
                        : sre.getStatus().getCode().name());
            };
        }
        return sanitizeFailureMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
    }

    private String sanitizeFailureMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return "UNKNOWN_FAILURE";
        }
        String upper = msg.toUpperCase().trim();
        if (upper.contains("INSUFFICIENT")) {
            return BankFailureReason.INSUFFICIENT_FUNDS.name();
        }
        if (upper.contains("LOCKED")) {
            return BankFailureReason.ACCOUNT_LOCKED.name();
        }
        if (upper.contains("EXPIRED")) {
            return BankFailureReason.HOLD_EXPIRED.name();
        }
        if (upper.contains("NOT FOUND")) {
            return BankFailureReason.HOLD_NOT_FOUND.name();
        }
        if (upper.contains("ALREADY CAPTURED") || upper.contains("ALREADY_CAPTURED")) {
            return BankFailureReason.HOLD_ALREADY_CAPTURED.name();
        }
        if (upper.contains("ALREADY RELEASED") || upper.contains("ALREADY_RELEASED")) {
            return BankFailureReason.HOLD_ALREADY_RELEASED.name();
        }
        if (upper.contains("CHAOS")) {
            return BankFailureReason.CHAOS_INJECTED.name();
        }
        return msg.length() > 255 ? msg.substring(0, 255) : msg;
    }

    /**
     * Converts a string identifier into a UUID, parsing standard UUIDs or deterministically
     * hashing non-UUID strings to maintain repeatable idempotency keys.
     */
    private UUID toDeterministicUuid(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    public Optional<Transaction> getTransactionById(UUID transactionId) {
        return transactionRepository.findById(transactionId);
    }

    public Optional<Transaction> getTransactionByIdempotencyKey(String idempotencyKey) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }
}

