package com.paymentsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity representing an application user in the PostgreSQL database.
 *
 * <p><strong>Architectural Role:</strong>
 * Domain Model / Persistence Entity layer. Maps directly to the {@code users} table
 * created by Flyway migrations {@code V1__create_users_table.sql} and {@code V3__add_role_to_users_table.sql}.
 * Represents the user's identity, credential state, and authorization role.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeORM entity class (with {@code @Entity()}, {@code @Column()}) or a
 * Prisma model definition ({@code model User { id BigInt @id role Role @default(USER) ... }}).
 * Hibernate maps instances of this class to relational rows in PostgreSQL.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Managed by {@link com.paymentsystem.repository.UserRepository} and manipulated by
 * {@link com.paymentsystem.service.AuthService} and {@link com.paymentsystem.service.LoginService}.</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Default no-argument constructor required by JPA/Hibernate proxy instantiation.
     */
    protected User() {}

    /**
     * Constructs a new User instance for standard registration, defaulting to {@link Role#USER}.
     *
     * @param username the chosen unique username
     * @param email the user's unique email address
     * @param passwordHash the BCrypt-hashed password (must not be raw text)
     */
    public User(String username, String email, String passwordHash) {
        this(username, email, passwordHash, Role.USER);
    }

    /**
     * Constructs a User instance with an explicit authorization role.
     *
     * @param username the chosen unique username
     * @param email the user's unique email address
     * @param passwordHash the BCrypt-hashed password
     * @param role the user's security role (defaults to USER if null)
     */
    public User(String username, String email, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = (role != null ? role : Role.USER);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (role == null) {
            role = Role.USER;
        }
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
