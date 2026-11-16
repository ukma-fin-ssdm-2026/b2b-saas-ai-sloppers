package ua.edu.ukma.community.board;

import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Оголошення на дошці (FR-043). {@code contact} — персональні дані: назовні його віддає лише
 * {@link AdvertView}, а {@link #toString()} його не містить, щоб він не потрапив у лог (NFR-009).
 */
public record Advert(
        UUID id,
        UUID authorId,
        AdvertType type,
        String title,
        String description,
        int slots,
        OffsetDateTime deadline,
        String contact,
        AdvertStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public Advert {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (slots < 1) {
            throw new IllegalArgumentException("slots must be positive");
        }
    }

    public boolean isAuthor(UUID userId) {
        return authorId.equals(userId);
    }

    public boolean isDeleted() {
        return status == AdvertStatus.DELETED;
    }

    /**
     * Чи приймає оголошення відгуки. Оголошення з дедлайном, що минув, вважається закритим одразу,
     * навіть якщо планувальник ще не перевів його в {@code CLOSED} (FR-052).
     */
    public boolean isOpen(OffsetDateTime now) {
        return status == AdvertStatus.ACTIVE && deadline.isAfter(now);
    }

    /** Статус, який бачить користувач: прострочене {@code ACTIVE} показуємо як {@code CLOSED}. */
    public AdvertStatus effectiveStatus(OffsetDateTime now) {
        return status == AdvertStatus.ACTIVE && !isOpen(now) ? AdvertStatus.CLOSED : status;
    }

    public Advert close(OffsetDateTime now) {
        if (status != AdvertStatus.ACTIVE) {
            throw transitionNotAllowed("закрити");
        }
        return withStatus(AdvertStatus.CLOSED, now);
    }

    public Advert delete(OffsetDateTime now) {
        if (status == AdvertStatus.DELETED) {
            throw transitionNotAllowed("видалити");
        }
        return withStatus(AdvertStatus.DELETED, now);
    }

    private Advert withStatus(AdvertStatus newStatus, OffsetDateTime now) {
        return new Advert(id, authorId, type, title, description, slots, deadline, contact, newStatus,
                createdAt, now);
    }

    private ApiException transitionNotAllowed(String action) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                "Оголошення у статусі " + status + " не можна " + action);
    }

    /** Без контакту (NFR-009). */
    @Override
    public String toString() {
        return "Advert[id=" + id + ", authorId=" + authorId + ", type=" + type + ", slots=" + slots
                + ", deadline=" + deadline + ", status=" + status + "]";
    }
}
