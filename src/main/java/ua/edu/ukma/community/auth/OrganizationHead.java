package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;


@Schema(description = "Голова організації")
public record OrganizationHead(
        @Schema(description = "Ідентифікатор користувача", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,

        @Schema(description = "Пошта", example = "head@ukma.edu.ua", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Schema(description = "Ім'я та прізвище; null, якщо користувач їх ще не задав (FR-069)",
                example = "Теодора Гречко", nullable = true)
        String name,

        @Schema(description = "Хто призначив; null, якщо цього користувача вже видалено")
        UUID appointedBy,

        @Schema(description = "Коли призначено (ISO 8601)", requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime appointedAt
) {
}
