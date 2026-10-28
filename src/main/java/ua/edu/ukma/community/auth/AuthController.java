package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.List;


@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@Tag(name = "Auth")
public class AuthController {

    private final RegistrationService registration;
    private final LoginService logins;
    private final CurrentUser currentUser;
    private final HeadService heads;

    public AuthController(RegistrationService registration, LoginService login, CurrentUser currentUser,
                          HeadService heads) {
        this.registration = registration;
        this.logins = login;
        this.currentUser = currentUser;
        this.heads = heads;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "register", summary = "Зареєструватися",
            description = "Лише пошта @ukma.edu.ua (FR-001). Створює неактивний акаунт і надсилає 6-значний код "
                    + "(FR-002), дійсний 15 хв, 5 спроб (FR-003).")
    @ApiResponse(responseCode = "201", description = "Акаунт створено, код надіслано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    @ApiResponse(responseCode = "503", ref = OpenApiConfig.MAIL_UNAVAILABLE)
    public UserResponse register(@Valid @RequestBody AuthRequests.Register request) {
        return UserResponse.from(registration.register(request.email(), request.password()), List.of());
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "verifyEmail", summary = "Підтвердити пошту кодом",
            description = "Після успіху можна входити. Неправильний код зменшує кількість спроб.")
    @ApiResponse(responseCode = "204", description = "Пошту підтверджено")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    @ApiResponse(responseCode = "409", ref = OpenApiConfig.CONFLICT)
    public void verify(@Valid @RequestBody AuthRequests.Verify request) {
        registration.verify(request.email(), request.code());
    }

    @PostMapping("/resend-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "resendCode", summary = "Надіслати код ще раз",
            description = "Для непідтвердженого акаунта: новий код, старий стає недійсним. Не частіше ніж раз на "
                    + "хвилину. Для невідомої пошти теж 204, щоб не розкривати, які пошти зареєстровані.")
    @ApiResponse(responseCode = "204", description = "Якщо акаунт існує й не підтверджений, код надіслано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "429", ref = OpenApiConfig.TOO_MANY_REQUESTS)
    @ApiResponse(responseCode = "503", ref = OpenApiConfig.MAIL_UNAVAILABLE)
    public void resendCode(@Valid @RequestBody AuthRequests.ResendCode request) {
        registration.resendCode(request.email());
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Увійти",
            description = "Повертає JWT на 60 хв (FR-004, NFR-010). Неактивний акаунт — 403 ACCOUNT_NOT_VERIFIED.")
    @ApiResponse(responseCode = "200", description = "Токен доступу")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
    public TokenResponse login(@Valid @RequestBody AuthRequests.Login request) {
        return logins.login(request.email(), request.password());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(operationId = "me", summary = "Поточний користувач",
            description = "Пошта, роль Global Admin і організації, де користувач голова.")
    @ApiResponse(responseCode = "200", description = "Поточний користувач")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    public UserResponse me() {
        User user = currentUser.require();
        return UserResponse.from(user, heads.organizationsHeadedBy(user));
    }
}
