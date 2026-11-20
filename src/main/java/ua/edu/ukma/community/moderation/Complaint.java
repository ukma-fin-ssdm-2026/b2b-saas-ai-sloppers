package ua.edu.ukma.community.moderation;

import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Скарга на оголошення або відгук (FR-056), таблиця {@code complaint}.
 *
 * <p>{@code authorId} — автор оскарженого контенту на момент скарги. Він зберігається поряд, а не
 * вичитується щоразу з дошки: за ним рахується «3 скарги від різних користувачів» для автоматичного
 * блокування (FR-060), і прибраний контент не має зникати з цього підрахунку.
 */
public record Complaint(
        UUID id,
        UUID reporterId,
        UUID authorId,
        ContentType targetType,
        UUID targetId,
        ComplaintCategory category,
        String comment,
        ComplaintStatus status,
        UUID resolvedBy,
        String resolution,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {

    public Complaint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reporterId, "reporterId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Complaint open(UUID reporterId, UUID authorId, ContentType targetType, UUID targetId,
                                 ComplaintCategory category, String comment, OffsetDateTime now) {
        return new Complaint(UUID.randomUUID(), reporterId, authorId, targetType, targetId, category, comment,
                ComplaintStatus.OPEN, null, null, now, null);
    }

    public boolean isOpen() {
        return status == ComplaintStatus.OPEN;
    }

    /**
     * Рішення Global Admin (FR-057). Повторне рішення щодо вже закритої скарги не проходить: інакше
     * два адміністратори, що відкрили чергу одночасно, прибрали б контент двічі.
     *
     * @param decision {@code RESOLVED} або {@code DISMISSED}
     */
    public Complaint resolve(ComplaintStatus decision, UUID adminId, String resolution, OffsetDateTime at) {
        if (decision == ComplaintStatus.OPEN) {
            throw new IllegalArgumentException("decision must be RESOLVED or DISMISSED");
        }
        if (!isOpen()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Скаргу вже розглянуто: статус " + status);
        }
        return new Complaint(id, reporterId, authorId, targetType, targetId, category, comment, decision,
                adminId, resolution, createdAt, at);
    }

    /** Без тексту скарги: це вільний текст від користувача, який не має потрапляти в лог. */
    @Override
    public String toString() {
        return "Complaint[id=" + id + ", target=" + targetType + ":" + targetId + ", category=" + category
                + ", status=" + status + "]";
    }
}
