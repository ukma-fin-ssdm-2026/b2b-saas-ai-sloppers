package ua.edu.ukma.community.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Порушення правила валідації для одного поля або параметра")
public record FieldViolation(
        @Schema(description = "Назва поля тіла запиту або query/path-параметра", example = "slug")
        String field,

        @Schema(description = "Що саме не так", example = "Slug: 3–50 символів, лише a-z, 0-9 і дефіс")
        String message,

        @Schema(description = "Передане значення (може бути відсутнє; для пароля й коду підтвердження не повертається)",
                example = "Debate Club!")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Object providedValue
) {
}
