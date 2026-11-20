package ua.edu.ukma.community.rating;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Поставлена оцінка автора разом з його оновленим рейтингом (FR-061, FR-062). */
@Schema(description = "Оцінка автора оголошення")
public record AuthorRatingResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID advertId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID authorId,
        @Schema(description = "Ваша оцінка", example = "5", requiredMode = Schema.RequiredMode.REQUIRED) int score,
        @Schema(description = "Рейтинг автора з урахуванням цієї оцінки", requiredMode = Schema.RequiredMode.REQUIRED)
        RatingSummary authorRating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt
) {

    public static AuthorRatingResponse from(AuthorRating rating, RatingSummary authorRating) {
        return new AuthorRatingResponse(rating.advertId(), rating.authorId(), rating.score(), authorRating,
                rating.createdAt());
    }
}
