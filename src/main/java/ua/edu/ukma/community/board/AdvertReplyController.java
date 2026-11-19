package ua.edu.ukma.community.board;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.UserDirectory;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Відгуки на оголошення (FR-046–FR-048, FR-053). */
@RestController
@RequestMapping(ApiPaths.V1 + "/adverts/{advertId}/replies")
@Tag(name = "Board")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdvertReplyController {

    private static final String REPLY_ID_EXAMPLE = "c1000000-0000-4000-8000-000000000001";

    private final BoardService board;
    private final CurrentUser currentUser;
    private final UserDirectory directory;

    public AdvertReplyController(BoardService board, CurrentUser currentUser, UserDirectory directory) {
        this.board = board;
        this.currentUser = currentUser;
        this.directory = directory;
    }

    @GetMapping
    @Operation(operationId = "listAdvertReplies", summary = "Відгуки на моє оголошення",
            description = "Лише автор (FR-047). Відкликані відгуки не показуються; найстаріші спершу.")
    @ApiResponse(responseCode = "200", description = "Відгуки")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public List<ReplyResponse> listAdvertReplies(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = AdvertController.ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId) {
        List<Reply> replies = board.listReplies(currentUser.require(), advertId);
        Map<UUID, String> names = directory.fullNames(replies.stream().map(Reply::userId).toList());
        return replies.stream().map(r -> ReplyResponse.from(r, names.get(r.userId()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "replyToAdvert", summary = "Відгукнутися на оголошення",
            description = "FR-046: лише на чуже (на своє — 403 OWN_ADVERT) відкрите оголошення, один відгук "
                    + "на оголошення. Відкликаний раніше відгук можна подати знову, відхилений — ні.")
    @ApiResponse(responseCode = "201", description = "Відгук подано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ReplyResponse replyToAdvert(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = AdvertController.ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId,
            @Valid @RequestBody ReplyCreateRequest request) {
        return toResponse(board.reply(currentUser.require(), advertId, request.message()));
    }

    @PostMapping("/{replyId}/confirm")
    @Operation(operationId = "confirmAdvertReply", summary = "Підтвердити відгук",
            description = "Автор (FR-048). Відгукувач починає бачити контакт (FR-049). Коли підтверджених стає "
                    + "стільки, скільки місць, оголошення закривається (FR-050).")
    @ApiResponse(responseCode = "200", description = "Відгук підтверджено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ReplyResponse confirmAdvertReply(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = AdvertController.ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId,
            @Parameter(description = "Ідентифікатор відгуку (UUID)", example = REPLY_ID_EXAMPLE)
            @PathVariable("replyId") UUID replyId) {
        return toResponse(board.confirm(currentUser.require(), advertId, replyId));
    }

    @PostMapping("/{replyId}/reject")
    @Operation(operationId = "rejectAdvertReply", summary = "Відхилити відгук", description = "Автор (FR-048).")
    @ApiResponse(responseCode = "200", description = "Відгук відхилено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ReplyResponse rejectAdvertReply(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = AdvertController.ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId,
            @Parameter(description = "Ідентифікатор відгуку (UUID)", example = REPLY_ID_EXAMPLE)
            @PathVariable("replyId") UUID replyId) {
        return toResponse(board.reject(currentUser.require(), advertId, replyId));
    }

    @PostMapping("/{replyId}/withdraw")
    @Operation(operationId = "withdrawAdvertReply", summary = "Відкликати свій відгук",
            description = "Автор відгуку, поки той у статусі PENDING (FR-053).")
    @ApiResponse(responseCode = "200", description = "Відгук відкликано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ReplyResponse withdrawAdvertReply(
            @Parameter(description = "Ідентифікатор оголошення (UUID)", example = AdvertController.ADVERT_ID_EXAMPLE)
            @PathVariable("advertId") UUID advertId,
            @Parameter(description = "Ідентифікатор відгуку (UUID)", example = REPLY_ID_EXAMPLE)
            @PathVariable("replyId") UUID replyId) {
        return toResponse(board.withdraw(currentUser.require(), advertId, replyId));
    }

    private ReplyResponse toResponse(Reply reply) {
        return ReplyResponse.from(reply, directory.fullName(reply.userId()));
    }
}
