package com.paymentsystem.repository;

import com.paymentsystem.model.LinkedBankAccount;
import com.paymentsystem.model.Role;
import com.paymentsystem.model.Transaction;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransactionAndLinkedBankAccountRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LinkedBankAccountRepository linkedBankAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should persist and retrieve a Transaction entity from PostgreSQL")
    void shouldPersistAndRetrieveTransaction() {
        String idempotencyKey = "idemp-" + UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccount("ACC_1001")
                .toAccount("ACC_2002")
                .amount(new BigDecimal("150.5000"))
                .currency("USD")
                .status(TransactionStatus.PENDING)
                .holdId("HOLD_987")
                .captureTxnId("CAP_654")
                .creditTxnId("CRED_321")
                .build();

        Transaction savedTx = transactionRepository.save(tx);
        assertThat(savedTx.getId()).isNotNull();
        assertThat(savedTx.getCreatedAt()).isNotNull();
        assertThat(savedTx.getExpiresAt()).isNotNull();

        Optional<Transaction> retrievedOpt = transactionRepository.findById(savedTx.getId());
        assertThat(retrievedOpt).isPresent();

        Transaction retrieved = retrievedOpt.get();
        assertThat(retrieved.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(retrieved.getFromAccount()).isEqualTo("ACC_1001");
        assertThat(retrieved.getToAccount()).isEqualTo("ACC_2002");
        assertThat(retrieved.getAmount()).isEqualByComparingTo(new BigDecimal("150.5000"));
        assertThat(retrieved.getCurrency()).isEqualTo("USD");
        assertThat(retrieved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(retrieved.getHoldId()).isEqualTo("HOLD_987");
        assertThat(retrieved.getCaptureTxnId()).isEqualTo("CAP_654");
        assertThat(retrieved.getCreditTxnId()).isEqualTo("CRED_321");
    }

    @Test
    @DisplayName("Should query Transaction by idempotencyKey")
    void shouldQueryTransactionByIdempotencyKey() {
        String idempotencyKey = "key-query-" + UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .fromAccount("ACC_A")
                .toAccount("ACC_B")
                .amount(new BigDecimal("25.0000"))
                .currency("EUR")
                .status(TransactionStatus.PENDING)
                .build();

        transactionRepository.save(tx);

        Optional<Transaction> found = transactionRepository.findByIdempotencyKey(idempotencyKey);
        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isTrue();
        assertThat(transactionRepository.existsByIdempotencyKey("non-existent-key")).isFalse();
    }

    @Test
    @DisplayName("Should reject duplicate idempotency_key values at database level")
    void shouldRejectDuplicateIdempotencyKey() {
        String sharedKey = "dup-key-" + UUID.randomUUID();

        Transaction tx1 = Transaction.builder()
                .idempotencyKey(sharedKey)
                .fromAccount("ACC_1")
                .toAccount("ACC_2")
                .amount(new BigDecimal("10.0000"))
                .currency("USD")
                .build();

        transactionRepository.saveAndFlush(tx1);

        Transaction tx2 = Transaction.builder()
                .idempotencyKey(sharedKey)
                .fromAccount("ACC_3")
                .toAccount("ACC_4")
                .amount(new BigDecimal("20.0000"))
                .currency("USD")
                .build();

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(tx2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should persist and retrieve LinkedBankAccount entity from PostgreSQL")
    void shouldPersistAndRetrieveLinkedBankAccount() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User("user_" + uniqueSuffix, "user_" + uniqueSuffix + "@example.com", "hash123", Role.USER);
        user = userRepository.save(user);

        String bankAccountId = "BANK_ACC_" + uniqueSuffix;
        LinkedBankAccount account = new LinkedBankAccount(
                user,
                bankAccountId,
                "INR",
                50000L,
                1000L,
                "State Bank",
                "John Doe"
        );

        LinkedBankAccount savedAccount = linkedBankAccountRepository.save(account);
        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getCreatedAt()).isNotNull();

        Optional<LinkedBankAccount> retrievedOpt = linkedBankAccountRepository.findById(savedAccount.getId());
        assertThat(retrievedOpt).isPresent();

        LinkedBankAccount retrieved = retrievedOpt.get();
        assertThat(retrieved.getBankAccountId()).isEqualTo(bankAccountId);
        assertThat(retrieved.getCurrency()).isEqualTo("INR");
        assertThat(retrieved.getBalance()).isEqualTo(50000L);
        assertThat(retrieved.getHeld()).isEqualTo(1000L);
        assertThat(retrieved.getBankName()).isEqualTo("State Bank");
        assertThat(retrieved.getHolderName()).isEqualTo("John Doe");
        assertThat(retrieved.getUser().getId()).isEqualTo(user.getId());
        assertThat(retrieved.getUserId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Should query LinkedBankAccount using derived repository methods")
    void shouldQueryLinkedBankAccountByDerivedMethods() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User("query_" + uniqueSuffix, "query_" + uniqueSuffix + "@example.com", "hash123", Role.USER);
        user = userRepository.save(user);

        String bankAccountId = "BANK_QRY_" + uniqueSuffix;
        LinkedBankAccount account = new LinkedBankAccount(
                user,
                bankAccountId,
                "USD",
                "Chase Bank",
                "Jane Doe"
        );

        linkedBankAccountRepository.save(account);

        List<LinkedBankAccount> byUser = linkedBankAccountRepository.findByUser(user);
        assertThat(byUser).hasSize(1);
        assertThat(byUser.get(0).getBankAccountId()).isEqualTo(bankAccountId);

        List<LinkedBankAccount> byUserId = linkedBankAccountRepository.findByUserId(user.getId());
        assertThat(byUserId).hasSize(1);

        Optional<LinkedBankAccount> byBankAccountId = linkedBankAccountRepository.findByBankAccountId(bankAccountId);
        assertThat(byBankAccountId).isPresent();

        Optional<LinkedBankAccount> byUserAndBank = linkedBankAccountRepository.findByUserIdAndBankAccountId(user.getId(), bankAccountId);
        assertThat(byUserAndBank).isPresent();

        assertThat(linkedBankAccountRepository.existsByUserIdAndBankAccountId(user.getId(), bankAccountId)).isTrue();
        assertThat(linkedBankAccountRepository.existsByUserIdAndBankAccountId(user.getId(), "NON_EXISTENT")).isFalse();
    }
}

