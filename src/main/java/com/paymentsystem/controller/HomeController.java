package com.paymentsystem.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root controller providing a simple health check or landing endpoint.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation/Web layer. Serves as a basic root endpoint to verify that the web server
 * is running and responding to HTTP requests.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a root test route in Express: {@code app.get('/', (req, res) => res.send('<h1>Hello World!</h1>'))}.</p>
 */
@RestController 
public class HomeController {

    /**
     * Handles GET requests to the application root path.
     *
     * @return a simple HTML string greeting
     */
    @GetMapping("/")
    public String home() {
        return "<h1>Hello World!</h1>";
    }
}