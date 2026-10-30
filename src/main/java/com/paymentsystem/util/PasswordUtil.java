package com.paymentsystem.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class for password hashing using the BCrypt strong hashing algorithm.
 *
 * <p><strong>Architectural Role:</strong>
 * Common utility layer. Encapsulates password hashing mechanics so calling services
 * don't need to manually configure salting or hashing rounds.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a helper function wrapping {@code bcrypt.hash()} or {@code bcryptjs.hash()}
 * in Node.js (e.g. {@code export const hashPassword = (pw: string) => bcrypt.hash(pw, 10)}).</p>
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private PasswordUtil() {
        // Prevent instantiation of utility class
    }

    /**
     * Hashes a plain-text password using BCrypt with an automatically generated salt.
     *
     * @param password the plain-text password to hash
     * @return the salted and hashed password string (safe for database storage)
     */
    public static String hashPassword(String password) {
        return encoder.encode(password);
    }

}
