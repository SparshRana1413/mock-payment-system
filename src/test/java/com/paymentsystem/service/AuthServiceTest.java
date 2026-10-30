package com.paymentsystem.service;

import com.paymentsystem.dto.SignupRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying registration business logic, input normalization,
 * uniqueness validation, and password encoding in {@link AuthService}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should successfully register a new user and encode their password")
    void testSignupSuccess() {
        SignupRequest request = new SignupRequest("alice", "alice@example.com", "Password123!");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$hashedPasswordValue");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.signup(request);

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals("$2a$10$hashedPasswordValue", result.getPasswordHash());
        assertEquals(com.paymentsystem.model.Role.USER, result.getRole());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("alice", savedUser.getUsername());
        assertEquals("alice@example.com", savedUser.getEmail());
        assertEquals("$2a$10$hashedPasswordValue", savedUser.getPasswordHash());
        assertNotEquals("Password123!", savedUser.getPasswordHash());
    }

    @Test
    @DisplayName("Should reject signup with duplicate username")
    void testSignupDuplicateUsername() {
        SignupRequest request = new SignupRequest("duplicateUser", "user@example.com", "Password123!");
        when(userRepository.existsByUsername("duplicateUser")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            authService.signup(request)
        );

        assertEquals("Username already exists", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject signup with duplicate email")
    void testSignupDuplicateEmail() {
        SignupRequest request = new SignupRequest("newUser", "taken@example.com", "Password123!");
        when(userRepository.existsByUsername("newUser")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            authService.signup(request)
        );

        assertEquals("Email already exists", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should normalize email to lowercase and trim whitespace from username and email")
    void testSignupNormalizesInputs() {
        SignupRequest request = new SignupRequest("  bob  ", "  BOB@Example.COM  ", "Password123!");

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedBob");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.signup(request);

        assertEquals("bob", result.getUsername());
        assertEquals("bob@example.com", result.getEmail());
        verify(userRepository).existsByUsername("bob");
        verify(userRepository).existsByEmail("bob@example.com");
    }

    @Test
    @DisplayName("Should reject signup with null or empty fields")
    void testSignupInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> authService.signup(null));
        assertThrows(IllegalArgumentException.class, () -> authService.signup(new SignupRequest(null, "a@b.com", "pass12345")));
        assertThrows(IllegalArgumentException.class, () -> authService.signup(new SignupRequest("user", null, "pass12345")));
        assertThrows(IllegalArgumentException.class, () -> authService.signup(new SignupRequest("user", "a@b.com", null)));
        assertThrows(IllegalArgumentException.class, () -> authService.signup(new SignupRequest("   ", "a@b.com", "pass12345")));
        assertThrows(IllegalArgumentException.class, () -> authService.signup(new SignupRequest("user", "   ", "pass12345")));
    }
}

