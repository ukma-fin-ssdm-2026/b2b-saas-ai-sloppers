package ua.edu.ukma.community.board;

import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Відгук на оголошення (FR-046), таблиця {@code advert_response}. Один користувач — один рядок на оголошення:
 * відкликаний відгук можна подати знову, і тоді оновлюється той самий рядок ({@link #resubmit}).
 */
public record Reply(
        UUID id,
        UUID advertId,
        UUID userId,
        String message,
        ReplyStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime decidedAt
) {

    public Reply {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(advertId, "advertId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Reply create(UUID advertId, UUID userId, String message, OffsetDateTime now) {
        return new Reply(UUID.randomUUID(), advertId, userId, message, ReplyStatus.PENDING, now, null);
    }

    public boolean isConfirmed() {
        return status == ReplyStatus.CONFIRMED;
    }

    public boolean isRemoved() {
        return status == ReplyStatus.REMOVED;
    }

    public Reply confirm(OffsetDateTime now) {
        return decide(ReplyStatus.CONFIRMED, "підтвердити", now);
    }

    public Reply reject(OffsetDateTime now) {
        return decide(ReplyStatus.REJECTED, "відхилити", now);
    }

    /** FR-053: лише поки автор не ухвалив рішення. */
    public Reply withdraw(OffsetDateTime now) {
        return decide(ReplyStatus.WITHDRAWN, "відкликати", now);
    }

    /**
     * Модератор прибирає відгук за підтвердженою скаргою (FR-057). Можливо з будь-якого статусу:
     * образливим може бути й уже підтверджений відгук.
     */
    public Reply remove(OffsetDateTime now) {
        if (status == ReplyStatus.REMOVED) {
            throw transitionNotAllowed("прибрати");
        }
        return new Reply(id, advertId, userId, message, ReplyStatus.REMOVED, createdAt, now);
    }

    /** Повторний відгук після відкликання: знову {@code PENDING}, з новим повідомленням і часом. */
    public Reply resubmit(String newMessage, OffsetDateTime now) {
        if (status != ReplyStatus.WITHDRAWN) {
            throw transitionNotAllowed("подати повторно");
        }
        return new Reply(id, advertId, userId, newMessage, ReplyStatus.PENDING, now, null);
    }

    private Reply decide(ReplyStatus newStatus, String action, OffsetDateTime now) {
        if (status != ReplyStatus.PENDING) {
            throw transitionNotAllowed(action);
        }
        return new Reply(id, advertId, userId, message, newStatus, createdAt, now);
    }

    private ApiException transitionNotAllowed(String action) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                "Відгук у статусі " + status + " не можна " + action);
    }

    /** Без тексту повідомлення: це персональні дані відгукувача. */
    @Override
    public String toString() {
        return "Reply[id=" + id + ", advertId=" + advertId + ", userId=" + userId + ", status=" + status + "]";
    }
}
