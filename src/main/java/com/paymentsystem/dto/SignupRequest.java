package com.paymentsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound Request DTO capturing user registration details.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation / DTO layer. Defines the API request contract for user registration,
 * enforcing input integrity at the application boundary via Jakarta Bean Validation.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface combined with a Zod or Joi schema:
 * <pre>{@code
 * const SignupSchema = z.object({
 *   username: z.string().min(1).max(50),
 *   email: z.string().email().max(254),
 *   password: z.string().min(8).max(72)
 * });
 * }</pre></p>
 *
 * <p><strong>Component Interactions:</strong>
 * Passed into {@link com.paymentsystem.controller.AuthController#signup(SignupRequest)}.
 * Validated by Spring's validator before the controller method executes.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Uniqueness verification and password hashing belong in {@link com.paymentsystem.service.AuthService}.</p>
 *
 * @param username desired username (mandatory, up to 50 characters)
 * @param email valid user email address (mandatory, RFC compliant, up to 254 characters)
 * @param password raw user password (mandatory, 8-72 characters; 72 is the BCrypt max length limit)
 */
public record SignupRequest(

    @NotBlank
    @Size(max = 50)
    String username,

    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @NotBlank
    @Size(min = 8, max = 72)
    String password

) {}
