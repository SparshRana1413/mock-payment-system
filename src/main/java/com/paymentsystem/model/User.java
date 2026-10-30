package com.paymentsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity representing an application user in the PostgreSQL database.
 *
 * <p><strong>Architectural Role:</strong>
 * Domain Model / Persistence Entity layer. Maps directly to the {@code users} table
 * created by Flyway migration {@code V1__create_users_table.sql}. Represents the user's
 * identity and credential state.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeORM entity class (with {@code @Entity()}, {@code @Column()}) or a
 * Prisma model definition ({@code model User { id BigInt @id ... }}). Hibernate maps instances
 * of this class to relational rows in PostgreSQL.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Managed by {@link com.paymentsystem.repository.UserRepository} and manipulated by
 * {@link com.paymentsystem.service.AuthService} and {@link com.paymentsystem.service.LoginService}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * HTTP serialization is mediated by DTOs/records (e.g., {@code SignupResponse}) rather than
 * exposing this entity directly, protecting sensitive attributes like {@code passwordHash}.</p>
 */
@Entity // Tells Hibernate/JPA to manage instances of this class as database rows
@Table(name = "users") // Specifies the target PostgreSQL table name
public class User {

    @Id // Denotes the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Relies on PostgreSQL's GENERATED ALWAYS AS IDENTITY
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Default no-argument constructor required by JPA/Hibernate proxy instantiation.
     */
    protected User() {}

    /**
     * Constructs a new User instance for registration.
     *
     * @param username the chosen unique username
     * @param email the user's unique email address
     * @param passwordHash the BCrypt-hashed password (must not be raw text)
     */
    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /**
     * JPA entity lifecycle callback executed before the entity is first persisted.
     *
     * <p>Analogous to TypeORM's {@code @BeforeInsert()} or Mongoose pre-save middleware.</p>
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
