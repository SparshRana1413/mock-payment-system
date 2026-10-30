package com.paymentsystem.config;

import com.paymentsystem.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that authenticates HTTP requests containing an {@code access_token} cookie.
 *
 * <p><strong>Architectural Role:</strong>
 * Security Filter layer. Intercepts incoming requests before reaching REST controllers,
 * extracts and validates JWT tokens from HTTP-only cookies, extracts role claims,
 * and populates Spring Security's {@link SecurityContextHolder} with an authenticated
 * {@link UsernamePasswordAuthenticationToken} containing granted authorities.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to an authentication middleware function in Express:
 * <pre>{@code
 * function authenticate(req, res, next) {
 *   const token = req.cookies['access_token'];
 *   if (token && jwtService.validateToken(token)) {
 *     req.user = {
 *       id: jwtService.extractUserId(token),
 *       roles: jwtService.extractRoles(token)
 *     };
 *   }
 *   next();
 * }
 * }</pre></p>
 *
 * <p><strong>Principal and Authority Representation:</strong>
 * The user ID string is stored as the principal. Roles extracted from the JWT (e.g. {@code ROLE_USER},
 * {@code ROLE_ADMIN}) are converted into Spring Security {@link SimpleGrantedAuthority} instances,
 * enabling endpoint security checks ({@code hasRole('ADMIN')}) and method-level annotations
 * ({@code @PreAuthorize}).</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    private final JwtService jwtService;

    /**
     * Constructs the filter with the required JWT service.
     *
     * @param jwtService service for cryptographic token validation and claim extraction
     */
    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromCookie(request);

        // Only establish authentication if token is valid and SecurityContext is not already authenticated
        if (token != null && jwtService.validateToken(token)) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String userId = jwtService.extractUserId(token);
                if (userId != null && !userId.isBlank()) {
                    List<String> roles = jwtService.extractRoles(token);
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    authorities
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        // Always proceed to the next filter; authorization rules will handle unauthenticated access
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT string from the {@code access_token} HTTP cookie.
     *
     * @param request the incoming HTTP servlet request
     * @return the token string, or {@code null} if the cookie is not present
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}