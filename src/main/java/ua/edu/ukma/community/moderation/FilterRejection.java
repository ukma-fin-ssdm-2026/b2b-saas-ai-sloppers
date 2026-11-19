package ua.edu.ukma.community.moderation;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Запис про відмову автофільтра (FR-058). Сам відхилений текст не зберігаємо — лише слово, що зматчилось:
 * рядки потрібні тільки для підрахунку «3 відхилені повідомлення за добу» (FR-060).
 */
public record FilterRejection(
        UUID id,
        UUID userId,
        ContentType targetType,
        String matchedWord,
        OffsetDateTime createdAt
) {

    public FilterRejection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(matchedWord, "matchedWord");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static FilterRejection of(UUID userId, ContentType targetType, String matchedWord,
                                     OffsetDateTime now) {
        return new FilterRejection(UUID.randomUUID(), userId, targetType, matchedWord, now);
    }
}
