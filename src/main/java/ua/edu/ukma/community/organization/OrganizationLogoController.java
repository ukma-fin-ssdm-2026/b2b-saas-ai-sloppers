package ua.edu.ukma.community.organization;

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
 * Логотип організації: файл у Supabase Storage, посилання в {@link Organization#logoUrl()}.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/organizations/{organizationId}/logo")
@Tag(name = "Organizations")
public class OrganizationLogoController {

    private static final String ORG_ID_EXAMPLE = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60";

    private final OrganizationLogoService logos;
    private final CurrentUser currentUser;
    private final OrgRolePolicy policy;

    public OrganizationLogoController(OrganizationLogoService logos, CurrentUser currentUser, OrgRolePolicy policy) {
        this.logos = logos;
        this.currentUser = currentUser;
        this.policy = policy;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "uploadOrganizationLogo", summary = "Завантажити або замінити логотип",
            description = "Global Admin або голова цієї організації. JPEG, PNG або WebP до 5 МБ, поле форми `file`. "
                    + "Формат визначається за вмістом файлу. Попередній логотип видаляється зі сховища.")
    @ApiResponse(responseCode = "200", description = "Організацію з новим logoUrl повернуто")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "413", ref = OpenApiConfig.PAYLOAD_TOO_LARGE)
    @ApiResponse(responseCode = "415", ref = OpenApiConfig.UNSUPPORTED_MEDIA_TYPE)
    @ApiResponse(responseCode = "503", ref = OpenApiConfig.STORAGE_UNAVAILABLE)
    public Organization uploadOrganizationLogo(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,
            @Parameter(description = "Файл картинки") @RequestPart("file") MultipartFile file) {
        policy.requireHeadOrAdmin(currentUser.require(), organizationId);
        return logos.upload(organizationId, file);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "deleteOrganizationLogo", summary = "Прибрати логотип",
            description = "Global Admin або голова цієї організації. Якщо логотипу немає, нічого не змінюється.")
    @ApiResponse(responseCode = "204", description = "Логотипу більше немає")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public void deleteOrganizationLogo(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId) {
        policy.requireHeadOrAdmin(currentUser.require(), organizationId);
        logos.delete(organizationId);
    }
}
