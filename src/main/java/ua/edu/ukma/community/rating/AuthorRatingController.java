package ua.edu.ukma.community.rating;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.Set;
import java.util.UUID;

/** Оцінка автора оголошення його учасником (FR-061). */
@RestController
@RequestMapping(ApiPaths.V1 + "/adverts/{advertId}/rating")
@Tag(name = "Ratings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AuthorRatingController {

    private static final String ADVERT_ID_EXAMPLE = "b1000000-0000-4000-8000-000000000001";

    private final RatingService ratings;
    private final CurrentUser currentUser;

    public AuthorRatingController(RatingService ratings, CurrentUser currentUser) {
        this.ratings = ratings;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "rateAdvertAuthor", summary = "Оцінити автора оголошення",
            description = "FR-061: лише підтверджений учасник, лише після закриття оголошення (вручну, за "
                    + "дедлайном або після заповнення місць), один раз. У відповіді — оновлений рейтинг автора "
                    + "(FR-062).")
    @ApiResponse(responseCode = "201", description = "Оцінку збережено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public AuthorRatingResponse rateAdvertAuthor(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId,
            @Valid @RequestBody RatingRequest request) {
        AuthorRating rating = ratings.rateAuthor(currentUser.require(), advertId, request.score());
        return AuthorRatingResponse.from(rating,
                ratings.authorSummaries(Set.of(rating.authorId())).get(rating.authorId()));
    }
}
