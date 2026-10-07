package ua.edu.ukma.community.common.error;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Тіло помилки: код для програми, повідомлення для людини, деталі по полях")
public record ApiError(
        @Schema(description = "Машиночитаний код помилки", example = "VALIDATION_ERROR",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ErrorCode code,

        @Schema(description = "Зрозуміле людині повідомлення", example = "Запит містить невалідні поля",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @ArraySchema(arraySchema = @Schema(description = "Порушення по конкретних полях/параметрах; порожній, якщо не застосовно"))
        List<FieldViolation> details,

        @Schema(description = "Кореляційний ідентифікатор запиту (дублюється в заголовку X-Request-Id і в логах)",
                example = "req_7f3a9c21d04b4e18", requiredMode = Schema.RequiredMode.REQUIRED)
        String requestId,

        @Schema(description = "Посилання на опис цього коду помилки",
                example = "https://ukma-fin-ssdm-2026.github.io/YOUR-REPO/api/errors#validation-error")
        String documentation
) {
}
