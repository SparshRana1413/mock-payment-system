package com.paymentsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound Request DTO capturing user login credentials.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation / DTO layer. Represents the HTTP JSON request payload received
 * by {@link com.paymentsystem.controller.LoginController}.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface combined with Zod/Joi validation for login requests:
 * {@code z.object({ email: z.string().email(), password: z.string().min(1) })}.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Bound from HTTP request body by Jackson and validated via {@code @Valid} before being passed
 * to {@link com.paymentsystem.service.LoginService#login(LoginRequest)}.</p>
 *
 * @param email user's registered email address (mandatory, valid email format)
 * @param password raw password submitted for authentication (mandatory)
 */
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}
