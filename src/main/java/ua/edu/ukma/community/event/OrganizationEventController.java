package ua.edu.ukma.community.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.net.URI;
import java.util.UUID;


@RestController
@RequestMapping(ApiPaths.V1 + "/organizations/{organizationId}/events")
@Tag(name = "Events")
public class OrganizationEventController {

    private static final String ORG_ID_EXAMPLE = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60";

    private final EventService events;
    private final CurrentUser currentUser;
    private final OrgRolePolicy policy;

    public OrganizationEventController(EventService events, CurrentUser currentUser, OrgRolePolicy policy) {
        this.events = events;
        this.currentUser = currentUser;
        this.policy = policy;
    }

    @GetMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "listOrganizationEvents", summary = "Події організації для голови",
            description = "Голова організації. Усі статуси; status=PENDING — черга модерації (FR-033), "
                    + "verified=false — автоопубліковані, ще не переглянуті (FR-032).")
    @ApiResponse(responseCode = "200", description = "Сторінка подій організації")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public OffsetPage<EventResponse> listOrganizationEvents(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,

            @Parameter(description = "Фільтр за статусом", example = "PENDING")
            @RequestParam(name = "status", required = false) EventStatus status,

            @Parameter(description = "Фільтр за ознакою перевірки головою", example = "false")
            @RequestParam(name = "verified", required = false) Boolean verified,

            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        policy.requireHead(currentUser.require(), organizationId);
        return events.listForOrganization(organizationId, status, verified, limit, offset).map(EventResponse::from);
    }

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "createEvent", summary = "Створити подію вручну",
            description = "Голова організації (FR-037). Подія публікується одразу.")
    @ApiResponse(responseCode = "201", description = "Подію створено",
            headers = @Header(name = "Location", description = "URL нової події",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<EventResponse> createEvent(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody EventCreateRequest request) {
        policy.requireHead(currentUser.require(), organizationId);
        Event created = events.create(organizationId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ApiPaths.V1 + "/events/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(EventResponse.from(created));
    }
}
