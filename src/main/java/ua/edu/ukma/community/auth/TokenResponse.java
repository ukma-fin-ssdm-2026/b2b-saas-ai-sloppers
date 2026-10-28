package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "JWT для заголовка Authorization: Bearer <accessToken>")
public record TokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenType,

        @Schema(description = "Через скільки секунд токен стане недійсним", example = "3600",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long expiresIn
) {
}
