package ua.edu.ukma.community.board;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.net.URI;
import java.util.UUID;

/** Оголошення на дошці (FR-043–FR-045, FR-051). Гостям дошка недоступна: усі маршрути вимагають JWT. */
@RestController
@RequestMapping(ApiPaths.V1 + "/adverts")
@Tag(name = "Board")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdvertController {

    static final String ADVERT_ID_EXAMPLE = "b1000000-0000-4000-8000-000000000001";

    private final BoardService board;
    private final CurrentUser currentUser;

    public AdvertController(BoardService board, CurrentUser currentUser) {
        this.board = board;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(operationId = "listAdverts", summary = "Активні оголошення",
            description = "Лише відкриті оголошення (ACTIVE і дедлайн не минув), найновіші спершу; фільтр за типом "
                    + "(FR-045, FR-052). Контакт автора тут бачить лише сам автор і підтверджені відгукувачі.")
    @ApiResponse(responseCode = "200", description = "Сторінка оголошень")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    public OffsetPage<AdvertResponse> listAdverts(
            @Parameter(description = "Лише цей тип", example = "TEAM")
            @RequestParam(name = "type", required = false) AdvertType type,

            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        return board.listOpen(currentUser.require(), type, limit, offset).map(AdvertResponse::from);
    }

    @PostMapping
    @Operation(operationId = "createAdvert", summary = "Створити оголошення",
            description = "FR-043, FR-044: тип, заголовок, опис, кількість місць, дедлайн у майбутньому й контакт "
                    + "(Telegram, телефон або email).")
    @ApiResponse(responseCode = "201", description = "Оголошення створено",
            headers = @Header(name = "Location", description = "URL нового оголошення",
                    schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    public ResponseEntity<AdvertResponse> createAdvert(@Valid @RequestBody AdvertCreateRequest request) {
        AdvertView created = board.create(currentUser.require(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.advert().id()).toUri();
        return ResponseEntity.created(location).body(AdvertResponse.from(created));
    }

    @GetMapping("/mine")
    @Operation(operationId = "listMyAdverts", summary = "Мої оголошення",
            description = "Оголошення поточного користувача, активні й закриті, найновіші спершу.")
    @ApiResponse(responseCode = "200", description = "Сторінка оголошень")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    public OffsetPage<AdvertResponse> listMyAdverts(
            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        return board.listMine(currentUser.require(), limit, offset).map(AdvertResponse::from);
    }

    @GetMapping("/replied")
    @Operation(operationId = "listRepliedAdverts", summary = "Оголошення, на які я відгукнувся",
            description = "Зокрема закриті: після підтвердження відгуку тут видно контакт автора (FR-049, US-013).")
    @ApiResponse(responseCode = "200", description = "Сторінка оголошень")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    public OffsetPage<AdvertResponse> listRepliedAdverts(
            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        return board.listReplied(currentUser.require(), limit, offset).map(AdvertResponse::from);
    }

    @GetMapping("/{advertId}")
    @Operation(operationId = "getAdvert", summary = "Оголошення",
            description = "Активне або закрите; видалене повертає 404. Контакт — лише автору й підтвердженому "
                    + "відгукувачу (FR-049).")
    @ApiResponse(responseCode = "200", description = "Оголошення знайдено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public AdvertResponse getAdvert(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId) {
        return AdvertResponse.from(board.get(currentUser.require(), advertId));
    }

    @PostMapping("/{advertId}/close")
    @Operation(operationId = "closeAdvert", summary = "Закрити оголошення",
            description = "Автор, у будь-який момент (FR-051). ACTIVE → CLOSED; нові відгуки більше не приймаються, "
                    + "підтверджені учасники зберігають контакт.")
    @ApiResponse(responseCode = "200", description = "Оголошення закрито")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public AdvertResponse closeAdvert(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId) {
        return AdvertResponse.from(board.close(currentUser.require(), advertId));
    }

    @DeleteMapping("/{advertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAdvert", summary = "Видалити оголошення",
            description = "Автор (FR-051). Оголошення зникає для всіх; рядок лишається в базі зі статусом DELETED.")
    @ApiResponse(responseCode = "204", description = "Оголошення видалено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public void deleteAdvert(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId) {
        board.delete(currentUser.require(), advertId);
    }
}
