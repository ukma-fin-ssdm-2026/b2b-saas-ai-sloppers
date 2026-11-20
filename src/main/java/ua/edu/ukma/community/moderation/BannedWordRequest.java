package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Нове слово в списку автофільтра (FR-058). */
@Schema(description = "Заборонене слово")
public record BannedWordRequest(

        @Schema(description = "Одне слово; регістр не має значення", example = "спам",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Слово обов'язкове")
        @Size(min = 2, max = 100, message = "Слово від 2 до 100 символів")
        String word
) {
}
