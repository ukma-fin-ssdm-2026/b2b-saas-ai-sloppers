package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public final class AuthRequests {

    private AuthRequests() {
    }

    @Schema(name = "RegisterRequest", description = "Реєстрація з університетською поштою")
    public record Register(
            @Schema(example = "student@ukma.edu.ua")
            @NotBlank(message = "Пошта обов'язкова")
            @Email(message = "Некоректна адреса пошти")
            @Size(max = 320, message = "Пошта: не довше 320 символів")
            String email,

            @Schema(example = "correct-horse-battery", minLength = 8, maxLength = 72)
            @NotBlank(message = "Пароль обов'язковий")
            @Size(min = 8, max = 72, message = "Пароль: від 8 до 72 символів")
            String password
    ) {
        @Override
        public String toString() {
            return "Register[email=" + email + "]";
        }
    }

    @Schema(name = "VerifyRequest", description = "Підтвердження пошти кодом з листа")
    public record Verify(
            @Schema(example = "student@ukma.edu.ua")
            @NotBlank(message = "Пошта обов'язкова")
            String email,

            @Schema(example = "042517")
            @NotBlank(message = "Код обов'язковий")
            @Pattern(regexp = "\\s*\\d{6}\\s*", message = "Код: 6 цифр")
            String code
    ) {
    }

    @Schema(name = "ResendCodeRequest", description = "Запит нового коду підтвердження")
    public record ResendCode(
            @Schema(example = "student@ukma.edu.ua")
            @NotBlank(message = "Пошта обов'язкова")
            String email
    ) {
    }

    @Schema(name = "LoginRequest", description = "Вхід за поштою й паролем")
    public record Login(
            @Schema(example = "student@ukma.edu.ua")
            @NotBlank(message = "Пошта обов'язкова")
            String email,

            @Schema(example = "correct-horse-battery")
            @NotBlank(message = "Пароль обов'язковий")
            String password
    ) {
        @Override
        public String toString() {
            return "Login[email=" + email + "]";
        }
    }

    @Schema(name = "AppointHeadRequest", description = "Призначити голову організації")
    public record AppointHead(
            @Schema(description = "Пошта зареєстрованого користувача з підтвердженою поштою",
                    example = "head@ukma.edu.ua")
            @NotBlank(message = "Пошта обов'язкова")
            String email,

            @Schema(description = "Якщо задано — цього голову буде знято в тій самій операції (лише Global Admin)")
            java.util.UUID replacesUserId
    ) {
    }
}
