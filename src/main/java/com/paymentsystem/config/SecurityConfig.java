package com.paymentsystem.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

/**
 * Web security configuration defining HTTP filter pipelines, endpoint authorization,
 * role-based access control, session policies, and password hashing beans.
 *
 * <p><strong>Architectural Role:</strong>
 * Infrastructure / Configuration layer. Configures Spring Security to operate statelessly
 * with JWT authentication delivered via HTTP-only cookies, method-level security, and role-based
 * access controls (RBAC).</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Activates @PreAuthorize, @PostAuthorize, and @Secured method annotations
public class SecurityConfig {

    /**
     * Shared Spring-managed BCrypt password encoder bean.
     *
     * @return a singleton {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Prevents Spring Boot from registering {@link JwtAuthenticationFilter} in the root servlet container.
     *
     * @param filter the JWT authentication filter bean
     * @return a {@link FilterRegistrationBean} with registration disabled
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Custom entry point that returns HTTP 401 Unauthorized for unauthenticated requests.
     *
     * @return the {@link AuthenticationEntryPoint} implementation
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"timestamp":"%s","status":401,"error":"Unauthorized","message":"Full authentication is required to access this resource"}
                """.formatted(Instant.now().toString()).trim());
        };
    }

    /**
     * Custom access denied handler that returns HTTP 403 Forbidden for authenticated users with insufficient privileges.
     *
     * @return the {@link AccessDeniedHandler} implementation
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"timestamp":"%s","status":403,"error":"Forbidden","message":"Access denied: insufficient privileges"}
                """.formatted(Instant.now().toString()).trim());
        };
    }

    /**
     * Configures the HTTP security filter chain.
     *
     * @param http the security builder
     * @param jwtAuthenticationFilter the custom JWT filter
     * @param authenticationEntryPoint the 401 entry point
     * @param accessDeniedHandler the 403 access denied handler
     * @return the assembled {@link SecurityFilterChain} bean
     * @throws Exception if an error occurs configuring web security
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        http
            .csrf(csrf -> csrf.disable()) // See architectural note on CSRF & SameSite cookies
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
