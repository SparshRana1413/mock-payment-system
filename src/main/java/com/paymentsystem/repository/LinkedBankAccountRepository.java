package com.paymentsystem.repository;

import com.paymentsystem.model.LinkedBankAccount;
import com.paymentsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LinkedBankAccount} entity persistence and retrieval.
 */
@Repository
public interface LinkedBankAccountRepository extends JpaRepository<LinkedBankAccount, Long> {

    /**
     * Finds all bank accounts linked to a specific user entity.
     *
     * @param user the owning user
     * @return list of linked bank accounts
     */
    List<LinkedBankAccount> findByUser(User user);

    /**
     * Finds all bank accounts linked to a user by traversing the User relation ID.
     *
     * @param userId the owning user's ID
     * @return list of linked bank accounts
     */
    List<LinkedBankAccount> findByUser_Id(Long userId);

    /**
     * Finds all bank accounts linked to a user by user ID.
     *
     * @param userId the owning user's ID
     * @return list of linked bank accounts
     */
    @Query("SELECT l FROM LinkedBankAccount l WHERE l.user.id = :userId")
    List<LinkedBankAccount> findByUserId(@Param("userId") Long userId);

    /**
     * Finds a linked bank account by external bank account identifier.
     *
     * @param bankAccountId external bank account identifier
     * @return an {@link Optional} containing the matched {@link LinkedBankAccount}, or empty if not found
     */
    Optional<LinkedBankAccount> findByBankAccountId(String bankAccountId);

    /**
     * Finds a linked bank account by user and external bank account identifier.
     *
     * @param user the owning user
     * @param bankAccountId external bank account identifier
     * @return an {@link Optional} containing the matched {@link LinkedBankAccount}, or empty if not found
     */
    Optional<LinkedBankAccount> findByUserAndBankAccountId(User user, String bankAccountId);

    /**
     * Finds a linked bank account by user ID and external bank account identifier via relation traversal.
     *
     * @param userId the user ID
     * @param bankAccountId external bank account identifier
     * @return an {@link Optional} containing the matched {@link LinkedBankAccount}, or empty if not found
     */
    Optional<LinkedBankAccount> findByUser_IdAndBankAccountId(Long userId, String bankAccountId);

    /**
     * Finds a linked bank account by user ID and external bank account identifier.
     *
     * @param userId the user ID
     * @param bankAccountId external bank account identifier
     * @return an {@link Optional} containing the matched {@link LinkedBankAccount}, or empty if not found
     */
    @Query("SELECT l FROM LinkedBankAccount l WHERE l.user.id = :userId AND l.bankAccountId = :bankAccountId")
    Optional<LinkedBankAccount> findByUserIdAndBankAccountId(@Param("userId") Long userId, @Param("bankAccountId") String bankAccountId);

    /**
     * Checks if an account linkage already exists for a user and external bank account.
     *
     * @param user the user
     * @param bankAccountId external bank account identifier
     * @return true if an entry exists, false otherwise
     */
    boolean existsByUserAndBankAccountId(User user, String bankAccountId);

    /**
     * Checks if an account linkage already exists for a user ID and external bank account.
     *
     * @param userId the user ID
     * @param bankAccountId external bank account identifier
     * @return true if an entry exists, false otherwise
     */
    boolean existsByUser_IdAndBankAccountId(Long userId, String bankAccountId);

    /**
     * Checks if an account linkage already exists for a user ID and external bank account.
     *
     * @param userId the user ID
     * @param bankAccountId external bank account identifier
     * @return true if an entry exists, false otherwise
     */
    @Query("SELECT COUNT(l) > 0 FROM LinkedBankAccount l WHERE l.user.id = :userId AND l.bankAccountId = :bankAccountId")
    boolean existsByUserIdAndBankAccountId(@Param("userId") Long userId, @Param("bankAccountId") String bankAccountId);
}