package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Скарга на оголошення або відгук (FR-056). */
@Schema(description = "Скарга на контент")
public record ComplaintCreateRequest(

        @Schema(description = "На що скарга", example = "ADVERT", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "targetType обов'язковий")
        ContentType targetType,

        @Schema(description = "Ідентифікатор оголошення або відгуку",
                example = "b1000000-0000-4000-8000-000000000001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "targetId обов'язковий")
        UUID targetId,

        @Schema(description = "Причина", example = "SPAM", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "category обов'язкова")
        ComplaintCategory category,

        @Schema(description = "Деталі, якщо потрібні", example = "Рекламує курси, не має стосунку до НаУКМА")
        @Size(max = 500, message = "Коментар не довший за 500 символів")
        String comment
) {
}
