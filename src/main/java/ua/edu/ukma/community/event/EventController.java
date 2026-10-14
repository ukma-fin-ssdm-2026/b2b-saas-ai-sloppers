package ua.edu.ukma.community.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.time.OffsetDateTime;
import java.util.UUID;


@RestController
@RequestMapping(ApiPaths.V1 + "/events")
@Tag(name = "Events")
public class EventController {

    static final String EVENT_ID_EXAMPLE = "e1000000-0000-4000-8000-000000000001";

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @GetMapping
    @Operation(operationId = "listEvents", summary = "Дайджест подій",
            description = "Публічна (доступна гостям). Лише опубліковані майбутні події, відсортовані за часом "
                    + "початку (FR-038). Фільтри за організацією й діапазоном дат (FR-039). "
                    + "Знак «+» у зсуві часового поясу в query потрібно кодувати як %2B або використовувати «Z».")
    @ApiResponse(responseCode = "200", description = "Сторінка подій")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    public OffsetPage<EventResponse> listEvents(
            @Parameter(description = "Лише події цієї організації", example = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60")
            @RequestParam(name = "organizationId", required = false) UUID organizationId,

            @Parameter(description = "Початок не раніше (ISO 8601)", example = "2026-10-20T00:00:00Z")
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,

            @Parameter(description = "Початок раніше за (ISO 8601)", example = "2026-11-01T00:00:00Z")
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,

            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        return events.digest(organizationId, from, to, limit, offset).map(EventResponse::from);
    }

    @GetMapping("/{eventId}")
    @Operation(operationId = "getEvent", summary = "Деталі події",
            description = "Публічна (FR-040). Неопубліковані події повертають 404.")
    @ApiResponse(responseCode = "200", description = "Подію знайдено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public EventResponse getEvent(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        return EventResponse.from(events.getPublished(eventId));
    }

    @PatchMapping("/{eventId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "updateEvent", summary = "Частково оновити подію",
            description = "Голова організації-власника. Можна редагувати до і після публікації (FR-034), "
                    + "крім відхилених подій.")
    @ApiResponse(responseCode = "200", description = "Оновлену подію повернуто")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public EventResponse updateEvent(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody EventUpdateRequest request) {
        return EventResponse.from(events.update(eventId, request));
    }

    @PostMapping("/{eventId}/approve")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "approveEvent", summary = "Схвалити подію",
            description = "Голова організації. PENDING або UNPUBLISHED → PUBLISHED (FR-035); для неперевіреної "
                    + "автоопублікованої події позначає її переглянутою (FR-032).")
    @ApiResponse(responseCode = "200", description = "Подію опубліковано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public EventResponse approveEvent(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        return EventResponse.from(events.approve(eventId));
    }

    @PostMapping("/{eventId}/reject")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "rejectEvent", summary = "Відхилити подію",
            description = "Голова організації. Лише з черги модерації: PENDING → REJECTED (FR-035).")
    @ApiResponse(responseCode = "200", description = "Подію відхилено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public EventResponse rejectEvent(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        return EventResponse.from(events.reject(eventId));
    }

    @PostMapping("/{eventId}/unpublish")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "unpublishEvent", summary = "Зняти подію з публікації",
            description = "Голова організації. PUBLISHED → UNPUBLISHED (FR-036).")
    @ApiResponse(responseCode = "200", description = "Подію знято з публікації")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public EventResponse unpublishEvent(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        return EventResponse.from(events.unpublish(eventId));
    }
}
