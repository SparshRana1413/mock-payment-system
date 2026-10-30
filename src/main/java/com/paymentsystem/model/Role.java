package com.paymentsystem.model;

/**
 * Enumeration representing user security authorization roles.
 *
 * <p>Maps to the Spring Security authority convention:
 * <ul>
 *   <li>{@link #USER} maps to {@code ROLE_USER}</li>
 *   <li>{@link #ADMIN} maps to {@code ROLE_ADMIN}</li>
 * </ul>
 * </p>
 */
public enum Role {
    USER,
    ADMIN;

    public static final String PREFIX = "ROLE_";

    /**
     * Returns the role name formatted with Spring Security's standard authority prefix.
     *
     * @return the authority string (e.g. {@code "ROLE_USER"})
     */
    public String toAuthority() {
        return PREFIX + name();
    }
}

