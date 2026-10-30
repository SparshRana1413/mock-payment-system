package com.paymentsystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paymentsystem.model.User;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entity database operations.
 *
 * <p><strong>Architectural Role:</strong>
 * Data Access Layer (DAL). Provides an abstraction over PostgreSQL, encapsulating database
 * queries and persistence operations for the {@code users} table.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a Prisma model delegate (e.g., {@code prisma.user.findUnique(...)})
 * or a TypeORM {@code Repository<User>}. Unlike Node ORMs where queries are written manually
 * or via builder methods, Spring Data JPA automatically derives and executes SQL queries at runtime
 * simply by analyzing the repository method names ("derived query methods").</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Injected into {@link com.paymentsystem.service.AuthService} and {@link com.paymentsystem.service.LoginService}.
 * Executes queries against the PostgreSQL schema managed by Flyway.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Password verification and business rules belong in the service layer ({@code service/}).</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique email address.
     *
     * <p>Spring Data automatically generates:
     * {@code SELECT * FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1}.</p>
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the found {@link User}, or {@link Optional#empty()} if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their unique username.
     *
     * <p>Spring Data automatically generates:
     * {@code SELECT * FROM users WHERE username = ? LIMIT 1}.</p>
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the found {@link User}, or {@link Optional#empty()} if not found
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks if a user already exists with the given email address.
     *
     * <p>Spring Data automatically generates an optimized existence query:
     * {@code SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM users u WHERE u.email = ?}.</p>
     *
     * @param email the email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Checks if a user already exists with the given username.
     *
     * <p>Spring Data automatically generates an optimized existence query:
     * {@code SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM users u WHERE u.username = ?}.</p>
     *
     * @param username the username to check
     * @return true if a user with this username exists, false otherwise
     */
    boolean existsByUsername(String username);
}
