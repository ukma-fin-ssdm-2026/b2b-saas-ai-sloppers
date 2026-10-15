package ua.edu.ukma.community.organization;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Студентська організація — ресурс каталогу (компонент C-04).
 */
@Schema(description = "Студентська організація")
public record Organization(
        @Schema(description = "Ідентифікатор", example = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Назва", example = "Дебатний клуб НаУКМА", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Унікальний людиночитаний ідентифікатор для URL", example = "debate-club",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,

        @Schema(description = "Опис діяльності",
                example = "Щотижневі тренування з британського парламентського формату")
        String description,

        @Schema(description = "Instagram-акаунт без @, з якого скрейпер збирає події", example = "ukma.debate")
        String instagramHandle,

        @Schema(description = "Контактна пошта організації", example = "debate@ukma.edu.ua")
        String contactEmail,

        @Schema(description = "Публікувати спаршені події без модерації (FR-029); за замовчуванням вимкнено",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean autoPublish,

        @Schema(description = "Час створення (ISO 8601)", example = "2026-09-01T10:00:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(description = "Час останньої зміни (ISO 8601)", example = "2026-09-15T12:30:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime updatedAt
) {
}
