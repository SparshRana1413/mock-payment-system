
package com.paymentsystem.service;

import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.UserRepository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public LoginService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
            .orElseThrow(() ->
                new IllegalArgumentException("Invalid credentials")
            );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return user;
    }
}
