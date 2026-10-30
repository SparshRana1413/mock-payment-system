package com.paymentsystem.service;

import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying credential authentication, password matching,
 * email normalization, and anti-enumeration error handling in {@link LoginService}.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should successfully authenticate a user with correct credentials")
    void testLoginSuccess() {
        User user = new User("charlie", "charlie@example.com", "$2a$10$hashedCharliePassword");
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedCharliePassword")).thenReturn(true);

        LoginRequest request = new LoginRequest("charlie@example.com", "ValidPassword123!");
        User authenticatedUser = loginService.login(request);

        assertNotNull(authenticatedUser);
        assertEquals("charlie", authenticatedUser.getUsername());
        assertEquals("charlie@example.com", authenticatedUser.getEmail());
    }

    @Test
    @DisplayName("Should reject login when email does not exist with generic BadCredentialsException")
    void testLoginUnknownEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknown@example.com", "SomePassword123!");
        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
            loginService.login(request)
        );

        assertEquals("Invalid credentials", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject login when password does not match with generic BadCredentialsException")
    void testLoginIncorrectPassword() {
        User user = new User("charlie", "charlie@example.com", "$2a$10$hashedCharliePassword");
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "$2a$10$hashedCharliePassword")).thenReturn(false);

        LoginRequest request = new LoginRequest("charlie@example.com", "WrongPassword!");
        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
            loginService.login(request)
        );

        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    @DisplayName("Should normalize email by trimming and lowercasing before database lookup")
    void testLoginNormalizesEmail() {
        User user = new User("charlie", "charlie@example.com", "$2a$10$hashedCharliePassword");
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ValidPassword123!", "$2a$10$hashedCharliePassword")).thenReturn(true);

        LoginRequest request = new LoginRequest("  CHARLIE@example.COM  ", "ValidPassword123!");
        User result = loginService.login(request);

        assertNotNull(result);
        verify(userRepository).findByEmail("charlie@example.com");
    }

    @Test
    @DisplayName("Should reject null or empty login credentials with generic BadCredentialsException")
    void testLoginInvalidInputs() {
        assertThrows(BadCredentialsException.class, () -> loginService.login(null));
        assertThrows(BadCredentialsException.class, () -> loginService.login(new LoginRequest(null, "pass")));
        assertThrows(BadCredentialsException.class, () -> loginService.login(new LoginRequest("a@b.com", null)));
        assertThrows(BadCredentialsException.class, () -> loginService.login(new LoginRequest("   ", "pass")));
        assertThrows(BadCredentialsException.class, () -> loginService.login(new LoginRequest("a@b.com", "")));
    }
}

