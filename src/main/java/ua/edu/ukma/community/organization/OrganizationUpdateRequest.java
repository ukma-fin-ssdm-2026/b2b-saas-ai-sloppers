package ua.edu.ukma.community.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


@Schema(description = "Часткове оновлення організації: передавайте лише поля, які змінюються; "
        + "відсутнє або null поле не змінюється")
public record OrganizationUpdateRequest(
        @Schema(description = "Назва", example = "Debate Club NaUKMA")
        @Size(min = 3, max = 100, message = "Назва: від 3 до 100 символів")
        String name,

        @Schema(description = "Унікальний slug", example = "debate-club")
        @Size(min = 3, max = 50, message = "Slug: від 3 до 50 символів")
        @Pattern(regexp = OrganizationRules.SLUG_REGEX, message = OrganizationRules.SLUG_MESSAGE)
        String slug,

        @Schema(description = "Опис діяльності")
        @Size(max = 2000, message = "Опис: не довше 2000 символів")
        String description,

        @Schema(description = "Instagram-акаунт без @", example = "ukma.debate")
        @Pattern(regexp = OrganizationRules.INSTAGRAM_REGEX, message = OrganizationRules.INSTAGRAM_MESSAGE)
        String instagramHandle,

        @Schema(description = "Контактна пошта", example = "debate@ukma.edu.ua")
        @Email(message = "Некоректна адреса пошти")
        String contactEmail,

        @Schema(description = "Увімкнути або вимкнути автопублікацію спаршених подій (FR-029)", example = "true")
        Boolean autoPublish
) {
}
