package com.paymentsystem.service;

import com.paymentsystem.dto.SignupRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.UserRepository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user registration and credential persistence.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Business Logic layer. Orchestrates registration rules, validates uniqueness
 * against PostgreSQL via {@link UserRepository}, hashes passwords, and saves new {@link User} entities.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to an {@code authService.ts} module containing business functions like
 * {@code export async function signup(dto: SignupDto)}. Unlike Node where database transactions
 * must be manually opened via {@code prisma.$transaction(async (tx) => ...)}, Spring provides
 * declarative transactions via the {@link Transactional} annotation.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Called by {@link com.paymentsystem.controller.AuthController}. Interacts with {@link UserRepository}
 * to check existence and persist users. Uses {@link BCryptPasswordEncoder} to hash raw passwords.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * HTTP response formatting belongs in {@link com.paymentsystem.controller.AuthController};
 * JWT issuance belongs in {@link JwtService}; login verification belongs in {@link LoginService}.</p>
 */
@Service // Registers this class as a Spring-managed singleton bean in the service layer
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Constructs the auth service with its repository dependency.
     *
     * @param userRepository the JPA repository for user database operations
     */
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Registers a new user in the system after validating uniqueness and hashing the password.
     *
     * <p><strong>Transaction Management:</strong>
     * Annotated with {@link Transactional}. In Express/TypeORM/Prisma, this is like wrapping code in
     * a database transaction block. Spring automatically commits on successful completion,
     * or rolls back if an unhandled runtime exception is thrown.</p>
     *
     * @param request the validated registration payload containing username, email, and raw password
     * @return the saved {@link User} entity including its generated database ID
     * @throws IllegalArgumentException if the username or email is already taken
     */
    @Transactional
    public User signup(SignupRequest request) {

        // Normalize input values to prevent duplicate entries caused by casing or whitespace
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        // Enforce business uniqueness constraints before attempting to insert
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Salt and hash the password using BCrypt before persisting
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(username, email, passwordHash);

        // Saves entity to PostgreSQL via JPA (Hibernate generates the INSERT statement)
        return userRepository.save(user);
    }
}
