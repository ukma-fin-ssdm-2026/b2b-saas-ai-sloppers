package ua.edu.ukma.community.auth;

import java.time.OffsetDateTime;

/**
 * Поточний код підтвердження пошти. Зберігається лише хеш коду (FR-002, FR-003).
 *
 * @param codeHash     bcrypt-хеш 6-значного коду
 * @param expiresAt    після цього моменту код недійсний
 * @param attemptsLeft скільки ще можна помилитися
 * @param sentAt       коли код надіслано (для обмеження повторних запитів)
 */
public record VerificationCode(String codeHash, OffsetDateTime expiresAt, int attemptsLeft, OffsetDateTime sentAt) {

    public boolean usable(OffsetDateTime now) {
        return attemptsLeft > 0 && now.isBefore(expiresAt);
    }

    public VerificationCode withFailedAttempt() {
        return new VerificationCode(codeHash, expiresAt, attemptsLeft - 1, sentAt);
    }

    @Override
    public String toString() {
        return "VerificationCode[expiresAt=" + expiresAt + ", attemptsLeft=" + attemptsLeft + "]";
    }
}
