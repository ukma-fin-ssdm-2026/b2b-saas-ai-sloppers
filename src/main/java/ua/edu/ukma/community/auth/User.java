package ua.edu.ukma.community.auth;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Користувач. {@code firstName} і {@code lastName} необов'язкові (FR-069): у акаунтів, створених до V7,
 * їх немає, доки користувач не задасть їх сам.
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        UserStatus status,
        boolean globalAdmin,
        VerificationCode verification,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String firstName,
        String lastName
) {

    /** Користувач без імені — для коду й тестів, яким ім'я не потрібне. */
    public User(UUID id, String email, String passwordHash, UserStatus status, boolean globalAdmin,
                VerificationCode verification, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, email, passwordHash, status, globalAdmin, verification, createdAt, updatedAt, null, null);
    }

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
        return new User(id, email, passwordHash, status, globalAdmin, code, createdAt, now, firstName, lastName);
    }

    public User activated(OffsetDateTime now) {
        return new User(id, email, passwordHash, UserStatus.ACTIVE, globalAdmin, null, createdAt, now,
                firstName, lastName);
    }

    /**
     * Новий пароль після скидання (FR-065). Код скидання доводить володіння поштою, тож непідтверджений
     * акаунт заодно стає активним; заблокований сюди не доходить.
     */
    public User withNewPassword(String newPasswordHash, OffsetDateTime now) {
        UserStatus newStatus = status == UserStatus.PENDING ? UserStatus.ACTIVE : status;
        return new User(id, email, newPasswordHash, newStatus, globalAdmin, null, createdAt, now,
                firstName, lastName);
    }

    public User withName(String newFirstName, String newLastName, OffsetDateTime now) {
        return new User(id, email, passwordHash, status, globalAdmin, verification, createdAt, now,
                newFirstName, newLastName);
    }

    /** «Ім'я Прізвище», те, що бачать інші користувачі; {@code null}, якщо ім'я ще не задано. */
    public String fullName() {
        return Names.full(firstName, lastName);
    }

    public User withGlobalAdmin(boolean admin, OffsetDateTime now) {
        return new User(id, email, passwordHash, status, admin, verification, createdAt, now);
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", status=" + status + ", globalAdmin=" + globalAdmin + "]";
    }
}
