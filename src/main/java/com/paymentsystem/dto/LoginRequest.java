
package com.paymentsystem.dto;

/**
 * Inbound Request DTO capturing user login credentials.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation / DTO layer. Represents the HTTP JSON request payload received
 * by {@link com.paymentsystem.controller.LoginController}.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface for login requests:
 * {@code interface LoginRequest { email: string; password: string; }}.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Bound from HTTP request body by Jackson, passed to {@link com.paymentsystem.service.LoginService#login(LoginRequest)}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Verification of credentials against database records belongs in {@link com.paymentsystem.service.LoginService}.</p>
 *
 * @param email user's registered email address
 * @param password raw password submitted for authentication
 */
public record LoginRequest(
    String email,
    String password
) {}