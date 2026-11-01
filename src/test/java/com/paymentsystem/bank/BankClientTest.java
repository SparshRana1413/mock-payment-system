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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import payment.bank.v1.Bank;
import payment.bank.v1.BankServiceGrpc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Isolated unit tests for {@link BankClient} and its complete retry lifecycle.
 *
 * <p>Verifies:
 * <ol>
 *   <li>Successful operation on the first attempt.</li>
 *   <li>Successful operation after one or more retries.</li>
 *   <li>Exhaustion of all configured attempts with structured diagnostics.</li>
 *   <li>Immediate termination on permanent errors.</li>
 *   <li>Correct exponential backoff calculation.</li>
 *   <li>Maximum backoff enforcement and overflow safety.</li>
 *   <li>Retry deadline expiration.</li>
 *   <li>No additional attempt after the deadline expires.</li>
 *   <li>Preservation of the same idempotency key across all attempts.</li>
 *   <li>Preservation of transaction identifiers and request parameters.</li>
 *   <li>Correct handling of transaction outcomes after intermediate failures.</li>
 *   <li>Correct propagation of the final exception without data leakage.</li>
 *   <li>Correct thread interruption handling.</li>
 *   <li>Configuration validation.</li>
 *   <li>Consistent retry behaviour across all six operations.</li>
 * </ol>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BankClientTest {

    @Mock
    private ManagedChannel channel;

    @Mock
    private BankServiceGrpc.BankServiceBlockingStub blockingStub;

    @Mock
    private Sleeper sleeper;

    private AtomicLong simulatedNanoTime;
    private TimeSource timeSource;
    private BankRetryConfig retryConfig;
    private BankRetryExecutor retryExecutor;
    private BankClient bankClient;

    @BeforeEach
    void setUp() throws InterruptedException {
        simulatedNanoTime = new AtomicLong(1_000_000_000L); // Start at 1 second
        timeSource = () -> simulatedNanoTime.get();
        retryConfig = new BankRetryConfig(3, 100L, 2.0, 2000L, 5000L);
        retryExecutor = new BankRetryExecutor(retryConfig, sleeper, timeSource);
        bankClient = new BankClient(channel, blockingStub, retryExecutor);

        // Advance simulated time whenever sleeper.sleep(delay) is invoked
        lenient().doAnswer(inv -> {
            Duration d = inv.getArgument(0);
            if (d != null) {
                simulatedNanoTime.addAndGet(d.toNanos());
            }
            return null;
        }).when(sleeper).sleep(any());
    }

    // =========================================================================
    // 1. Client Construction & Configuration
    // =========================================================================

    @Nested
    @DisplayName("1. Client Construction & Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should construct BankClient using host and port settings")
        void testHostAndPortConstructor() {
            try (BankClient client = new BankClient("localhost", 50051)) {
                assertNotNull(client.getChannel(), "Channel should be created");
                assertNotNull(client.getBlockingStub(), "Blocking stub should be created");
                assertNotNull(client.getRetryExecutor(), "Default retry executor should be created");
                assertFalse(client.getChannel().isShutdown(), "Channel should be open initially");
            }
        }

        @Test
        @DisplayName("Should construct BankClient using an existing ManagedChannel")
        void testChannelConstructor() {
            BankClient client = new BankClient(channel);
            assertSame(channel, client.getChannel());
            assertNotNull(client.getBlockingStub());
            assertNotNull(client.getRetryExecutor());
        }

        @Test
        @DisplayName("Should construct BankClient with both Channel and Stub")
        void testChannelAndStubConstructor() {
            BankClient client = new BankClient(channel, blockingStub);
            assertSame(channel, client.getChannel());
            assertSame(blockingStub, client.getBlockingStub());
            assertNotNull(client.getRetryExecutor());
        }

        @Test
        @DisplayName("Should construct BankClient with Channel, Stub, and RetryExecutor")
        void testFullConstructor() {
            assertSame(channel, bankClient.getChannel());
            assertSame(blockingStub, bankClient.getBlockingStub());
            assertSame(retryExecutor, bankClient.getRetryExecutor());
        }
    }

    // =========================================================================
    // 2. Retry Configuration & Exponential Backoff
    // =========================================================================

    @Nested
    @DisplayName("2. Retry Configuration & Exponential Backoff")
    class RetryConfigTests {

        @Test
        @DisplayName("Requirement 14: Should initialize with default retry configuration values")
        void testDefaultConfiguration() {
            BankRetryConfig defaultConfig = new BankRetryConfig();
            assertEquals(3, defaultConfig.getMaxAttempts());
            assertEquals(Duration.ofMillis(100), defaultConfig.getInitialBackoff());
            assertEquals(2.0, defaultConfig.getBackoffMultiplier());
            assertEquals(Duration.ofMillis(2000), defaultConfig.getMaxBackoff());
            assertEquals(Duration.ofMillis(5000), defaultConfig.getRetryTimeout());
        }

        @Test
        @DisplayName("Requirement 14: Should reject invalid retry configuration values")
        void testInvalidConfiguration() {
            assertThrows(IllegalArgumentException.class, () -> new BankRetryConfig(0, 100L, 2.0, 2000L, 5000L));
            assertThrows(IllegalArgumentException.class, () -> new BankRetryConfig(3, -1L, 2.0, 2000L, 5000L));
            assertThrows(IllegalArgumentException.class, () -> new BankRetryConfig(3, 100L, 0.5, 2000L, 5000L));
            assertThrows(IllegalArgumentException.class, () -> new BankRetryConfig(3, 500L, 2.0, 200L, 5000L));
            assertThrows(IllegalArgumentException.class, () -> new BankRetryConfig(3, 100L, 2.0, 2000L, 0L));
            assertThrows(IllegalArgumentException.class, () -> new BankRetryConfig(3, 100L, 2.0, 2000L, -500L));
        }

        @Test
        @DisplayName("Requirement 5: Should correctly calculate exponential backoff durations")
        void testExponentialBackoffCalculation() {
            BankRetryConfig config = new BankRetryConfig(5, 100L, 2.0, 1000L, 5000L);

            // Attempt 1 (delay before 2nd attempt): 100ms
            assertEquals(Duration.ofMillis(100), config.calculateBackoff(1));

            // Attempt 2 (delay before 3rd attempt): 100 * 2^1 = 200ms
            assertEquals(Duration.ofMillis(200), config.calculateBackoff(2));

            // Attempt 3 (delay before 4th attempt): 100 * 2^2 = 400ms
            assertEquals(Duration.ofMillis(400), config.calculateBackoff(3));

            // Attempt 4 (delay before 5th attempt): 100 * 2^3 = 800ms
            assertEquals(Duration.ofMillis(800), config.calculateBackoff(4));

            // Attempt 5 (delay before 6th attempt): 100 * 2^4 = 1600ms, capped at 1000ms
            assertEquals(Duration.ofMillis(1000), config.calculateBackoff(5));

            // Edge cases: attempt 0 or negative
            assertEquals(Duration.ofMillis(100), config.calculateBackoff(0));
            assertEquals(Duration.ofMillis(100), config.calculateBackoff(-2));
        }

        @Test
        @DisplayName("Requirement 6: Should enforce maximum backoff cap and avoid arithmetic overflow")
        void testMaximumBackoffEnforcementAndOverflowSafety() {
            BankRetryConfig config = new BankRetryConfig(50, 100L, 2.0, 500L, 60000L);

            // 100 * 2^3 = 800ms, capped at 500ms
            assertEquals(Duration.ofMillis(500), config.calculateBackoff(4));

            // Very high attempts (e.g. 35, 45) must safely cap at maxBackoff without overflow
            assertEquals(Duration.ofMillis(500), config.calculateBackoff(35));
            assertEquals(Duration.ofMillis(500), config.calculateBackoff(45));
        }

        @Test
        @DisplayName("Requirement 5: Should calculate backoff with multiplier 1.0 (constant backoff)")
        void testConstantBackoffCalculation() {
            BankRetryConfig constantConfig = new BankRetryConfig(3, 250L, 1.0, 500L, 5000L);
            assertEquals(Duration.ofMillis(250), constantConfig.calculateBackoff(1));
            assertEquals(Duration.ofMillis(250), constantConfig.calculateBackoff(2));
            assertEquals(Duration.ofMillis(250), constantConfig.calculateBackoff(3));
        }
    }

    // =========================================================================
    // 3. Centralized Retry Mechanism Behavior & Error Handling
    // =========================================================================

    @Nested
    @DisplayName("3. Centralized Retry Behavior & Attempts")
    class CentralizedRetryTests {

        @Test
        @DisplayName("Requirement 1: Operation succeeds on first attempt without retrying")
        void testSuccessOnFirstAttempt() throws InterruptedException {
            UUID accountId = UUID.randomUUID();
            GetBalanceRequest request = new GetBalanceRequest(accountId);

            Bank.GetBalanceResponse mockResponse = Bank.GetBalanceResponse.newBuilder()
                    .setAccountId(accountId.toString())
                    .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(1000).build())
                    .setAvailable(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(1000).build())
                    .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(0).build())
                    .build();

            when(blockingStub.getBalance(any())).thenReturn(mockResponse);

            GetBalanceResponse response = bankClient.getBalance(request);

            assertNotNull(response);
            verify(blockingStub, times(1)).getBalance(any());
            verify(sleeper, never()).sleep(any());
        }

        @Test
        @DisplayName("Requirement 2: Operation fails with UNAVAILABLE once and succeeds on second attempt")
        void testSuccessOnSecondAttempt() throws InterruptedException {
            UUID accountId = UUID.randomUUID();
            GetBalanceRequest request = new GetBalanceRequest(accountId);

            Bank.GetBalanceResponse mockResponse = Bank.GetBalanceResponse.newBuilder()
                    .setAccountId(accountId.toString())
                    .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(5000).build())
                    .setAvailable(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(5000).build())
                    .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(0).build())
                    .build();

            when(blockingStub.getBalance(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Temporary network drop")))
                    .thenReturn(mockResponse);

            GetBalanceResponse response = bankClient.getBalance(request);

            assertNotNull(response);
            verify(blockingStub, times(2)).getBalance(any());
            verify(sleeper, times(1)).sleep(Duration.ofMillis(100));
        }

        @Test
        @DisplayName("Requirement 2: Operation fails twice with UNAVAILABLE and succeeds on third attempt")
        void testSuccessOnThirdAttempt() throws InterruptedException {
            UUID accountId = UUID.randomUUID();
            GetBalanceRequest request = new GetBalanceRequest(accountId);

            Bank.GetBalanceResponse mockResponse = Bank.GetBalanceResponse.newBuilder()
                    .setAccountId(accountId.toString())
                    .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(5000).build())
                    .setAvailable(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(5000).build())
                    .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(0).build())
                    .build();

            when(blockingStub.getBalance(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Drop 1")))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Drop 2")))
                    .thenReturn(mockResponse);

            GetBalanceResponse response = bankClient.getBalance(request);

            assertNotNull(response);
            verify(blockingStub, times(3)).getBalance(any());
            verify(sleeper, times(1)).sleep(Duration.ofMillis(100));
            verify(sleeper, times(1)).sleep(Duration.ofMillis(200));
        }

        @Test
        @DisplayName("Requirements 3 & 12: Operation exhausts all configured attempts and propagates BankRetryExhaustedException")
        void testExhaustedRetriesPropagatesBankRetryExhaustedException() throws InterruptedException {
            GetBalanceRequest request = new GetBalanceRequest(UUID.randomUUID());

            StatusRuntimeException cause = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Server down permanently"));
            when(blockingStub.getBalance(any())).thenThrow(cause);

            BankRetryExhaustedException ex = assertThrows(BankRetryExhaustedException.class, () -> bankClient.getBalance(request));

            assertEquals(Status.Code.UNAVAILABLE, ex.getStatus().getCode());
            assertEquals(3, ex.getAttemptsMade());
            assertEquals("getBalance", ex.getOperationName());
            assertSame(cause, ex.getCause());
            assertTrue(ex.getMessage().contains("Retry exhausted after 3 attempts for operation 'getBalance'"));
            // Ensure no sensitive transaction/account info is leaked
            assertFalse(ex.getMessage().contains(request.getAccountId().toString()));

            verify(blockingStub, times(3)).getBalance(any());
            verify(sleeper, times(2)).sleep(any());
        }

        @Test
        @DisplayName("Requirements 4 & 12: Permanent non-retryable error terminates immediately without retrying")
        void testNonRetryableErrorsNotRetried() throws InterruptedException {
            HoldRequest request = new HoldRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), 30, UUID.randomUUID());

            when(blockingStub.hold(any()))
                    .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("Bad request payload")));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> bankClient.hold(request));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
            assertFalse(ex instanceof BankRetryExhaustedException, "Should not be wrapped in BankRetryExhaustedException");
            verify(blockingStub, times(1)).hold(any());
            verify(sleeper, never()).sleep(any());
        }

        @Test
        @DisplayName("Requirements 4 & 12: Non-UNAVAILABLE status codes terminate immediately without retrying")
        void testOtherStatusCodesNotRetried() throws InterruptedException {
            CaptureRequest request = new CaptureRequest(UUID.randomUUID(), UUID.randomUUID());

            Status[] nonRetryableStatuses = new Status[] {
                    Status.NOT_FOUND,
                    Status.PERMISSION_DENIED,
                    Status.ALREADY_EXISTS,
                    Status.INTERNAL,
                    Status.FAILED_PRECONDITION,
                    Status.UNAUTHENTICATED
            };

            for (Status status : nonRetryableStatuses) {
                reset(blockingStub, sleeper);
                when(blockingStub.capture(any())).thenThrow(new StatusRuntimeException(status.withDescription("Error")));

                StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> bankClient.capture(request));
                assertEquals(status.getCode(), ex.getStatus().getCode());
                verify(blockingStub, times(1)).capture(any());
                verify(sleeper, never()).sleep(any());
            }
        }

        @Test
        @DisplayName("Requirement 13: InterruptedException during sleep restores interrupt flag and throws CANCELLED")
        void testInterruptedSleep() throws InterruptedException {
            GetBalanceRequest request = new GetBalanceRequest(UUID.randomUUID());

            when(blockingStub.getBalance(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Temporary error")));
            doThrow(new InterruptedException("Interrupted during backoff")).when(sleeper).sleep(any());

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> bankClient.getBalance(request));

            assertEquals(Status.Code.CANCELLED, ex.getStatus().getCode());
            assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag should be set on current thread");
            Thread.interrupted(); // Clean up thread interrupt state
            verify(blockingStub, times(1)).getBalance(any());
        }
    }

    // =========================================================================
    // 4. Retry Deadline & Timeout Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("4. Retry Deadline & Timeout Lifecycle")
    class RetryTimeoutTests {

        @Test
        @DisplayName("Requirements 7 & 8: Stops retrying and throws BankRetryTimeoutException when deadline expires")
        void testRetryDeadlineExpires() throws InterruptedException {
            // Configure 5 attempts with a short 250ms deadline
            BankRetryConfig shortTimeoutConfig = new BankRetryConfig(5, 100L, 2.0, 1000L, 250L);
            BankRetryExecutor timeoutExecutor = new BankRetryExecutor(shortTimeoutConfig, sleeper, timeSource);
            BankClient timeoutClient = new BankClient(channel, blockingStub, timeoutExecutor);

            GetBalanceRequest request = new GetBalanceRequest(UUID.randomUUID());

            when(blockingStub.getBalance(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Bank unavailable")));

            BankRetryTimeoutException ex = assertThrows(BankRetryTimeoutException.class, () -> timeoutClient.getBalance(request));

            assertEquals(Status.Code.DEADLINE_EXCEEDED, ex.getStatus().getCode());
            assertEquals("getBalance", ex.getOperationName());
            assertEquals(Duration.ofMillis(250), ex.getTimeoutLimit());

            // Attempt 1 fails (T=0), sleeps 100ms. Attempt 2 fails (T=100ms), sleeps 150ms (capped at remaining 150ms).
            // Total elapsed = 250ms. Deadline reached before Attempt 3!
            assertEquals(2, ex.getAttemptsMade());
            verify(blockingStub, times(2)).getBalance(any());
        }

        @Test
        @DisplayName("Requirement 8: Does not begin an attempt if remaining time is insufficient / expired")
        void testNoAttemptWhenDeadlineExpiredBeforehand() {
            BankRetryConfig config = new BankRetryConfig(3, 100L, 2.0, 1000L, 100L);
            AtomicLong customTime = new AtomicLong(10_000_000_000L);
            TimeSource customSource = customTime::get;
            BankRetryExecutor timeoutExecutor = new BankRetryExecutor(config, sleeper, customSource);
            BankClient timeoutClient = new BankClient(channel, blockingStub, timeoutExecutor);

            // Attempt 1 fails, and time advances by 200ms (exceeding 100ms timeout)
            when(blockingStub.getBalance(any())).thenAnswer(inv -> {
                customTime.addAndGet(Duration.ofMillis(200).toNanos());
                throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Fail"));
            });

            BankRetryTimeoutException ex = assertThrows(BankRetryTimeoutException.class,
                    () -> timeoutClient.getBalance(new GetBalanceRequest(UUID.randomUUID())));

            assertEquals(Status.Code.DEADLINE_EXCEEDED, ex.getStatus().getCode());
            assertEquals(1, ex.getAttemptsMade());
            // Attempt 2 was NEVER begun because remaining time was expired
            verify(blockingStub, times(1)).getBalance(any());
        }

        @Test
        @DisplayName("Requirement 7: Caps backoff delay to remaining deadline time")
        void testBackoffCappedToRemainingDeadline() throws InterruptedException {
            // Initial backoff = 200ms, but remaining deadline is only 150ms
            BankRetryConfig config = new BankRetryConfig(3, 200L, 2.0, 1000L, 150L);
            BankRetryExecutor timeoutExecutor = new BankRetryExecutor(config, sleeper, timeSource);
            BankClient timeoutClient = new BankClient(channel, blockingStub, timeoutExecutor);

            when(blockingStub.getBalance(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Drop")));

            assertThrows(BankRetryTimeoutException.class,
                    () -> timeoutClient.getBalance(new GetBalanceRequest(UUID.randomUUID())));

            ArgumentCaptor<Duration> delayCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(sleeper).sleep(delayCaptor.capture());

            // The sleep delay should be capped at remaining deadline (150ms), not the full backoff (200ms)
            assertEquals(150, delayCaptor.getValue().toMillis(), "Sleep delay must be capped to remaining deadline");
        }
    }

    // =========================================================================
    // 5. Idempotency Key & Transaction Identifier Preservation Across Retries
    // =========================================================================

    @Nested
    @DisplayName("5. Idempotency & Request Parameter Preservation")
    class IdempotencyPreservationTests {

        @Test
        @DisplayName("Requirements 9, 10, 11: Hold preserves exact same idempotency key and tx parameters across retries")
        void testHoldPreservesParametersAcrossRetries() {
            UUID idempotencyKey = UUID.randomUUID();
            UUID accountId = UUID.randomUUID();
            UUID transactionRef = UUID.randomUUID();
            Money amount = new Money(5000, "USD");
            long ttlSeconds = 60;

            HoldRequest request = new HoldRequest(idempotencyKey, accountId, amount, ttlSeconds, transactionRef);

            UUID holdId = UUID.randomUUID();
            Bank.HoldResponse successResponse = Bank.HoldResponse.newBuilder()
                    .setHoldId(holdId.toString())
                    .setTransactionId(holdId.toString())
                    .setStatus(Bank.TransactionStatus.HELD)
                    .setAccountId(accountId.toString())
                    .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(10000).build())
                    .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(5000).build())
                    .build();

            when(blockingStub.hold(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Drop 1")))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Drop 2")))
                    .thenReturn(successResponse);

            HoldResponse response = bankClient.hold(request);

            assertNotNull(response);
            assertEquals(holdId, response.getHoldId());
            assertEquals(TransactionStatus.HELD, response.getStatus());
            assertEquals(HoldStatus.ACTIVE, response.getHoldStatus());

            ArgumentCaptor<Bank.HoldRequest> captor = ArgumentCaptor.forClass(Bank.HoldRequest.class);
            verify(blockingStub, times(3)).hold(captor.capture());

            List<Bank.HoldRequest> capturedRequests = captor.getAllValues();
            assertEquals(3, capturedRequests.size());

            // Verify that every retry passed the exact same identity and transaction details
            for (Bank.HoldRequest req : capturedRequests) {
                assertEquals(idempotencyKey.toString(), req.getIdempotencyKey(), "Idempotency key must match on all retries");
                assertEquals(accountId.toString(), req.getAccountId(), "Account ID must match on all retries");
                assertEquals(5000, req.getAmount().getAmountInUnits(), "Amount units must match on all retries");
                assertEquals("USD", req.getAmount().getCurrency(), "Currency must match on all retries");
                assertEquals(ttlSeconds, req.getTtlSeconds(), "TTL seconds must match on all retries");
                assertEquals(transactionRef.toString(), req.getTransactionReference(), "Transaction ref must match on all retries");
            }
        }

        @Test
        @DisplayName("Requirements 9, 10, 11: Capture preserves exact same idempotency key and holdId across retries")
        void testCapturePreservesParametersAcrossRetries() {
            UUID idempotencyKey = UUID.randomUUID();
            UUID holdId = UUID.randomUUID();
            CaptureRequest request = new CaptureRequest(idempotencyKey, holdId);

            Bank.CaptureResponse successResponse = Bank.CaptureResponse.newBuilder()
                    .setHoldId(holdId.toString())
                    .setTransactionId(holdId.toString())
                    .setStatus(Bank.TransactionStatus.CAPTURED)
                    .setAccountId(UUID.randomUUID().toString())
                    .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(8000).build())
                    .setCapturedAmount(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(2000).build())
                    .build();

            when(blockingStub.capture(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(successResponse);

            CaptureResponse response = bankClient.capture(request);
            assertNotNull(response);
            assertEquals(TransactionStatus.CAPTURED, response.getStatus());

            ArgumentCaptor<Bank.CaptureRequest> captor = ArgumentCaptor.forClass(Bank.CaptureRequest.class);
            verify(blockingStub, times(2)).capture(captor.capture());

            for (Bank.CaptureRequest req : captor.getAllValues()) {
                assertEquals(idempotencyKey.toString(), req.getIdempotencyKey());
                assertEquals(holdId.toString(), req.getHoldId());
            }
        }

        @Test
        @DisplayName("Requirements 9, 10, 11: Release preserves exact same idempotency key, holdId, and reason across retries")
        void testReleasePreservesParametersAcrossRetries() {
            UUID idempotencyKey = UUID.randomUUID();
            UUID holdId = UUID.randomUUID();
            String reason = "User aborted checkout";
            ReleaseRequest request = new ReleaseRequest(idempotencyKey, holdId, reason);

            Bank.ReleaseResponse successResponse = Bank.ReleaseResponse.newBuilder()
                    .setHoldId(holdId.toString())
                    .setTransactionId(holdId.toString())
                    .setStatus(Bank.TransactionStatus.COMPLETED)
                    .setAccountId(UUID.randomUUID().toString())
                    .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(0).build())
                    .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(10000).build())
                    .build();

            when(blockingStub.release(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(successResponse);

            ReleaseResponse response = bankClient.release(request);
            assertNotNull(response);
            assertEquals(TransactionStatus.COMPLETED, response.getStatus());

            ArgumentCaptor<Bank.ReleaseRequest> captor = ArgumentCaptor.forClass(Bank.ReleaseRequest.class);
            verify(blockingStub, times(2)).release(captor.capture());

            for (Bank.ReleaseRequest req : captor.getAllValues()) {
                assertEquals(idempotencyKey.toString(), req.getIdempotencyKey());
                assertEquals(holdId.toString(), req.getHoldId());
                assertEquals(reason, req.getReason());
            }
        }

        @Test
        @DisplayName("Requirements 9, 10, 11: Credit preserves exact same idempotency key and destination account across retries")
        void testCreditPreservesParametersAcrossRetries() {
            UUID idempotencyKey = UUID.randomUUID();
            UUID destAccountId = UUID.randomUUID();
            Money amount = new Money(7500, "USD");
            UUID txRef = UUID.randomUUID();
            CreditRequest request = new CreditRequest(idempotencyKey, destAccountId, amount, txRef);

            Bank.CreditResponse successResponse = Bank.CreditResponse.newBuilder()
                    .setTransactionId(UUID.randomUUID().toString())
                    .setStatus(Bank.TransactionStatus.COMPLETED)
                    .setAccountId(destAccountId.toString())
                    .setBankReferenceNumber("CR-98765")
                    .build();

            when(blockingStub.credit(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(successResponse);

            CreditResponse response = bankClient.credit(request);
            assertNotNull(response);
            assertEquals(TransactionStatus.COMPLETED, response.getStatus());

            ArgumentCaptor<Bank.CreditRequest> captor = ArgumentCaptor.forClass(Bank.CreditRequest.class);
            verify(blockingStub, times(2)).credit(captor.capture());

            for (Bank.CreditRequest req : captor.getAllValues()) {
                assertEquals(idempotencyKey.toString(), req.getIdempotencyKey());
                assertEquals(destAccountId.toString(), req.getDestinationAccountId());
                assertEquals(7500, req.getAmount().getAmountInUnits());
                assertEquals("USD", req.getAmount().getCurrency());
                assertEquals(txRef.toString(), req.getTransactionReference());
            }
        }

        @Test
        @DisplayName("Requirements 9, 10, 11: Reverse preserves exact same idempotency key and original txId across retries")
        void testReversePreservesParametersAcrossRetries() {
            UUID idempotencyKey = UUID.randomUUID();
            UUID origTxId = UUID.randomUUID();
            Money amount = new Money(2500, "USD");
            String reason = "Duplicate charge";
            ReverseRequest request = new ReverseRequest(idempotencyKey, origTxId, amount, reason);

            Bank.ReverseResponse successResponse = Bank.ReverseResponse.newBuilder()
                    .setOriginalTransactionId(origTxId.toString())
                    .setReversalTransactionId(UUID.randomUUID().toString())
                    .setStatus(Bank.TransactionStatus.REVERSED)
                    .setAccountId(UUID.randomUUID().toString())
                    .setBankReferenceNumber("RV-12345")
                    .build();

            when(blockingStub.reverse(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(successResponse);

            ReverseResponse response = bankClient.reverse(request);
            assertNotNull(response);
            assertEquals(TransactionStatus.REVERSED, response.getStatus());

            ArgumentCaptor<Bank.ReverseRequest> captor = ArgumentCaptor.forClass(Bank.ReverseRequest.class);
            verify(blockingStub, times(2)).reverse(captor.capture());

            for (Bank.ReverseRequest req : captor.getAllValues()) {
                assertEquals(idempotencyKey.toString(), req.getIdempotencyKey());
                assertEquals(origTxId.toString(), req.getTransactionId());
                assertEquals(2500, req.getAmountToReverse().getAmountInUnits());
                assertEquals("USD", req.getAmountToReverse().getCurrency());
                assertEquals(reason, req.getReason());
            }
        }
    }

    // =========================================================================
    // 6. Requirement 15: Consistent Retry Behavior Across All Six Operations
    // =========================================================================

    @Nested
    @DisplayName("6. Consistent Retry Across All Six Operations")
    class AllSixOperationsRetryTests {

        @Test
        @DisplayName("getBalance retries on UNAVAILABLE and succeeds on 2nd attempt")
        void testGetBalanceRetrySuccess() {
            UUID accountId = UUID.randomUUID();
            when(blockingStub.getBalance(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(Bank.GetBalanceResponse.newBuilder()
                            .setAccountId(accountId.toString())
                            .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(0).build())
                            .setAvailable(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(1000).build())
                            .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(1000).build())
                            .build());

            GetBalanceResponse res = bankClient.getBalance(new GetBalanceRequest(accountId));
            assertNotNull(res);
            verify(blockingStub, times(2)).getBalance(any());
        }

        @Test
        @DisplayName("hold retries on UNAVAILABLE and succeeds on 2nd attempt")
        void testHoldRetrySuccess() {
            when(blockingStub.hold(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(Bank.HoldResponse.newBuilder()
                            .setHoldId(UUID.randomUUID().toString())
                            .setTransactionId(UUID.randomUUID().toString())
                            .setStatus(Bank.TransactionStatus.HELD)
                            .setAccountId(UUID.randomUUID().toString())
                            .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(1000).build())
                            .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(500).build())
                            .build());

            HoldResponse res = bankClient.hold(new HoldRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(500, "USD"), 30, UUID.randomUUID()));
            assertNotNull(res);
            assertEquals(TransactionStatus.HELD, res.getStatus());
            verify(blockingStub, times(2)).hold(any());
        }

        @Test
        @DisplayName("capture retries on UNAVAILABLE and succeeds on 2nd attempt")
        void testCaptureRetrySuccess() {
            when(blockingStub.capture(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(Bank.CaptureResponse.newBuilder()
                            .setHoldId(UUID.randomUUID().toString())
                            .setTransactionId(UUID.randomUUID().toString())
                            .setStatus(Bank.TransactionStatus.CAPTURED)
                            .setAccountId(UUID.randomUUID().toString())
                            .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(500).build())
                            .setCapturedAmount(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(500).build())
                            .build());

            CaptureResponse res = bankClient.capture(new CaptureRequest(UUID.randomUUID(), UUID.randomUUID()));
            assertNotNull(res);
            assertEquals(TransactionStatus.CAPTURED, res.getStatus());
            verify(blockingStub, times(2)).capture(any());
        }

        @Test
        @DisplayName("release retries on UNAVAILABLE and succeeds on 2nd attempt")
        void testReleaseRetrySuccess() {
            when(blockingStub.release(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(Bank.ReleaseResponse.newBuilder()
                            .setHoldId(UUID.randomUUID().toString())
                            .setTransactionId(UUID.randomUUID().toString())
                            .setStatus(Bank.TransactionStatus.COMPLETED)
                            .setAccountId(UUID.randomUUID().toString())
                            .setHeld(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(0).build())
                            .setBalance(Bank.Money.newBuilder().setCurrency("USD").setAmountInUnits(1000).build())
                            .build());

            ReleaseResponse res = bankClient.release(new ReleaseRequest(UUID.randomUUID(), UUID.randomUUID(), "void"));
            assertNotNull(res);
            assertEquals(TransactionStatus.COMPLETED, res.getStatus());
            verify(blockingStub, times(2)).release(any());
        }

        @Test
        @DisplayName("credit retries on UNAVAILABLE and succeeds on 2nd attempt")
        void testCreditRetrySuccess() {
            when(blockingStub.credit(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(Bank.CreditResponse.newBuilder()
                            .setTransactionId(UUID.randomUUID().toString())
                            .setStatus(Bank.TransactionStatus.COMPLETED)
                            .setAccountId(UUID.randomUUID().toString())
                            .setBankReferenceNumber("CR-555")
                            .build());

            CreditResponse res = bankClient.credit(new CreditRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), UUID.randomUUID()));
            assertNotNull(res);
            assertEquals(TransactionStatus.COMPLETED, res.getStatus());
            verify(blockingStub, times(2)).credit(any());
        }

        @Test
        @DisplayName("reverse retries on UNAVAILABLE and succeeds on 2nd attempt")
        void testReverseRetrySuccess() {
            when(blockingStub.reverse(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                    .thenReturn(Bank.ReverseResponse.newBuilder()
                            .setOriginalTransactionId(UUID.randomUUID().toString())
                            .setReversalTransactionId(UUID.randomUUID().toString())
                            .setStatus(Bank.TransactionStatus.REVERSED)
                            .setAccountId(UUID.randomUUID().toString())
                            .setBankReferenceNumber("RV-777")
                            .build());

            ReverseResponse res = bankClient.reverse(new ReverseRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), "refund"));
            assertNotNull(res);
            assertEquals(TransactionStatus.REVERSED, res.getStatus());
            verify(blockingStub, times(2)).reverse(any());
        }

        @Test
        @DisplayName("All six operations throw BankRetryExhaustedException when all attempts fail")
        void testAllSixOperationsExhaustion() {
            when(blockingStub.getBalance(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(blockingStub.hold(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(blockingStub.capture(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(blockingStub.release(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(blockingStub.credit(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(blockingStub.reverse(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThrows(BankRetryExhaustedException.class, () -> bankClient.getBalance(new GetBalanceRequest(UUID.randomUUID())));
            verify(blockingStub, times(3)).getBalance(any());

            assertThrows(BankRetryExhaustedException.class, () -> bankClient.hold(new HoldRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), 30, UUID.randomUUID())));
            verify(blockingStub, times(3)).hold(any());

            assertThrows(BankRetryExhaustedException.class, () -> bankClient.capture(new CaptureRequest(UUID.randomUUID(), UUID.randomUUID())));
            verify(blockingStub, times(3)).capture(any());

            assertThrows(BankRetryExhaustedException.class, () -> bankClient.release(new ReleaseRequest(UUID.randomUUID(), UUID.randomUUID(), "cancel")));
            verify(blockingStub, times(3)).release(any());

            assertThrows(BankRetryExhaustedException.class, () -> bankClient.credit(new CreditRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), UUID.randomUUID())));
            verify(blockingStub, times(3)).credit(any());

            assertThrows(BankRetryExhaustedException.class, () -> bankClient.reverse(new ReverseRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), "refund")));
            verify(blockingStub, times(3)).reverse(any());
        }
    }

    // =========================================================================
    // 7. Response Mapping & Business Failure Verification (From Step 1)
    // =========================================================================

    @Nested
    @DisplayName("7. Business Response Mapping & Validation")
    class ResponseMappingTests {

        @Test
        @DisplayName("Should correctly map Hold failure response and error message without retrying")
        void testHoldFailureResponseMapping() {
            UUID accountId = UUID.randomUUID();
            HoldRequest request = new HoldRequest(UUID.randomUUID(), accountId, new Money(10000, "USD"), 30, UUID.randomUUID());

            Bank.HoldResponse mockResponse = Bank.HoldResponse.newBuilder()
                    .setStatus(Bank.TransactionStatus.FAILED)
                    .setAccountId(accountId.toString())
                    .setErrorMessage("insufficient balance")
                    .build();

            when(blockingStub.hold(any())).thenReturn(mockResponse);

            HoldResponse response = bankClient.hold(request);

            assertEquals(TransactionStatus.FAILED, response.getStatus());
            assertEquals(BankFailureReason.INSUFFICIENT_FUNDS, response.getFailureReason());
            assertNull(response.getHoldStatus());
            assertEquals("insufficient balance", response.getErrorMessage());
            // Successful RPC completion with business failure is not retried
            verify(blockingStub, times(1)).hold(any());
        }

        @Test
        @DisplayName("Should map Capture failure with HOLD_EXPIRED without retrying")
        void testCaptureFailureMapping() {
            CaptureRequest request = new CaptureRequest(UUID.randomUUID(), UUID.randomUUID());

            Bank.CaptureResponse mockResponse = Bank.CaptureResponse.newBuilder()
                    .setHoldId(request.getHoldId().toString())
                    .setStatus(Bank.TransactionStatus.FAILED)
                    .setErrorMessage("transaction expired")
                    .build();

            when(blockingStub.capture(any())).thenReturn(mockResponse);

            CaptureResponse response = bankClient.capture(request);

            assertEquals(TransactionStatus.FAILED, response.getStatus());
            assertEquals(BankFailureReason.HOLD_EXPIRED, response.getFailureReason());
            assertNull(response.getHoldStatus());
            verify(blockingStub, times(1)).capture(any());
        }

        @Test
        @DisplayName("Should map Release failure with HOLD_ALREADY_RELEASED without retrying")
        void testReleaseFailureMapping() {
            ReleaseRequest request = new ReleaseRequest(UUID.randomUUID(), UUID.randomUUID(), "retry");

            Bank.ReleaseResponse mockResponse = Bank.ReleaseResponse.newBuilder()
                    .setStatus(Bank.TransactionStatus.FAILED)
                    .setErrorMessage("hold already released")
                    .build();

            when(blockingStub.release(any())).thenReturn(mockResponse);

            ReleaseResponse response = bankClient.release(request);

            assertEquals(TransactionStatus.FAILED, response.getStatus());
            assertEquals(BankFailureReason.HOLD_ALREADY_RELEASED, response.getFailureReason());
            assertNull(response.getHoldStatus());
            verify(blockingStub, times(1)).release(any());
        }

        @Test
        @DisplayName("Should map Credit failure with ACCOUNT_LOCKED without retrying")
        void testCreditFailureMapping() {
            CreditRequest request = new CreditRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), UUID.randomUUID());

            Bank.CreditResponse mockResponse = Bank.CreditResponse.newBuilder()
                    .setStatus(Bank.TransactionStatus.FAILED)
                    .setErrorMessage("account locked by bank")
                    .build();

            when(blockingStub.credit(any())).thenReturn(mockResponse);

            CreditResponse response = bankClient.credit(request);

            assertEquals(TransactionStatus.FAILED, response.getStatus());
            assertEquals(BankFailureReason.ACCOUNT_LOCKED, response.getFailureReason());
            verify(blockingStub, times(1)).credit(any());
        }

        @Test
        @DisplayName("Should map Reverse failure with HOLD_NOT_FOUND without retrying")
        void testReverseFailureMapping() {
            ReverseRequest request = new ReverseRequest(UUID.randomUUID(), UUID.randomUUID(), new Money(100, "USD"), "refund");

            Bank.ReverseResponse mockResponse = Bank.ReverseResponse.newBuilder()
                    .setStatus(Bank.TransactionStatus.FAILED)
                    .setErrorMessage("transaction not found")
                    .build();

            when(blockingStub.reverse(any())).thenReturn(mockResponse);

            ReverseResponse response = bankClient.reverse(request);

            assertEquals(TransactionStatus.FAILED, response.getStatus());
            assertEquals(BankFailureReason.HOLD_NOT_FOUND, response.getFailureReason());
            verify(blockingStub, times(1)).reverse(any());
        }

        @Test
        @DisplayName("Should reject null request inputs on all six operations")
        void testNullRequestValidation() {
            assertThrows(IllegalArgumentException.class, () -> bankClient.getBalance((GetBalanceRequest) null));
            assertThrows(IllegalArgumentException.class, () -> bankClient.getBalance((Bank.GetBalanceRequest) null));

            assertThrows(IllegalArgumentException.class, () -> bankClient.hold((HoldRequest) null));
            assertThrows(IllegalArgumentException.class, () -> bankClient.hold((Bank.HoldRequest) null));

            assertThrows(IllegalArgumentException.class, () -> bankClient.capture((CaptureRequest) null));
            assertThrows(IllegalArgumentException.class, () -> bankClient.capture((Bank.CaptureRequest) null));

            assertThrows(IllegalArgumentException.class, () -> bankClient.release((ReleaseRequest) null));
            assertThrows(IllegalArgumentException.class, () -> bankClient.release((Bank.ReleaseRequest) null));

            assertThrows(IllegalArgumentException.class, () -> bankClient.credit((CreditRequest) null));
            assertThrows(IllegalArgumentException.class, () -> bankClient.credit((Bank.CreditRequest) null));

            assertThrows(IllegalArgumentException.class, () -> bankClient.reverse((ReverseRequest) null));
            assertThrows(IllegalArgumentException.class, () -> bankClient.reverse((Bank.ReverseRequest) null));
        }
    }

    // =========================================================================
    // 8. Channel Lifecycle & Resource Management
    // =========================================================================

    @Nested
    @DisplayName("8. Channel Lifecycle & Resource Management")
    class LifecycleTests {

        @Test
        @DisplayName("Should gracefully shut down channel when open")
        void testGracefulShutdown() throws InterruptedException {
            when(channel.isShutdown()).thenReturn(false);
            when(channel.awaitTermination(anyLong(), any())).thenReturn(true);

            bankClient.shutdown();

            verify(channel).shutdown();
            verify(channel).awaitTermination(5, TimeUnit.SECONDS);
            verify(channel, never()).shutdownNow();
        }

        @Test
        @DisplayName("Should forcefully terminate channel if graceful shutdown times out")
        void testForcedShutdownOnTimeout() throws InterruptedException {
            when(channel.isShutdown()).thenReturn(false);
            when(channel.awaitTermination(anyLong(), any())).thenReturn(false);

            bankClient.shutdown();

            verify(channel).shutdown();
            verify(channel).awaitTermination(5, TimeUnit.SECONDS);
            verify(channel).shutdownNow();
        }

        @Test
        @DisplayName("Should forcefully terminate channel and restore interrupt flag on interruption")
        void testShutdownInterrupted() throws InterruptedException {
            when(channel.isShutdown()).thenReturn(false);
            when(channel.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException("Interrupted"));

            bankClient.shutdown();

            verify(channel).shutdown();
            verify(channel).shutdownNow();
            assertTrue(Thread.currentThread().isInterrupted(), "Thread interrupt flag should be restored");
            Thread.interrupted();
        }

        @Test
        @DisplayName("Should not attempt shutdown if channel is already shut down")
        void testAlreadyShutdown() {
            when(channel.isShutdown()).thenReturn(true);

            bankClient.shutdown();

            verify(channel, never()).shutdown();
        }

        @Test
        @DisplayName("Should invoke shutdown on close() via AutoCloseable")
        void testAutoCloseable() throws InterruptedException {
            when(channel.isShutdown()).thenReturn(false);
            when(channel.awaitTermination(anyLong(), any())).thenReturn(true);

            bankClient.close();

            verify(channel).shutdown();
        }
    }
}
