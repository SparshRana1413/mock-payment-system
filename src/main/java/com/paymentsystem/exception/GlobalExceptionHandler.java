package com.paymentsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that catches unhandled exceptions thrown across all REST controllers
 * and transforms them into standardized JSON error responses.
 *
 * <p><strong>Architectural Role:</strong>
 * Cross-cutting error handling layer. Intercepts exceptions before they escape to the servlet
 * container, preventing raw stack traces or internal server details from leaking to API clients.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Directly analogous to Express's 4-argument error-handling middleware function:
 * {@code app.use((err, req, res, next) => { res.status(err.status || 500).json(...) })}.
 * In Spring, {@link RestControllerAdvice} registers this globally, and {@link ExceptionHandler}
 * routes specific exception types to targeted handler methods.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Catches exceptions thrown by controllers, service layers (e.g., {@link IllegalArgumentException}
 * from {@link com.paymentsystem.service.AuthService} or {@link com.paymentsystem.service.LoginService}),
 * and the Jakarta Validation framework ({@link MethodArgumentNotValidException}).</p>
 */
@RestControllerAdvice // Combines @ControllerAdvice and @ResponseBody to automatically serialize handler return values into HTTP response bodies
public class GlobalExceptionHandler {

    /**
     * Handles business rule violations and illegal argument states (e.g., duplicate username or email).
     *
     * <p>Maps application-level {@link IllegalArgumentException} to an HTTP 400 Bad Request.</p>
     *
     * @param ex the intercepted {@link IllegalArgumentException}
     * @return a {@link ResponseEntity} containing a structured error response with timestamp, status, and message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("error", "Bad Request");
        errorBody.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    /**
     * Handles DTO validation failures triggered by {@code @Valid} annotations (e.g., {@code @NotBlank}, {@code @Email}).
     *
     * <p>Extracts field-level validation error messages produced by Hibernate Validator and maps them
     * to an HTTP 400 Bad Request with a map of offending field names and violation messages.</p>
     *
     * @param ex the intercepted {@link MethodArgumentNotValidException} containing binding and validation results
     * @return a {@link ResponseEntity} containing a structured error response with field-level failure details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Collect field-specific error messages (analogous to parsing Joi or Zod validation errors)
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("error", "Validation Failed");
        errorBody.put("messages", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    /**
     * Fallback safety-net handler for any uncaught or unexpected runtime exceptions.
     *
     * <p>Returns an HTTP 500 Internal Server Error with a generic message to prevent leaking internal
     * architecture, class names, or database details to the client.</p>
     *
     * @param ex the uncaught {@link Exception}
     * @return a {@link ResponseEntity} with an HTTP 500 status and sanitized error payload
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorBody.put("error", "Internal Server Error");
        // Keeps internal server details hidden for security (defense in depth)
        errorBody.put("message", "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }
}