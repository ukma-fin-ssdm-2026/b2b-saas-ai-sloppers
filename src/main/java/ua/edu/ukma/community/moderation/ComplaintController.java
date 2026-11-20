package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

/**
 * Скарги від користувачів (FR-056). Читає й розглядає їх лише Global Admin — це вже
 * {@link ModerationController}.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/complaints")
@Tag(name = "Moderation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ComplaintController {

    private final ModerationService moderation;
    private final CurrentUser currentUser;

    public ComplaintController(ModerationService moderation, CurrentUser currentUser) {
        this.moderation = moderation;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createComplaint", summary = "Поскаржитися на контент",
            description = "FR-056: скарга на оголошення або відгук із причиною (спам, образи, інше). "
                    + "Одна скарга на один об'єкт від одного користувача; на власний контент скаржитися "
                    + "не можна. Заблокований акаунт скарг не подає (FR-055).")
    @ApiResponse(responseCode = "201", description = "Скаргу прийнято")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ComplaintResponse createComplaint(@Valid @RequestBody ComplaintCreateRequest request) {
        Complaint complaint = moderation.complain(currentUser.require(), request.targetType(), request.targetId(),
                request.category(), request.comment());
        return ComplaintResponse.from(complaint, null);
    }
}
