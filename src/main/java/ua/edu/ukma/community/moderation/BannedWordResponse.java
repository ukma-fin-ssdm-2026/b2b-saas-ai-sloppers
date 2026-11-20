package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Слово зі списку автофільтра (FR-058). */
@Schema(description = "Заборонене слово")
public record BannedWordResponse(
        UUID id,
        String word,
        @Schema(description = "Хто додав") UUID addedBy,
        OffsetDateTime createdAt
) {

    public static BannedWordResponse from(BannedWord word) {
        return new BannedWordResponse(word.id(), word.word(), word.addedBy(), word.createdAt());
    }
}
