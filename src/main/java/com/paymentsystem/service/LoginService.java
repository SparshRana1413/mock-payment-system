
package com.paymentsystem.service;

import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.UserRepository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service managing user credential authentication.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Business Logic layer. Responsible for locating users by email and verifying
 * plain-text password candidates against stored BCrypt hashes.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a service function like {@code export async function authenticate(email, password)}
 * that queries the database via an ORM and uses {@code await bcrypt.compare(password, user.passwordHash)}.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Invoked by {@link com.paymentsystem.controller.LoginController}. Interacts with
 * {@link UserRepository} to retrieve {@link User} records by email.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Generating JWT tokens belongs in {@link JwtService}; setting HTTP response cookies
 * belongs in {@link com.paymentsystem.controller.LoginController}.</p>
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Constructs the login service with its repository dependency.
     *
     * @param userRepository the JPA repository for user lookup
     */
    public LoginService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Validates user credentials against PostgreSQL records.
     *
     * <p>Normalizes email input, queries the database, and uses BCrypt to verify the password.
     * Uniformly throws {@link IllegalArgumentException} with "Invalid credentials" to prevent
     * account enumeration timing or enumeration attacks.</p>
     *
     * @param request the login request payload containing email and password
     * @return the authenticated {@link User} entity
     * @throws IllegalArgumentException if the user does not exist or the password does not match
     */
    public User login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        // Optional<User> is Java's type-safe wrapper for values that may be null (similar to User | null in TypeScript)
        User user = userRepository.findByEmail(email)
            .orElseThrow(() ->
                new IllegalArgumentException("Invalid credentials")
            );

        // BCrypt matches() extracts the salt embedded in the stored hash and hashes the candidate to compare
        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return user;
    }
}
