package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserDirectory;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Інструменти Global Admin: черга скарг (FR-057), блокування користувачів (FR-054) і список
 * заборонених слів для автофільтра (FR-058). Права перевіряє {@link ModerationService}, тож 403
 * повертається однаково для всіх маршрутів.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/moderation")
@Tag(name = "Moderation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ModerationController {

    private static final String USER_ID_EXAMPLE = "a1000000-0000-4000-8000-000000000001";
    private static final String COMPLAINT_ID_EXAMPLE = "c1000000-0000-4000-8000-000000000001";

    private final ModerationService moderation;
    private final CurrentUser currentUser;
    private final UserDirectory directory;
    private final Clock clock;

    public ModerationController(ModerationService moderation, CurrentUser currentUser, UserDirectory directory,
                                Clock clock) {
        this.moderation = moderation;
        this.currentUser = currentUser;
        this.directory = directory;
        this.clock = clock;
    }

    // ---- скарги ----

    @GetMapping("/complaints")
    @Operation(operationId = "listComplaints", summary = "Черга скарг",
            description = "FR-057: скарги, найновіші спершу, з фільтром за станом. Лише Global Admin.")
    @ApiResponse(responseCode = "200", description = "Сторінка скарг")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    public OffsetPage<ComplaintResponse> listComplaints(
            @Parameter(description = "Лише скарги в цьому стані", example = "OPEN")
            @RequestParam(name = "status", required = false) ComplaintStatus status,

            @Parameter(description = "Розмір сторінки, 1–100",
                    schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit має бути ≥ 1")
            @Max(value = 100, message = "limit має бути ≤ 100") int limit,

            @Parameter(description = "Скільки елементів пропустити",
                    schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(name = "offset", defaultValue = "0")
            @Min(value = 0, message = "offset має бути ≥ 0") int offset) {
        OffsetPage<Complaint> page = moderation.listComplaints(currentUser.require(), status, limit, offset);
        Map<UUID, String> names = directory.fullNames(page.data().stream().map(Complaint::authorId).toList());
        return page.map(complaint -> ComplaintResponse.from(complaint, names.get(complaint.authorId())));
    }

    @PostMapping("/complaints/{complaintId}/resolve")
    @Operation(operationId = "resolveComplaint", summary = "Підтвердити скаргу і прибрати контент",
            description = "FR-057: оголошення переходить у DELETED, відгук — у REMOVED. Інші відкриті "
                    + "скарги на цей самий об'єкт закриваються тим самим рішенням.")
    @ApiResponse(responseCode = "200", description = "Скаргу підтверджено, контент прибрано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ComplaintResponse resolveComplaint(
            @Parameter(description = "Ідентифікатор скарги", example = COMPLAINT_ID_EXAMPLE)
            @PathVariable("complaintId") UUID complaintId,
            @Valid @RequestBody(required = false) ComplaintDecisionRequest request) {
        return respond(moderation.resolve(currentUser.require(), complaintId, resolution(request)));
    }

    @PostMapping("/complaints/{complaintId}/dismiss")
    @Operation(operationId = "dismissComplaint", summary = "Відхилити скаргу",
            description = "FR-057: контент лишається, скарга закривається і більше не рахується "
                    + "в автоматичному блокуванні (FR-060).")
    @ApiResponse(responseCode = "200", description = "Скаргу відхилено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public ComplaintResponse dismissComplaint(
            @Parameter(description = "Ідентифікатор скарги", example = COMPLAINT_ID_EXAMPLE)
            @PathVariable("complaintId") UUID complaintId,
            @Valid @RequestBody(required = false) ComplaintDecisionRequest request) {
        return respond(moderation.dismiss(currentUser.require(), complaintId, resolution(request)));
    }

    // ---- блокування ----

    @PostMapping("/users/{userId}/ban")
    @Operation(operationId = "banUser", summary = "Заблокувати користувача",
            description = "FR-054: на строк або безстроково, із причиною. Заблокований не входить у "
                    + "захищені маршрути, а його активні оголошення зникають із дошки (FR-055).")
    @ApiResponse(responseCode = "200", description = "Користувача заблоковано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public BanResponse banUser(
            @Parameter(description = "Ідентифікатор користувача", example = USER_ID_EXAMPLE)
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody BanRequest request) {
        Ban ban = moderation.ban(currentUser.require(), userId, request.reason(), request.expiresAt());
        return BanResponse.from(ban, OffsetDateTime.now(clock));
    }

    @DeleteMapping("/users/{userId}/ban")
    @Operation(operationId = "unbanUser", summary = "Розблокувати користувача",
            description = "FR-054: знімає блокування достроково і повертає акаунту статус, який був до бану.")
    @ApiResponse(responseCode = "200", description = "Користувача розблоковано")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public BanResponse unbanUser(
            @Parameter(description = "Ідентифікатор користувача", example = USER_ID_EXAMPLE)
            @PathVariable("userId") UUID userId) {
        return BanResponse.from(moderation.unban(currentUser.require(), userId), OffsetDateTime.now(clock));
    }

    @GetMapping("/users/{userId}/bans")
    @Operation(operationId = "listBans", summary = "Історія блокувань користувача",
            description = "FR-054: журнал блокувань, найновіші спершу; рядки не видаляються.")
    @ApiResponse(responseCode = "200", description = "Історія блокувань")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    public List<BanResponse> listBans(
            @Parameter(description = "Ідентифікатор користувача", example = USER_ID_EXAMPLE)
            @PathVariable("userId") UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return moderation.listBans(currentUser.require(), userId).stream()
                .map(ban -> BanResponse.from(ban, now))
                .toList();
    }

    // ---- автофільтр ----

    @GetMapping("/banned-words")
    @Operation(operationId = "listBannedWords", summary = "Список заборонених слів",
            description = "FR-058: слова, за якими автофільтр відхиляє оголошення й відгуки.")
    @ApiResponse(responseCode = "200", description = "Список слів")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    public List<BannedWordResponse> listBannedWords() {
        return moderation.listBannedWords(currentUser.require()).stream()
                .map(BannedWordResponse::from)
                .toList();
    }

    @PostMapping("/banned-words")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addBannedWord", summary = "Додати заборонене слово",
            description = "FR-058: одне слово, регістр не має значення. Фільтр порівнює слова цілком, "
                    + "тому «кіт» не блокує «Нікіту».")
    @ApiResponse(responseCode = "201", description = "Слово додано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public BannedWordResponse addBannedWord(@Valid @RequestBody BannedWordRequest request) {
        return BannedWordResponse.from(moderation.addBannedWord(currentUser.require(), request.word()));
    }

    @DeleteMapping("/banned-words/{wordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeBannedWord", summary = "Прибрати слово зі списку",
            description = "FR-058.")
    @ApiResponse(responseCode = "204", description = "Слово прибрано")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public void removeBannedWord(
            @Parameter(description = "Ідентифікатор слова", example = COMPLAINT_ID_EXAMPLE)
            @PathVariable("wordId") UUID wordId) {
        moderation.removeBannedWord(currentUser.require(), wordId);
    }

    private static String resolution(ComplaintDecisionRequest request) {
        return request == null ? null : request.resolution();
    }

    private ComplaintResponse respond(Complaint complaint) {
        return ComplaintResponse.from(complaint, directory.fullName(complaint.authorId()));
    }
}
