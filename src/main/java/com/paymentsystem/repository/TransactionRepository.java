package com.paymentsystem.repository;

import com.paymentsystem.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Transaction} entity persistence and retrieval.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Finds a transaction by its unique idempotency key.
     *
     * @param idempotencyKey the client-provided or generated unique idempotency key
     * @return an {@link Optional} containing the matched {@link Transaction}, or empty if not found
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Checks if a transaction with the given idempotency key exists.
     *
     * @param idempotencyKey the idempotency key to test
     * @return true if an entry exists, false otherwise
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Finds all transactions originating from a specific account.
     *
     * @param fromAccount the source bank account identifier
     * @return list of matching transactions
     */
    List<Transaction> findByFromAccount(String fromAccount);

    /**
     * Finds all transactions sent to a specific account.
     *
     * @param toAccount the destination bank account identifier
     * @return list of matching transactions
     */
    List<Transaction> findByToAccount(String toAccount);
}

