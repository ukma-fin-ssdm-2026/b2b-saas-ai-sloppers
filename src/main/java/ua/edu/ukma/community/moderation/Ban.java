package ua.edu.ukma.community.moderation;

import ua.edu.ukma.community.auth.UserStatus;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Блокування користувача (FR-054), таблиця {@code ban}. Рядок не видаляється: розблокування лише
 * заповнює {@code liftedAt}, тож історія покарань лишається (журнал проти заперечення авторства, T-06).
 *
 * <p>{@code bannedBy == null} — заблокувала система (FR-060), {@code expiresAt == null} — безстроково.
 * {@code previousStatus} потрібен, щоб після розблокування повернути користувача саме туди, де він був:
 * непідтверджений акаунт має лишитися непідтвердженим.
 */
public record Ban(
        UUID id,
        UUID userId,
        UUID bannedBy,
        String reason,
        OffsetDateTime expiresAt,
        UserStatus previousStatus,
        OffsetDateTime createdAt,
        OffsetDateTime liftedAt,
        UUID liftedBy
) {

    public Ban {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(previousStatus, "previousStatus");
        Objects.requireNonNull(createdAt, "createdAt");
        if (previousStatus == UserStatus.BLOCKED) {
            throw new IllegalArgumentException("previousStatus must not be BLOCKED");
        }
    }

    public static Ban create(UUID userId, UUID bannedBy, String reason, OffsetDateTime expiresAt,
                             UserStatus previousStatus, OffsetDateTime now) {
        return new Ban(UUID.randomUUID(), userId, bannedBy, reason, expiresAt, previousStatus, now, null, null);
    }

    /** Заблокувала система за накопиченими скаргами або відмовами автофільтра (FR-060). */
    public boolean isAutomatic() {
        return bannedBy == null;
    }

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isLifted() {
        return liftedAt != null;
    }

    public boolean hasExpired(OffsetDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Чи обмежує цей бан користувача зараз: не знятий і строк не вийшов. */
    public boolean isActive(OffsetDateTime now) {
        return !isLifted() && !hasExpired(now);
    }

    /**
     * Знімає блокування.
     *
     * @param adminId хто зняв, або {@code null}, якщо строк вийшов і бан знімає планувальник
     */
    public Ban lift(UUID adminId, OffsetDateTime at) {
        if (isLifted()) {
            throw new IllegalStateException("ban " + id + " is already lifted");
        }
        return new Ban(id, userId, bannedBy, reason, expiresAt, previousStatus, createdAt, at, adminId);
    }
}
