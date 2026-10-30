
package com.paymentsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web security configuration that defines endpoint access rules and session policy.
 *
 * <p><strong>Architectural Role:</strong>
 * Infrastructure/Configuration layer. Configures the Spring Security filter pipeline
 * that intercepts incoming HTTP requests before they reach any controller.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a chain of Express middleware (e.g., Passport.js, custom auth guards,
 * or {@code router.use()}) applied globally at the application level to enforce authentication
 * and authorization rules before route handlers execute.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Interacts with Spring Security's internal filter chain. Protects endpoints managed by
 * {@link com.paymentsystem.controller.AuthController}, {@link com.paymentsystem.controller.LoginController},
 * and {@link com.paymentsystem.controller.HomeController}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Token generation belongs in {@link com.paymentsystem.service.JwtService};
 * credential validation belongs in {@link com.paymentsystem.service.LoginService}.</p>
 */
@Configuration // Marks this class as a source of bean definitions for the Spring IoC container
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain.
     *
     * <p>Configures a stateless security architecture where public authentication
     * endpoints (signup, login) are permitted without credentials, while all other
     * routes require authentication.</p>
     *
     * @param http the {@link HttpSecurity} builder used to assemble the security pipeline
     * @return the built {@link SecurityFilterChain} bean
     * @throws Exception if an error occurs configuring web security
     */
    @Bean // Tells Spring that the returned SecurityFilterChain should be registered as a singleton bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            // Disabled because this is a stateless REST API; CSRF protection is primarily for stateful session cookies
            .csrf(csrf -> csrf.disable())
            // Disable default HTTP Basic authentication prompt (401 popup in browsers)
            .httpBasic(basic -> basic.disable())
            // Disable default HTML login form redirect
            .formLogin(form -> form.disable())
            // STATELESS means Spring will not create an HttpSession (no JSESSIONID cookie); every request must be self-authenticating
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            // Authorization rules: allow public POST to /api/auth/*; protect everything else
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/signup",
                    "/api/auth/login"
                ).permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
