package com.paymentsystem.bank;

import com.paymentsystem.dto.CaptureRequest;
import com.paymentsystem.dto.CaptureResponse;
import com.paymentsystem.dto.CreditRequest;
import com.paymentsystem.dto.CreditResponse;
import com.paymentsystem.dto.GetBalanceRequest;
import com.paymentsystem.dto.GetBalanceResponse;
import com.paymentsystem.dto.HoldRequest;
import com.paymentsystem.dto.HoldResponse;
import com.paymentsystem.dto.ReleaseRequest;
import com.paymentsystem.dto.ReleaseResponse;
import com.paymentsystem.dto.ReverseRequest;
import com.paymentsystem.dto.ReverseResponse;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.TransactionStatus;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import payment.bank.v1.Bank;
import payment.bank.v1.BankServiceGrpc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client wrapper responsible for coordinating operations with the external Mock Bank service.
 *
 * <p><strong>Architectural Role:</strong>
 * External Integration / Client layer (see ADR-001 and ARCHITECTURE.md).
 * Isolates gRPC communication mechanics, channel management, stub invocations, and network
 * error handling away from business services and controllers.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a dedicated gRPC client service module built with {@code @grpc/grpc-js}
 * and {@code @grpc/proto-loader} in Node.js, encapsulating channel connections, unary calls,
 * deadlines, retry policies, and status code translations.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Interacts with {@link BankServiceGrpc} generated stubs, translating internal models
 * (such as {@link Money} and DTOs) to Protobuf messages, delegating execution through a centralized
 * {@link BankRetryExecutor} to transparently retry transient errors, and returning bank responses
 * to the Payment System's service layer.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Local transaction recording and database persistence belong in {@code service/} and {@code repository/}.
 * Controllers must NEVER call this gRPC client directly (strictly prohibited by AGENTS.md rule 1).</p>
 */
@Component
public class BankClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final BankServiceGrpc.BankServiceBlockingStub blockingStub;
    private final BankRetryExecutor retryExecutor;

    /**
     * Spring-managed constructor injecting host and port configuration properties as well as
     * the centralized {@link BankRetryExecutor}.
     *
     * @param host external bank gRPC hostname
     * @param port external bank gRPC port number
     * @param retryExecutor the centralized retry executor (instantiates default if null)
     */
    @Autowired
    public BankClient(
            @Value("${bank.grpc.host:localhost}") String host,
            @Value("${bank.grpc.port:50051}") int port,
            @Autowired(required = false) BankRetryExecutor retryExecutor) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build(),
             retryExecutor != null ? retryExecutor : new BankRetryExecutor());
    }

    /**
     * Constructor using host and port with default retry execution.
     *
     * @param host external bank gRPC hostname
     * @param port external bank gRPC port number
     */
    public BankClient(String host, int port) {
        this(host, port, new BankRetryExecutor());
    }

    /**
     * Constructor allowing injection of a pre-configured {@link ManagedChannel} with default retry execution.
     *
     * @param channel the managed gRPC channel
     */
    public BankClient(ManagedChannel channel) {
        this(channel, BankServiceGrpc.newBlockingStub(channel), new BankRetryExecutor());
    }

    /**
     * Constructor accepting a {@link ManagedChannel} and a custom {@link BankRetryExecutor}.
     *
     * @param channel the managed gRPC channel
     * @param retryExecutor the retry executor
     */
    public BankClient(ManagedChannel channel, BankRetryExecutor retryExecutor) {
        this(channel, BankServiceGrpc.newBlockingStub(channel), retryExecutor);
    }

    /**
     * Constructor accepting both a {@link ManagedChannel} and a {@link BankServiceGrpc.BankServiceBlockingStub}.
     *
     * @param channel the managed gRPC channel (can be null in isolated tests if not closing)
     * @param blockingStub the blocking gRPC stub
     */
    public BankClient(ManagedChannel channel, BankServiceGrpc.BankServiceBlockingStub blockingStub) {
        this(channel, blockingStub, new BankRetryExecutor());
    }

    /**
     * Full constructor accepting {@link ManagedChannel}, {@link BankServiceGrpc.BankServiceBlockingStub},
     * and {@link BankRetryExecutor}. Primarily used for isolated unit testing with mocks.
     *
     * @param channel the managed gRPC channel
     * @param blockingStub the blocking gRPC stub
     * @param retryExecutor the centralized retry executor
     */
    public BankClient(ManagedChannel channel,
                      BankServiceGrpc.BankServiceBlockingStub blockingStub,
                      BankRetryExecutor retryExecutor) {
        this.channel = channel;
        this.blockingStub = blockingStub;
        this.retryExecutor = retryExecutor != null ? retryExecutor : new BankRetryExecutor();
    }

    // =========================================================================
    // 1. Get Balance
    // =========================================================================

    /**
     * Raw protobuf operation to query real-time balance, wrapped with centralized retry handling.
     *
     * @param request protobuf request
     * @return protobuf response
     */
    public Bank.GetBalanceResponse getBalance(Bank.GetBalanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("GetBalanceRequest must not be null");
        }
        return retryExecutor.execute(() -> blockingStub.getBalance(request), "getBalance");
    }

    /**
     * Queries current cleared, held, and available balances for an account.
     *
     * @param request the balance inquiry request DTO
     * @return the balance response DTO containing ledger and spendable amounts
     */
    public GetBalanceResponse getBalance(GetBalanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("GetBalanceRequest must not be null");
        }
        Bank.GetBalanceRequest protoRequest = Bank.GetBalanceRequest.newBuilder()
                .setAccountId(request.getAccountId() != null ? request.getAccountId().toString() : "")
                .build();

        Bank.GetBalanceResponse protoResponse = getBalance(protoRequest);

        return new GetBalanceResponse(
                parseUuid(protoResponse.getAccountId()),
                toDomainMoney(protoResponse.getHeld()),
                toDomainMoney(protoResponse.getAvailable()),
                toDomainMoney(protoResponse.getBalance())
        );
    }

    // =========================================================================
    // 2. Hold (Authorize / Reserve Funds)
    // =========================================================================

    /**
     * Raw protobuf operation to reserve funds, wrapped with centralized retry handling.
     *
     * @param request protobuf request
     * @return protobuf response
     */
    public Bank.HoldResponse hold(Bank.HoldRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HoldRequest must not be null");
        }
        return retryExecutor.execute(() -> blockingStub.hold(request), "hold");
    }

    /**
     * Initiates phase one of two-phase payment (Auth/Capture), reserving funds on an account.
     * The exact same idempotency key and transaction reference are preserved across every retry.
     *
     * @param request the hold request DTO
     * @return the hold response DTO
     */
    public HoldResponse hold(HoldRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("HoldRequest must not be null");
        }
        Bank.HoldRequest.Builder builder = Bank.HoldRequest.newBuilder();
        if (request.getIdempotencyKey() != null) {
            builder.setIdempotencyKey(request.getIdempotencyKey().toString());
        }
        if (request.getAccountId() != null) {
            builder.setAccountId(request.getAccountId().toString());
        }
        if (request.getAmount() != null) {
            builder.setAmount(toProtoMoney(request.getAmount()));
        }
        builder.setTtlSeconds(request.getTtlSeconds());
        if (request.getTransactionReference() != null) {
            builder.setTransactionReference(request.getTransactionReference().toString());
        }

        Bank.HoldResponse protoResponse = hold(builder.build());

        String errorMessage = extractErrorMessage(protoResponse.hasErrorMessage(), protoResponse.getErrorMessage());
        BankFailureReason failureReason = parseFailureReason(errorMessage);
        HoldStatus holdStatus = (protoResponse.getStatus() == Bank.TransactionStatus.HELD) ? HoldStatus.ACTIVE : null;

        return new HoldResponse(
                parseUuid(protoResponse.getHoldId()),
                parseUuid(protoResponse.getTransactionId()),
                mapTransactionStatus(protoResponse.getStatus()),
                failureReason,
                holdStatus,
                parseUuid(protoResponse.getAccountId()),
                toDomainMoney(protoResponse.getBalance()),
                toDomainMoney(protoResponse.getHeld()),
                errorMessage
        );
    }

    // =========================================================================
    // 3. Capture (Settle Held Funds)
    // =========================================================================

    /**
     * Raw protobuf operation to capture held funds, wrapped with centralized retry handling.
     *
     * @param request protobuf request
     * @return protobuf response
     */
    public Bank.CaptureResponse capture(Bank.CaptureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CaptureRequest must not be null");
        }
        return retryExecutor.execute(() -> blockingStub.capture(request), "capture");
    }

    /**
     * Settles previously authorized/held funds.
     * The exact same idempotency key and hold identifier are preserved across retries.
     *
     * @param request the capture request DTO
     * @return the capture response DTO
     */
    public CaptureResponse capture(CaptureRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CaptureRequest must not be null");
        }
        Bank.CaptureRequest.Builder builder = Bank.CaptureRequest.newBuilder();
        if (request.getIdempotencyKey() != null) {
            builder.setIdempotencyKey(request.getIdempotencyKey().toString());
        }
        if (request.getHoldId() != null) {
            builder.setHoldId(request.getHoldId().toString());
        }

        Bank.CaptureResponse protoResponse = capture(builder.build());

        String errorMessage = extractErrorMessage(protoResponse.hasErrorMessage(), protoResponse.getErrorMessage());
        BankFailureReason failureReason = parseFailureReason(errorMessage);
        HoldStatus holdStatus = (protoResponse.getStatus() == Bank.TransactionStatus.CAPTURED) ? HoldStatus.CAPTURED : null;

        return new CaptureResponse(
                parseUuid(protoResponse.getHoldId()),
                parseUuid(protoResponse.getTransactionId()),
                mapTransactionStatus(protoResponse.getStatus()),
                failureReason,
                holdStatus,
                parseUuid(protoResponse.getAccountId()),
                toDomainMoney(protoResponse.getBalance()),
                toDomainMoney(protoResponse.getCapturedAmount()),
                errorMessage
        );
    }

    // =========================================================================
    // 4. Release (Void / Release Hold)
    // =========================================================================

    /**
     * Raw protobuf operation to release held funds, wrapped with centralized retry handling.
     *
     * @param request protobuf request
     * @return protobuf response
     */
    public Bank.ReleaseResponse release(Bank.ReleaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ReleaseRequest must not be null");
        }
        return retryExecutor.execute(() -> blockingStub.release(request), "release");
    }

    /**
     * Voids an active fund hold, returning reserved funds to available balance.
     * The exact same idempotency key and hold identifier are preserved across retries.
     *
     * @param request the release request DTO
     * @return the release response DTO
     */
    public ReleaseResponse release(ReleaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ReleaseRequest must not be null");
        }
        Bank.ReleaseRequest.Builder builder = Bank.ReleaseRequest.newBuilder();
        if (request.getIdempotencyKey() != null) {
            builder.setIdempotencyKey(request.getIdempotencyKey().toString());
        }
        if (request.getHoldId() != null) {
            builder.setHoldId(request.getHoldId().toString());
        }
        if (request.getReason() != null) {
            builder.setReason(request.getReason());
        }

        Bank.ReleaseResponse protoResponse = release(builder.build());

        String errorMessage = extractErrorMessage(protoResponse.hasErrorMessage(), protoResponse.getErrorMessage());
        BankFailureReason failureReason = parseFailureReason(errorMessage);
        HoldStatus holdStatus = (protoResponse.getStatus() == Bank.TransactionStatus.COMPLETED
                || protoResponse.getStatus() == Bank.TransactionStatus.HELD)
                ? HoldStatus.RELEASED
                : (protoResponse.getStatus() == Bank.TransactionStatus.FAILED ? null : HoldStatus.RELEASED);

        return new ReleaseResponse(
                parseUuid(protoResponse.getHoldId()),
                parseUuid(protoResponse.getTransactionId()),
                mapTransactionStatus(protoResponse.getStatus()),
                failureReason,
                holdStatus,
                parseUuid(protoResponse.getAccountId()),
                toDomainMoney(protoResponse.getHeld()),
                toDomainMoney(protoResponse.getBalance()),
                errorMessage
        );
    }

    // =========================================================================
    // 5. Credit (Direct Transfer In)
    // =========================================================================

    /**
     * Raw protobuf operation to credit funds, wrapped with centralized retry handling.
     *
     * @param request protobuf request
     * @return protobuf response
     */
    public Bank.CreditResponse credit(Bank.CreditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreditRequest must not be null");
        }
        return retryExecutor.execute(() -> blockingStub.credit(request), "credit");
    }

    /**
     * Credits money directly into a recipient's bank account.
     * The exact same idempotency key and transaction reference are preserved across retries.
     *
     * @param request the credit request DTO
     * @return the credit response DTO
     */
    public CreditResponse credit(CreditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreditRequest must not be null");
        }
        Bank.CreditRequest.Builder builder = Bank.CreditRequest.newBuilder();
        if (request.getIdempotencyKey() != null) {
            builder.setIdempotencyKey(request.getIdempotencyKey().toString());
        }
        if (request.getDestinationAccountId() != null) {
            builder.setDestinationAccountId(request.getDestinationAccountId().toString());
        }
        if (request.getAmount() != null) {
            builder.setAmount(toProtoMoney(request.getAmount()));
        }
        if (request.getTransactionReference() != null) {
            builder.setTransactionReference(request.getTransactionReference().toString());
        }

        Bank.CreditResponse protoResponse = credit(builder.build());

        String errorMessage = extractErrorMessage(protoResponse.hasErrorMessage(), protoResponse.getErrorMessage());
        BankFailureReason failureReason = parseFailureReason(errorMessage);

        return new CreditResponse(
                parseUuid(protoResponse.getTransactionId()),
                mapTransactionStatus(protoResponse.getStatus()),
                failureReason,
                parseUuid(protoResponse.getAccountId()),
                protoResponse.getBankReferenceNumber(),
                errorMessage
        );
    }

    // =========================================================================
    // 6. Reverse (Refund Settled Transaction)
    // =========================================================================

    /**
     * Raw protobuf operation to reverse a transaction, wrapped with centralized retry handling.
     *
     * @param request protobuf request
     * @return protobuf response
     */
    public Bank.ReverseResponse reverse(Bank.ReverseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ReverseRequest must not be null");
        }
        return retryExecutor.execute(() -> blockingStub.reverse(request), "reverse");
    }

    /**
the     * Initiates a refund/reversal of settled funds against a prior bank transaction.
     * The exact same idempotency key and original transaction identifier are preserved across retries.
     *
     * @param request the reverse request DTO
     * @return the reverse response DTO
     */
    public ReverseResponse reverse(ReverseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ReverseRequest must not be null");
        }
        Bank.ReverseRequest.Builder builder = Bank.ReverseRequest.newBuilder();
        if (request.getIdempotencyKey() != null) {
            builder.setIdempotencyKey(request.getIdempotencyKey().toString());
        }
        if (request.getOriginalTransactionId() != null) {
            builder.setTransactionId(request.getOriginalTransactionId().toString());
        }
        if (request.getAmountToReverse() != null) {
            builder.setAmountToReverse(toProtoMoney(request.getAmountToReverse()));
        }
        if (request.getReason() != null) {
            builder.setReason(request.getReason());
        }

        Bank.ReverseResponse protoResponse = reverse(builder.build());

        String errorMessage = extractErrorMessage(protoResponse.hasErrorMessage(), protoResponse.getErrorMessage());
        BankFailureReason failureReason = parseFailureReason(errorMessage);

        return new ReverseResponse(
                parseUuid(protoResponse.getOriginalTransactionId()),
                parseUuid(protoResponse.getReversalTransactionId()),
                mapTransactionStatus(protoResponse.getStatus()),
                failureReason,
                parseUuid(protoResponse.getAccountId()),
                protoResponse.getBankReferenceNumber(),
                errorMessage
        );
    }

    // =========================================================================
    // Channel Lifecycle & Management
    // =========================================================================

    /**
     * Returns the underlying {@link ManagedChannel}, or null if using a standalone stub.
     *
     * @return the managed channel
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    /**
     * Returns the configured {@link BankServiceGrpc.BankServiceBlockingStub}.
     *
     * @return the blocking stub
     */
    public BankServiceGrpc.BankServiceBlockingStub getBlockingStub() {
        return blockingStub;
    }

    /**
     * Returns the configured {@link BankRetryExecutor}.
     *
     * @return the retry executor
     */
    public BankRetryExecutor getRetryExecutor() {
        return retryExecutor;
    }

    /**
     * Gracefully shuts down the gRPC channel, allowing in-flight RPCs to complete.
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @PreDestroy
    @Override
    public void close() {
        shutdown();
    }

    // =========================================================================
    // Helper & Mapping Methods
    // =========================================================================

    private static String extractErrorMessage(boolean hasErrorMessage, String errorMessage) {
        if (!hasErrorMessage || errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        return errorMessage;
    }

    private static Bank.Money toProtoMoney(Money domainMoney) {
        if (domainMoney == null) {
            return Bank.Money.getDefaultInstance();
        }
        return Bank.Money.newBuilder()
                .setCurrency(domainMoney.getCurrency() != null ? domainMoney.getCurrency() : "")
                .setAmountInUnits(domainMoney.getAmountInMinorUnits())
                .build();
    }

    private static Money toDomainMoney(Bank.Money protoMoney) {
        if (protoMoney == null || protoMoney.getCurrency() == null || protoMoney.getCurrency().isEmpty()) {
            return null;
        }
        return new Money(protoMoney.getAmountInUnits(), protoMoney.getCurrency());
    }

    private static TransactionStatus mapTransactionStatus(Bank.TransactionStatus protoStatus) {
        if (protoStatus == null) {
            return null;
        }
        return switch (protoStatus) {
            case PENDING -> TransactionStatus.PENDING;
            case HELD -> TransactionStatus.HELD;
            case CAPTURED -> TransactionStatus.CAPTURED;
            case COMPLETED -> TransactionStatus.COMPLETED;
            case REVERSED -> TransactionStatus.REVERSED;
            case FAILED, UNRECOGNIZED -> TransactionStatus.FAILED;
        };
    }

    private static BankFailureReason parseFailureReason(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String upper = errorMessage.toUpperCase();
        if (upper.contains("INSUFFICIENT")) {
            return BankFailureReason.INSUFFICIENT_FUNDS;
        }
        if (upper.contains("LOCKED")) {
            return BankFailureReason.ACCOUNT_LOCKED;
        }
        if (upper.contains("NOT FOUND")) {
            return BankFailureReason.HOLD_NOT_FOUND;
        }
        if (upper.contains("ALREADY CAPTURED") || upper.contains("ALREADY_CAPTURED")) {
            return BankFailureReason.HOLD_ALREADY_CAPTURED;
        }
        if (upper.contains("ALREADY RELEASED") || upper.contains("ALREADY_RELEASED")) {
            return BankFailureReason.HOLD_ALREADY_RELEASED;
        }
        if (upper.contains("EXPIRED")) {
            return BankFailureReason.HOLD_EXPIRED;
        }
        if (upper.contains("CHAOS")) {
            return BankFailureReason.CHAOS_INJECTED;
        }
        try {
            return BankFailureReason.valueOf(upper.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
