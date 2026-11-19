package ua.edu.ukma.community.auth;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;


public record User(
        UUID id,
        String email,
        String passwordHash,
        UserStatus status,
        boolean globalAdmin,
        VerificationCode verification,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public User withVerification(VerificationCode code, OffsetDateTime now) {
        return new User(id, email, passwordHash, status, globalAdmin, code, createdAt, now);
    }

    public User activated(OffsetDateTime now) {
        return new User(id, email, passwordHash, UserStatus.ACTIVE, globalAdmin, null, createdAt, now);
    }

    /**
     * Новий пароль після скидання (FR-065). Код скидання доводить володіння поштою, тож непідтверджений
     * акаунт заодно стає активним; заблокований сюди не доходить.
     */
    public User withNewPassword(String newPasswordHash, OffsetDateTime now) {
        UserStatus newStatus = status == UserStatus.PENDING ? UserStatus.ACTIVE : status;
        return new User(id, email, newPasswordHash, newStatus, globalAdmin, null, createdAt, now);
    }

    /** Без хеша пароля: запис може потрапити в лог (NFR-008). */
    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", status=" + status + ", globalAdmin=" + globalAdmin + "]";
    }
}
