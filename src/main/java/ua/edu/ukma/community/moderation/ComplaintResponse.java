package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Скарга в черзі модератора (FR-057). Текст скарги й контакти тут не дублюються: модератор відкриває
 * сам контент за {@code targetId}.
 */
@Schema(description = "Скарга")
public record ComplaintResponse(
        UUID id,
        @Schema(description = "Хто поскаржився") UUID reporterId,
        @Schema(description = "Автор оскарженого контенту") UUID authorId,
        @Schema(description = "Ім'я автора контенту, якщо задане") String authorName,
        ContentType targetType,
        UUID targetId,
        ComplaintCategory category,
        String comment,
        ComplaintStatus status,
        @Schema(description = "Хто розглянув") UUID resolvedBy,
        String resolution,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {

    public static ComplaintResponse from(Complaint complaint, String authorName) {
        return new ComplaintResponse(complaint.id(), complaint.reporterId(), complaint.authorId(), authorName,
                complaint.targetType(), complaint.targetId(), complaint.category(), complaint.comment(),
                complaint.status(), complaint.resolvedBy(), complaint.resolution(), complaint.createdAt(),
                complaint.resolvedAt());
    }
}
