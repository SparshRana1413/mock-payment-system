package com.paymentsystem.service;

import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructs the login service with its repository and password encoder dependencies.
     *
     * @param userRepository the JPA repository for user lookup
     * @param passwordEncoder the Spring-managed password encoder bean
     */
    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Validates user credentials against PostgreSQL records.
     *
     * <p>Normalizes email input, queries the database, and uses the configured {@link PasswordEncoder}
     * to verify the password. Uniformly throws {@link BadCredentialsException} with "Invalid credentials"
     * for unknown email, null inputs, or password mismatches to prevent account enumeration attacks.</p>
     *
     * @param request the login request payload containing email and password
     * @return the authenticated {@link User} entity
     * @throws BadCredentialsException if the user does not exist or the password does not match
     */
    public User login(LoginRequest request) {
        if (request == null || request.email() == null || request.password() == null) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String email = request.email().trim().toLowerCase();
        if (email.isEmpty() || request.password().isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return user;
    }
}
