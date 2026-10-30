package com.paymentsystem.controller;

import com.paymentsystem.dto.SignupRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller managing user registration endpoints.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation/Web layer. Receives HTTP requests, triggers payload validation via Jakarta Bean Validation,
 * delegates core user creation logic to the service layer, and shapes the resulting HTTP response.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to an Express router module (e.g. {@code const router = express.Router(); router.post('/signup', ...)})
 * with input validation middleware (such as Zod, Joi, or express-validator) executed before the handler.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Receives {@link SignupRequest} DTOs, coordinates with {@link AuthService} for business logic and persistence,
 * and produces {@link SignupResponse} payloads. Exceptions thrown during processing are handled by
 * {@link com.paymentsystem.exception.GlobalExceptionHandler}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Password hashing, database uniqueness checks, and JPA transactions belong in {@link AuthService}.
 * Login authentication and JWT issuance belong in {@link LoginController}.</p>
 */
@RestController // Combines @Controller and @ResponseBody; automatically serializes return values to JSON via Jackson
@RequestMapping("/api/auth") // Base route prefix, analogous to app.use('/api/auth', router) in Express
public class AuthController {

    private final AuthService authService;

    /**
     * Constructs the controller with required service dependencies.
     *
     * <p>Spring automatically performs constructor-based Dependency Injection (DI),
     * injecting the singleton {@link AuthService} bean from the ApplicationContext.</p>
     *
     * @param authService the service managing registration business logic
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Response payload representing a sanitized, newly registered user.
     *
     * <p>A Java Record is an immutable data carrier that auto-generates constructor,
     * getters (accessor methods like {@code id()}), {@code equals}, {@code hashCode}, and {@code toString}.
     * Analogous to a TypeScript immutable interface/type shape.</p>
     *
     * @param id the unique primary key of the created user
     * @param username the chosen username
     * @param email the user's registered email address
     */
    public record SignupResponse(
        Long id,
        String username,
        String email
    ) {}

    /**
     * Handles user registration requests.
     *
     * <p>Validates incoming JSON against {@link SignupRequest} constraints, creates the user,
     * and returns an HTTP 201 Created status.</p>
     *
     * @param request the validated registration payload ({@code @Valid} activates Jakarta validation)
     * @return a {@link ResponseEntity} containing the created user details (excluding sensitive credentials)
     */
    @PostMapping("/signup") // Maps HTTP POST /api/auth/signup, like router.post('/signup', ...) in Express
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request) {

        // Delegate persistence, duplicate checking, and hashing to the service layer
        User user = authService.signup(request);

        // Map internal entity to a public response record to prevent exposing sensitive fields like passwordHash
        SignupResponse response = new SignupResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail()
        );

        // Return HTTP 201 Created with JSON body
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
