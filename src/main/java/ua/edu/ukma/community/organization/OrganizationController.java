package ua.edu.ukma.community.organization;

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
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/organizations")
@Tag(name = "Organizations")
public class OrganizationController {

    private static final String ORG_ID_EXAMPLE = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60";

    private final OrganizationService organizations;
    private final OrganizationCreationService creation;
    private final CurrentUser currentUser;
    private final OrgRolePolicy policy;

    public OrganizationController(OrganizationService organizations, OrganizationCreationService creation,
                                  CurrentUser currentUser, OrgRolePolicy policy) {
        this.organizations = organizations;
        this.creation = creation;
        this.currentUser = currentUser;
        this.policy = policy;
    }

    @GetMapping
    @Operation(operationId = "listOrganizations", summary = "Список організацій",
            description = "Публічна (доступна гостям). Пагінація зі зсувом, сортування за назвою.")
    @ApiResponse(responseCode = "200", description = "Сторінка організацій")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    public OffsetPage<Organization> listOrganizations(
            @Parameter(description = "Пошук за назвою або slug, без урахування регістру", example = "клуб")
            @RequestParam(name = "q", required = false)
            @Size(max = 100, message = "Пошуковий запит: не довше 100 символів") String q,

            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        return organizations.list(q, limit, offset);
    }

    @GetMapping("/{organizationId}")
    @Operation(operationId = "getOrganization", summary = "Організація за ідентифікатором",
            description = "Публічна (доступна гостям).")
    @ApiResponse(responseCode = "200", description = "Організацію знайдено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public Organization getOrganization(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId) {
        return organizations.get(organizationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "createOrganization", summary = "Створити організацію",
            description = "Лише Global Admin. Slug має бути унікальним. Перший голова (headEmail) "
                    + "призначається одразу: організація без голови не створюється.")
    @ApiResponse(responseCode = "201", description = "Організацію створено",
            headers = @Header(name = "Location", description = "URL нової організації",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ResponseEntity<Organization> createOrganization(@Valid @RequestBody OrganizationCreateRequest request) {
        Organization created = creation.create(currentUser.require(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{organizationId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "updateOrganization", summary = "Частково оновити організацію",
            description = "Суперадмін або голова цієї організації. Змінюються лише передані поля.")
    @ApiResponse(responseCode = "200", description = "Оновлену організацію повернуто")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public Organization updateOrganization(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody OrganizationUpdateRequest request) {
        policy.requireHeadOrAdmin(currentUser.require(), organizationId);
        return organizations.update(organizationId, request);
    }

    @DeleteMapping("/{organizationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "deleteOrganization", summary = "Видалити організацію",
            description = "Лише Global Admin. Разом з організацією видаляються її події, голови й файли.")
    @ApiResponse(responseCode = "204", description = "Організацію видалено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public void deleteOrganization(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId) {
        policy.requireGlobalAdmin(currentUser.require());
        organizations.delete(organizationId);
    }
}
