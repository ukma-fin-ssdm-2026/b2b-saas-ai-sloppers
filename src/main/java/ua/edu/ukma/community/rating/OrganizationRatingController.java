package ua.edu.ukma.community.rating;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.UUID;

/**
 * Оцінка організації користувачем (FR-063). Середнє для всіх, зокрема гостей, уже є в самій
 * організації (FR-064); тут — ще й ваша власна оцінка, тому маршрут вимагає входу.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/organizations/{organizationId}/rating")
@Tag(name = "Ratings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class OrganizationRatingController {

    private static final String ORG_ID_EXAMPLE = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60";

    private final RatingService ratings;
    private final CurrentUser currentUser;

    public OrganizationRatingController(RatingService ratings, CurrentUser currentUser) {
        this.ratings = ratings;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(operationId = "getOrganizationRating", summary = "Рейтинг організації і моя оцінка",
            description = "FR-063, FR-064.")
    @ApiResponse(responseCode = "200", description = "Рейтинг")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public OrganizationRatingResponse getOrganizationRating(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId) {
        return OrganizationRatingResponse.from(ratings.organizationRating(currentUser.require(), organizationId));
    }

    @PutMapping
    @Operation(operationId = "rateOrganization", summary = "Оцінити організацію або змінити оцінку",
            description = "FR-063: одна оцінка 1–5 на користувача, повторний запит її змінює. Голова "
                    + "власну організацію не оцінює.")
    @ApiResponse(responseCode = "200", description = "Оцінку збережено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public OrganizationRatingResponse rateOrganization(
            @Parameter(description = "Ідентифікатор організації (UUID)", example = ORG_ID_EXAMPLE)
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody RatingRequest request) {
        return OrganizationRatingResponse.from(
                ratings.rateOrganization(currentUser.require(), organizationId, request.score()));
    }
}
