package ua.edu.ukma.community.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.UUID;

/**
 * Картинка (постер) події: файл у Supabase Storage, посилання в {@link EventResponse#imageUrl()}.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/events/{eventId}/image")
@Tag(name = "Events")
public class EventImageController {

    private final EventImageService images;
    private final EventService events;
    private final CurrentUser currentUser;
    private final OrgRolePolicy policy;

    public EventImageController(EventImageService images, EventService events, CurrentUser currentUser,
                                OrgRolePolicy policy) {
        this.images = images;
        this.events = events;
        this.currentUser = currentUser;
        this.policy = policy;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "uploadEventImage", summary = "Завантажити або замінити картинку події",
            description = "Голова організації-власника. JPEG, PNG або WebP до 5 МБ, поле форми `file`. "
                    + "Відхилену подію змінити не можна (409). Попередня картинка видаляється зі сховища.")
    @ApiResponse(responseCode = "200", description = "Подію з новим imageUrl повернуто")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @ApiResponse(responseCode = "413", ref = OpenApiConfig.PAYLOAD_TOO_LARGE)
    @ApiResponse(responseCode = "415", ref = OpenApiConfig.UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "503", ref = OpenApiConfig.STORAGE_UNAVAILABLE)
    public EventResponse uploadEventImage(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EventController.EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId,
            @Parameter(description = "Файл картинки") @RequestPart("file") MultipartFile file) {
        requireHeadOfEvent(eventId);
        return EventResponse.from(images.upload(eventId, file));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "deleteEventImage", summary = "Прибрати картинку події",
            description = "Голова організації-власника. Якщо картинки немає, нічого не змінюється.")
    @ApiResponse(responseCode = "204", description = "Картинки більше немає")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public void deleteEventImage(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EventController.EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        requireHeadOfEvent(eventId);
        images.delete(eventId);
    }

    private void requireHeadOfEvent(UUID eventId) {
        policy.requireHead(currentUser.require(), events.get(eventId).organizationId());
    }
}
