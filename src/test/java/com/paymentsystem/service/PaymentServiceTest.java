package com.paymentsystem.service;

import com.paymentsystem.bank.BankClient;
import com.paymentsystem.bank.BankRetryExhaustedException;
import com.paymentsystem.dto.CaptureRequest;
import com.paymentsystem.dto.CaptureResponse;
import com.paymentsystem.dto.CreditRequest;
import com.paymentsystem.dto.CreditResponse;
import com.paymentsystem.dto.HoldRequest;
import com.paymentsystem.dto.HoldResponse;
import com.paymentsystem.dto.PaymentRequest;
import com.paymentsystem.dto.ReleaseRequest;
import com.paymentsystem.dto.ReleaseResponse;
import com.paymentsystem.dto.ReverseRequest;
import com.paymentsystem.dto.ReverseResponse;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.Transaction;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.repository.TransactionRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit test suite for {@link PaymentService}.
 *
 * <p>Validates the complete two-leg payment orchestration sequence, failure branches,
 * automatic compensating reversals, idempotent re-executions, and concurrency safety.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BankClient bankClient;

    private PaymentService paymentService;

    private final String idempotencyKey = UUID.randomUUID().toString();
    private final String fromAccount = UUID.randomUUID().toString();
    private final String toAccount = UUID.randomUUID().toString();
    private final BigDecimal amount = new BigDecimal("100.00");
    private final String currency = "USD";
    private final Money money = new Money(10000, "USD");

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(transactionRepository, bankClient);
    }

    private PaymentRequest createStandardRequest() {
        return new PaymentRequest(idempotencyKey, fromAccount, toAccount, amount, currency);
    }

    private void mockSaveAndFlushPassthrough() {
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction t = inv.getArgument(0);
                    if (t.getId() == null) {
                        t.setId(UUID.randomUUID());
                    }
                    return t;
                });
    }

    // =========================================================================
    // 1. Successful payment
    // =========================================================================
    @Test
    @DisplayName("1. Successful payment: Full flow Hold -> Capture -> Credit -> COMPLETED")
    void testProcessPaymentSuccess() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        UUID bankHoldTxnId = UUID.randomUUID();
        HoldResponse holdRes = new HoldResponse(
                holdId, bankHoldTxnId, TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        );
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(holdRes);

        UUID captureTxnId = UUID.randomUUID();
        CaptureResponse captureRes = new CaptureResponse(
                holdId, captureTxnId, TransactionStatus.CAPTURED, null,
                HoldStatus.CAPTURED, UUID.fromString(fromAccount), money, money, null
        );
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(captureRes);

        UUID creditTxnId = UUID.randomUUID();
        CreditResponse creditRes = new CreditResponse(
                creditTxnId, TransactionStatus.COMPLETED, null,
                UUID.fromString(toAccount), "REF-12345", null
        );
        when(bankClient.credit(any(CreditRequest.class))).thenReturn(creditRes);

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        assertEquals(holdId.toString(), result.getHoldId());
        assertEquals(captureTxnId.toString(), result.getCaptureTxnId());
        assertEquals(creditTxnId.toString(), result.getCreditTxnId());
        assertNotNull(result.getCompletedAt());
        assertNull(result.getFailureReason());

        // Verify operations were executed in strict sequence
        ArgumentCaptor<HoldRequest> holdCaptor = ArgumentCaptor.forClass(HoldRequest.class);
        ArgumentCaptor<CaptureRequest> captureCaptor = ArgumentCaptor.forClass(CaptureRequest.class);
        ArgumentCaptor<CreditRequest> creditCaptor = ArgumentCaptor.forClass(CreditRequest.class);

        verify(bankClient).hold(holdCaptor.capture());
        verify(bankClient).capture(captureCaptor.capture());
        verify(bankClient).credit(creditCaptor.capture());
        verify(bankClient, never()).reverse(any(ReverseRequest.class));
        verify(bankClient, never()).release(any(ReleaseRequest.class));

        assertEquals(UUID.fromString(fromAccount), holdCaptor.getValue().getAccountId());
        assertEquals(holdId, captureCaptor.getValue().getHoldId());
        assertEquals(UUID.fromString(toAccount), creditCaptor.getValue().getDestinationAccountId());
    }

    // =========================================================================
    // 2. Hold failure
    // =========================================================================
    @Test
    @DisplayName("2a. Hold failure: Insufficient funds marks transaction FAILED and halts flow")
    void testHoldFailureInsufficientFunds() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        HoldResponse holdRes = new HoldResponse(
                null, null, TransactionStatus.FAILED, BankFailureReason.INSUFFICIENT_FUNDS,
                null, UUID.fromString(fromAccount), money, money, "INSUFFICIENT_FUNDS"
        );
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(holdRes);

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertEquals("INSUFFICIENT_FUNDS", result.getFailureReason());
        assertNull(result.getHoldId());

        verify(bankClient).hold(any(HoldRequest.class));
        verify(bankClient, never()).capture(any(CaptureRequest.class));
        verify(bankClient, never()).credit(any(CreditRequest.class));
        verify(bankClient, never()).reverse(any(ReverseRequest.class));
    }

    @Test
    @DisplayName("2b. Hold failure: Locked account marks transaction FAILED and halts flow")
    void testHoldFailureAccountLocked() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        HoldResponse holdRes = new HoldResponse(
                null, null, TransactionStatus.FAILED, BankFailureReason.ACCOUNT_LOCKED,
                null, UUID.fromString(fromAccount), money, money, "ACCOUNT_LOCKED"
        );
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(holdRes);

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertEquals("ACCOUNT_LOCKED", result.getFailureReason());

        verify(bankClient).hold(any(HoldRequest.class));
        verify(bankClient, never()).capture(any(CaptureRequest.class));
        verify(bankClient, never()).credit(any(CreditRequest.class));
    }

    @Test
    @DisplayName("2c. Hold failure: Exception thrown during hold marks transaction FAILED")
    void testHoldFailureException() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        when(bankClient.hold(any(HoldRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("CHAOS injected failure")));

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertEquals("CHAOS_INJECTED", result.getFailureReason());

        verify(bankClient).hold(any(HoldRequest.class));
        verify(bankClient, never()).capture(any(CaptureRequest.class));
    }

    // =========================================================================
    // 3. Capture failure
    // =========================================================================
    @Test
    @DisplayName("3a. Capture failure: Expired hold transitions status to EXPIRED (no release)")
    void testCaptureFailureHoldExpired() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        HoldResponse holdRes = new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        );
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(holdRes);

        CaptureResponse captureRes = new CaptureResponse(
                holdId, null, TransactionStatus.FAILED, BankFailureReason.HOLD_EXPIRED,
                null, UUID.fromString(fromAccount), money, money, "HOLD_EXPIRED"
        );
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(captureRes);

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.EXPIRED, result.getStatus());
        assertEquals("HOLD_EXPIRED", result.getFailureReason());
        assertNotNull(result.getCompletedAt());

        verify(bankClient).capture(any(CaptureRequest.class));
        verify(bankClient, never()).release(any(ReleaseRequest.class));
        verify(bankClient, never()).credit(any(CreditRequest.class));
        verify(bankClient, never()).reverse(any(ReverseRequest.class));
    }

    @Test
    @DisplayName("3b. Capture failure: General error transitions to FAILED and voids/releases hold")
    void testCaptureFailureGeneralErrorReleasesHold() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        HoldResponse holdRes = new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        );
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(holdRes);

        CaptureResponse captureRes = new CaptureResponse(
                holdId, null, TransactionStatus.FAILED, null,
                null, UUID.fromString(fromAccount), money, money, "SYSTEM_ERROR"
        );
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(captureRes);

        ReleaseResponse releaseRes = new ReleaseResponse(
                holdId, UUID.randomUUID(), TransactionStatus.COMPLETED, null,
                HoldStatus.RELEASED, UUID.fromString(fromAccount), money, money, null
        );
        when(bankClient.release(any(ReleaseRequest.class))).thenReturn(releaseRes);

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertEquals("SYSTEM_ERROR", result.getFailureReason());

        // Verify hold was released to prevent locked funds
        ArgumentCaptor<ReleaseRequest> releaseCaptor = ArgumentCaptor.forClass(ReleaseRequest.class);
        verify(bankClient).release(releaseCaptor.capture());
        assertEquals(holdId, releaseCaptor.getValue().getHoldId());

        verify(bankClient, never()).credit(any(CreditRequest.class));
        verify(bankClient, never()).reverse(any(ReverseRequest.class));
    }

    // =========================================================================
    // 4. Transient credit failure with retry
    // =========================================================================
    @Test
    @DisplayName("4. Transient credit failure: Succeeded on retry completes payment without reversal")
    void testTransientCreditFailureWithRetrySucceeds() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        ));

        UUID captureTxnId = UUID.randomUUID();
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(new CaptureResponse(
                holdId, captureTxnId, TransactionStatus.CAPTURED, null,
                HoldStatus.CAPTURED, UUID.fromString(fromAccount), money, money, null
        ));

        // BankClient internally retries transient errors and ultimately returns COMPLETED
        UUID creditTxnId = UUID.randomUUID();
        when(bankClient.credit(any(CreditRequest.class))).thenReturn(new CreditResponse(
                creditTxnId, TransactionStatus.COMPLETED, null,
                UUID.fromString(toAccount), "REF-RETRY-SUCCESS", null
        ));

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        assertEquals(creditTxnId.toString(), result.getCreditTxnId());
        assertNull(result.getFailureReason());

        verify(bankClient, never()).reverse(any(ReverseRequest.class));
    }

    // =========================================================================
    // 5. Credit failure with successful reversal
    // =========================================================================
    @Test
    @DisplayName("5. Credit failure with successful reversal: Reverses capture and preserves credit failure reason")
    void testCreditFailureWithSuccessfulReversal() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        ));

        UUID captureTxnId = UUID.randomUUID();
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(new CaptureResponse(
                holdId, captureTxnId, TransactionStatus.CAPTURED, null,
                HoldStatus.CAPTURED, UUID.fromString(fromAccount), money, money, null
        ));

        // Credit permanently fails with non-retryable ACCOUNT_LOCKED
        when(bankClient.credit(any(CreditRequest.class))).thenReturn(new CreditResponse(
                null, TransactionStatus.FAILED, BankFailureReason.ACCOUNT_LOCKED,
                UUID.fromString(toAccount), null, "ACCOUNT_LOCKED"
        ));

        UUID reversalTxnId = UUID.randomUUID();
        when(bankClient.reverse(any(ReverseRequest.class))).thenReturn(new ReverseResponse(
                captureTxnId, reversalTxnId, TransactionStatus.REVERSED, null,
                UUID.fromString(fromAccount), "REF-REV-01", null
        ));

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.REVERSED, result.getStatus());
        assertEquals("ACCOUNT_LOCKED", result.getFailureReason());
        assertEquals(reversalTxnId.toString(), result.getReversalTxnId());
        assertEquals(captureTxnId.toString(), result.getCaptureTxnId());
        assertNull(result.getCreditTxnId());

        // Verify reversal was invoked with the exact captureTxnId
        ArgumentCaptor<ReverseRequest> reverseCaptor = ArgumentCaptor.forClass(ReverseRequest.class);
        verify(bankClient).reverse(reverseCaptor.capture());
        assertEquals(captureTxnId, reverseCaptor.getValue().getOriginalTransactionId());
        assertEquals(money.getAmountInMinorUnits(), reverseCaptor.getValue().getAmountToReverse().getAmountInMinorUnits());
    }

    @Test
    @DisplayName("5b. Credit failure via retry exhaustion with successful reversal")
    void testCreditRetryExhaustedWithSuccessfulReversal() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        ));

        UUID captureTxnId = UUID.randomUUID();
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(new CaptureResponse(
                holdId, captureTxnId, TransactionStatus.CAPTURED, null,
                HoldStatus.CAPTURED, UUID.fromString(fromAccount), money, money, null
        ));

        when(bankClient.credit(any(CreditRequest.class)))
                .thenThrow(new BankRetryExhaustedException("credit", 3, Duration.ofMillis(500), new StatusRuntimeException(Status.UNAVAILABLE)));

        UUID reversalTxnId = UUID.randomUUID();
        when(bankClient.reverse(any(ReverseRequest.class))).thenReturn(new ReverseResponse(
                captureTxnId, reversalTxnId, TransactionStatus.REVERSED, null,
                UUID.fromString(fromAccount), "REF-REV-02", null
        ));

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.REVERSED, result.getStatus());
        assertEquals("BANK_UNAVAILABLE", result.getFailureReason());
        assertEquals(reversalTxnId.toString(), result.getReversalTxnId());
    }

    // =========================================================================
    // 6. Credit failure with failed reversal
    // =========================================================================
    @Test
    @DisplayName("6. Credit failure with failed reversal: Transitions to FAILED and indicates reversal failure")
    void testCreditFailureWithFailedReversal() {
        PaymentRequest request = createStandardRequest();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        mockSaveAndFlushPassthrough();

        UUID holdId = UUID.randomUUID();
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        ));

        UUID captureTxnId = UUID.randomUUID();
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(new CaptureResponse(
                holdId, captureTxnId, TransactionStatus.CAPTURED, null,
                HoldStatus.CAPTURED, UUID.fromString(fromAccount), money, money, null
        ));

        when(bankClient.credit(any(CreditRequest.class))).thenReturn(new CreditResponse(
                null, TransactionStatus.FAILED, BankFailureReason.ACCOUNT_LOCKED,
                UUID.fromString(toAccount), null, "ACCOUNT_LOCKED"
        ));

        // Reversal also fails
        when(bankClient.reverse(any(ReverseRequest.class))).thenReturn(new ReverseResponse(
                captureTxnId, null, TransactionStatus.FAILED, null,
                UUID.fromString(fromAccount), null, "REVERSAL_GATEWAY_DOWN"
        ));

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertNotEquals(TransactionStatus.REVERSED, result.getStatus());
        assertThat(result.getFailureReason()).contains("REVERSAL_FAILED");
        assertThat(result.getFailureReason()).contains("ACCOUNT_LOCKED");
        // Verify captureTxnId is preserved for compensation auditing
        assertEquals(captureTxnId.toString(), result.getCaptureTxnId());
    }

    // =========================================================================
    // 7. Idempotent replay
    // =========================================================================
    @Test
    @DisplayName("7. Idempotent replay: Existing transaction returned immediately without bank calls")
    void testIdempotentReplayReturnsExistingTransaction() {
        PaymentRequest request = createStandardRequest();

        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .currency(currency)
                .status(TransactionStatus.COMPLETED)
                .holdId(UUID.randomUUID().toString())
                .captureTxnId(UUID.randomUUID().toString())
                .creditTxnId(UUID.randomUUID().toString())
                .build();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        Transaction result = paymentService.processPayment(request);

        assertSame(existing, result);
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        verifyNoInteractions(bankClient);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    // =========================================================================
    // 8. Concurrent duplicate request
    // =========================================================================
    @Test
    @DisplayName("8. Concurrent duplicate request: DataIntegrityViolationException returns existing winner")
    void testConcurrentDuplicateRequestHandling() {
        PaymentRequest request = createStandardRequest();

        Transaction existingWinner = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .currency(currency)
                .status(TransactionStatus.COMPLETED)
                .build();

        // Initial check returns empty (both threads see nothing initially)
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty()) // Initial check in thread 2
                .thenReturn(Optional.of(existingWinner)); // Post-constraint violation lookup

        // Second insert attempt throws DataIntegrityViolationException
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key violates unique constraint"));

        Transaction result = paymentService.processPayment(request);

        assertNotNull(result);
        assertSame(existingWinner, result);
        verifyNoInteractions(bankClient);
    }

    @Test
    @DisplayName("8b. Concurrent threads: Only one thread executes bank operations, second returns existing")
    void testConcurrentThreadsIdempotency() throws Exception {
        PaymentRequest request = createStandardRequest();

        Transaction existingWinner = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(amount)
                .currency(currency)
                .status(TransactionStatus.COMPLETED)
                .build();

        AtomicBoolean firstCaller = new AtomicBoolean(true);

        when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenAnswer(inv -> {
            if (firstCaller.get()) {
                return Optional.empty();
            } else {
                return Optional.of(existingWinner);
            }
        });

        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) {
                // Initial insert attempt
                if (firstCaller.compareAndSet(true, false)) {
                    t.setId(UUID.randomUUID());
                    return t;
                } else {
                    throw new DataIntegrityViolationException("duplicate key violates unique constraint");
                }
            }
            // Subsequent updates by winning thread
            return t;
        });

        // Set up bank mocks for the winning thread
        UUID holdId = UUID.randomUUID();
        when(bankClient.hold(any(HoldRequest.class))).thenReturn(new HoldResponse(
                holdId, UUID.randomUUID(), TransactionStatus.HELD, null,
                HoldStatus.ACTIVE, UUID.fromString(fromAccount), money, money, null
        ));
        when(bankClient.capture(any(CaptureRequest.class))).thenReturn(new CaptureResponse(
                holdId, UUID.randomUUID(), TransactionStatus.CAPTURED, null,
                HoldStatus.CAPTURED, UUID.fromString(fromAccount), money, money, null
        ));
        when(bankClient.credit(any(CreditRequest.class))).thenReturn(new CreditResponse(
                UUID.randomUUID(), TransactionStatus.COMPLETED, null,
                UUID.fromString(toAccount), "REF-THREAD-WIN", null
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<Transaction> future1 = executor.submit(() -> {
            startLatch.await();
            return paymentService.processPayment(request);
        });

        Future<Transaction> future2 = executor.submit(() -> {
            startLatch.await();
            return paymentService.processPayment(request);
        });

        startLatch.countDown();

        Transaction result1 = future1.get();
        Transaction result2 = future2.get();

        executor.shutdown();

        assertNotNull(result1);
        assertNotNull(result2);

        // One must be the COMPLETED bank run, the other must have received the winning record
        assertTrue(result1.getStatus() == TransactionStatus.COMPLETED || result2.getStatus() == TransactionStatus.COMPLETED);
        // Bank calls happened exactly once for hold, capture, credit
        verify(bankClient, times(1)).hold(any(HoldRequest.class));
        verify(bankClient, times(1)).capture(any(CaptureRequest.class));
        verify(bankClient, times(1)).credit(any(CreditRequest.class));
    }

    // =========================================================================
    // 9. Validation Edge Cases
    // =========================================================================
    @Test
    @DisplayName("9. Null payment request throws IllegalArgumentException")
    void testNullPaymentRequestThrows() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.processPayment(null));
    }
}
