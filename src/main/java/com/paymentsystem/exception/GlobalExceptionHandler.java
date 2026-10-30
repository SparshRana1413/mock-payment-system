package com.paymentsystem.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
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
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles bad credentials during authentication, returning an HTTP 401 Unauthorized status.
     *
     * @param ex the intercepted {@link BadCredentialsException}
     * @return structured error response with status 401
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.UNAUTHORIZED.value());
        errorBody.put("error", "Unauthorized");
        errorBody.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody);
    }

    /**
     * Handles authorization and access denial failures (e.g. non-admin accessing admin endpoint),
     * returning an HTTP 403 Forbidden status.
     *
     * @param ex the intercepted {@link AccessDeniedException}
     * @return structured error response with status 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.FORBIDDEN.value());
        errorBody.put("error", "Forbidden");
        errorBody.put("message", "Access denied: insufficient privileges");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody);
    }

    /**
     * Handles database constraint violations (e.g. concurrent duplicate registrations),
     * returning an HTTP 409 Conflict status.
     *
     * @param ex the intercepted {@link DataIntegrityViolationException}
     * @return structured error response with status 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.CONFLICT.value());
        errorBody.put("error", "Conflict");
        errorBody.put("message", "User with this username or email already exists");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody);
    }

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
        errorBody.put("message", "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }
}
