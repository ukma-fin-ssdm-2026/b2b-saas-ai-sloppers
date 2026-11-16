package ua.edu.ukma.community.board;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Тіло {@code POST /adverts/{advertId}/replies}. */
@Schema(description = "Відгук на оголошення (FR-046)")
public record ReplyCreateRequest(
        @Schema(description = "Коротке повідомлення автору", example = "Привіт! Пишу на Spring третій рік, готовий.")
        @Size(max = BoardRules.MESSAGE_MAX, message = "Повідомлення: не довше 500 символів")
        String message
) {
}
