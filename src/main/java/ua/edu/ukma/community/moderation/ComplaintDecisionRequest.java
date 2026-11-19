package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Рішення Global Admin щодо скарги (FR-057). */
@Schema(description = "Рішення щодо скарги")
public record ComplaintDecisionRequest(

        @Schema(description = "Коментар модератора для журналу", example = "Реклама, оголошення прибрано")
        @Size(max = 500, message = "Коментар не довший за 500 символів")
        String resolution
) {
}
