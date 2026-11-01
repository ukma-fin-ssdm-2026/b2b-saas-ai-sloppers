package ua.edu.ukma.community.organization;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.AuthRequests;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.HeadService;
import ua.edu.ukma.community.auth.OrganizationHead;
import ua.edu.ukma.community.auth.RoleChange;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.List;
import java.util.UUID;

/**
 * Голови організації (FR-008–FR-013).
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/organizations/{organizationId}")
@Tag(name = "Heads")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class OrganizationHeadController {

    private static final String ORG_ID_EXAMPLE = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60";

    private final OrganizationService organizations;
    private final HeadService heads;
    private final CurrentUser currentUser;

    public OrganizationHeadController(OrganizationService organizations, HeadService heads, CurrentUser currentUser) {
        this.organizations = organizations;
        this.heads = heads;
        this.currentUser = currentUser;
    }

    @GetMapping("/heads")
    @Operation(operationId = "listHeads", summary = "Голови організації",
            description = "Голова цієї організації або Global Admin.")
    @ApiResponse(responseCode = "200", description = "Список голів")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public List<OrganizationHead> listHeads(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId) {
        organizations.get(organizationId);
        return heads.list(currentUser.require(), organizationId);
    }

    @PostMapping("/heads")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "appointHead", summary = "Призначити голову",
            description = "Global Admin — у будь-якій організації (FR-008), голова — у своїй (FR-009). "
                    + "З replacesUserId знімає вказаного голову в тій самій операції (FR-012, лише Global Admin).")
    @ApiResponse(responseCode = "201", description = "Голову призначено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public OrganizationHead appointHead(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody AuthRequests.AppointHead request) {
        organizations.get(organizationId);
        return heads.appoint(currentUser.require(), organizationId, request.email(), request.replacesUserId());
    }

    @DeleteMapping("/heads/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeHead", summary = "Зняти голову",
            description = "Голова — у своїй організації, Global Admin — у будь-якій (FR-010). "
                    + "Останнього голову зняти не можна: 409 LAST_HEAD (FR-011).")
    @ApiResponse(responseCode = "204", description = "Голову знято")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public void removeHead(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,
            @Parameter(description = "Ідентифікатор користувача-голови (UUID)")
            @PathVariable("userId") UUID userId) {
        organizations.get(organizationId);
        heads.remove(currentUser.require(), organizationId, userId);
    }

    @GetMapping("/role-log")
    @Operation(operationId = "roleLog", summary = "Журнал змін ролей",
            description = "Хто, кому, яку роль призначив чи зняв і коли (FR-013). Голова цієї організації або "
                    + "Global Admin. Нові записи першими.")
    @ApiResponse(responseCode = "200", description = "Журнал")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public List<RoleChange> roleLog(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId) {
        organizations.get(organizationId);
        return heads.log(currentUser.require(), organizationId);
    }
}
