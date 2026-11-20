package ua.edu.ukma.community.organization;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import ua.edu.ukma.community.rating.RatingSummary;

/**
 * Організація в каталозі й профілі разом з рейтингом (FR-064). Поля організації розгорнуті на верхній
 * рівень ({@link JsonUnwrapped}), тож для клієнта це той самий об'єкт, лише з новим полем {@code rating}.
 */
@Schema(description = "Студентська організація з рейтингом")
public record OrganizationResponse(
        @JsonUnwrapped
        Organization organization,

        @Schema(description = "Рейтинг за оцінками користувачів (FR-064)", requiredMode = Schema.RequiredMode.REQUIRED)
        RatingSummary rating
) {
}
