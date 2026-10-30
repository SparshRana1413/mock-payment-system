package com.paymentsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point for the Spring Boot Payment System.
 *
 * <p><strong>Architectural Role:</strong>
 * Acts as the bootstrap root for the entire backend application. When executed,
 * it bootstraps the Spring ApplicationContext, initializes the embedded web server
 * (Tomcat), discovers annotated components across the package hierarchy, and configures
 * database connections and security filters.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to the root {@code index.ts} or {@code server.ts} in an Express project
 * where you initialize {@code express()}, load environment variables (e.g. {@code dotenv}),
 * register global middleware, and invoke {@code app.listen(port)}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Route definitions belong in {@code controller/}, business rules in {@code service/},
 * and security policy definitions in {@code config/SecurityConfig}.</p>
 */
@SpringBootApplication // Combines @Configuration, @EnableAutoConfiguration, and @ComponentScan (auto-discovers beans in this package and sub-packages)
public class PaymentSystemApplication {

	/**
	 * JVM execution entry point that starts the Spring Boot runtime.
	 *
	 * @param args command-line arguments passed at process launch
	 */
	public static void main(String[] args) {
		// Boots the Spring container, performs classpath scanning, and starts embedded Tomcat
		SpringApplication.run(PaymentSystemApplication.class, args);
	}

}
