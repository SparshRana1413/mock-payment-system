package com.paymentsystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only REST controller exposing protected and admin-only endpoints to verify
 * Spring Security filter chains, role-based authorization, and method-level security.
 */
@RestController
@RequestMapping("/api/test")
public class TestProtectedController {

    /**
     * Protected endpoint requiring any authenticated user.
     */
    @GetMapping("/protected")
    public ResponseEntity<Map<String, Object>> getProtectedResource(Authentication authentication) {
        return ResponseEntity.ok(
            Map.of(
                "status", "success",
                "message", "Access granted to protected resource",
                "userId", authentication != null ? authentication.getPrincipal() : "none"
            )
        );
    }

    /**
     * Admin-only endpoint protected by method-level authorization.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminResource(Authentication authentication) {
        return ResponseEntity.ok(
            Map.of(
                "status", "success",
                "message", "Access granted to admin resource",
                "userId", authentication != null ? authentication.getPrincipal() : "none"
            )
        );
    }
}
