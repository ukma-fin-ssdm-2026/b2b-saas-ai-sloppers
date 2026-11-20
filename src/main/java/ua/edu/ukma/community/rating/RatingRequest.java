package ua.edu.ukma.community.rating;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Оцінка за шкалою 1–5 (FR-061, FR-063). */
@Schema(description = "Оцінка")
public record RatingRequest(

        @Schema(description = "Оцінка від 1 до 5", example = "5", minimum = "1", maximum = "5",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Оцінка обов'язкова")
        @Min(value = Scores.MIN, message = "Оцінка: від 1 до 5")
        @Max(value = Scores.MAX, message = "Оцінка: від 1 до 5")
        Integer score
) {
}
