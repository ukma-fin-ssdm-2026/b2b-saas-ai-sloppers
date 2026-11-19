package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Блокування користувача (FR-054). */
@Schema(description = "Блокування користувача")
public record BanRequest(

        @Schema(description = "Причина, яку побачить журнал модерації", example = "Спам на дошці оголошень",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Причина обов'язкова")
        @Size(max = 500, message = "Причина не довша за 500 символів")
        String reason,

        @Schema(description = "Коли блокування спадає саме; порожнє — безстроково",
                example = "2026-12-31T23:59:00+02:00")
        OffsetDateTime expiresAt
) {
}
