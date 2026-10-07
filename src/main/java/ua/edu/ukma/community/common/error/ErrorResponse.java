package ua.edu.ukma.community.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Єдина обгортка для всіх помилок API")
public record ErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ApiError error
) {
}
