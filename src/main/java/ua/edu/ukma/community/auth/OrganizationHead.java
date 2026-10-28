package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Голова організації.
 */
@Schema(description = "Голова організації")
public record OrganizationHead(
        @Schema(description = "Ідентифікатор користувача", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,

        @Schema(description = "Пошта", example = "head@ukma.edu.ua", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Schema(description = "Хто призначив; null, якщо цього користувача вже видалено")
        UUID appointedBy,

        @Schema(description = "Коли призначено (ISO 8601)", requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime appointedAt
) {
}
