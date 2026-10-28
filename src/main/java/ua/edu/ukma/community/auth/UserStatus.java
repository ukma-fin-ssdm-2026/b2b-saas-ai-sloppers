package ua.edu.ukma.community.auth;

/**
 * Стан акаунта. {@code PENDING} — пошту ще не підтверджено, увійти не можна (FR-002).
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    BLOCKED
}
