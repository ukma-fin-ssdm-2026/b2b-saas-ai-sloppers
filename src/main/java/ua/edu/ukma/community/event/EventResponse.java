package ua.edu.ukma.community.event;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Подія у відповідях API. Службові поля скрейпера ({@code sourceId}, {@code externalPostId}) назовні не віддаються.
 */
@Schema(description = "Подія студентської організації")
public record EventResponse(
        @Schema(description = "Ідентифікатор", example = "e1000000-0000-4000-8000-000000000001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Організація-власник", example = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID organizationId,

        @Schema(description = "Назва", example = "Граємо в кікер",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Опис", example = "Граємо в кікер на гроші")
        String description,

        @Schema(description = "Дата й час початку (ISO 8601)", example = "2026-10-21T18:00:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime startsAt,

        @Schema(description = "Місце проведення", example = "НаУКМА, лаба")
        String location,

        @Schema(description = "Посилання на оригінальний пост або анонс", example = "https://www.instagram.com/p/C1a2b3d4/")
        String originalUrl,

        @Schema(description = "Статус модерації", example = "PUBLISHED", requiredMode = Schema.RequiredMode.REQUIRED)
        EventStatus status,

        @Schema(description = "false — автоопублікована подія, яку голова ще не переглянув (FR-032)",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean verified,

        @Schema(description = "Чи створена подія скрейпером", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean scraped,

        @Schema(description = "Час створення (ISO 8601)", example = "2026-10-14T10:00:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(description = "Час останньої зміни (ISO 8601)", example = "2026-10-14T10:00:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime updatedAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.id(), event.organizationId(), event.title(), event.description(),
                event.startsAt(), event.location(), event.originalUrl(), event.status(), event.verified(),
                event.isScraped(), event.createdAt(), event.updatedAt());
    }
}
