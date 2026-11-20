package ua.edu.ukma.community.rating;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Рейтинг організації і ваша оцінка (FR-063, FR-064). */
@Schema(description = "Рейтинг організації")
public record OrganizationRatingResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID organizationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RatingSummary rating,
        @Schema(description = "Ваша оцінка; null, якщо ви ще не оцінювали", example = "4", nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Integer myScore
) {

    public static OrganizationRatingResponse from(RatingService.OrganizationRatingView view) {
        return new OrganizationRatingResponse(view.organizationId(), view.summary(), view.myScore());
    }
}
