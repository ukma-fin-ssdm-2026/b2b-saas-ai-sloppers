package ua.edu.ukma.community.auth;

import java.time.OffsetDateTime;


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
