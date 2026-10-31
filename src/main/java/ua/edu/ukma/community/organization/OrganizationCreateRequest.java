package ua.edu.ukma.community.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Дані для створення організації (лише суперадмін)")
public record OrganizationCreateRequest(
        @Schema(description = "Назва", example = "FIDo")
        @NotBlank(message = "Назва обов'язкова")
        @Size(min = 3, max = 100, message = "Назва: від 3 до 100 символів")
        String name,

        @Schema(description = "Унікальний slug: a-z, 0-9, дефіс між словами", example = "fido")
        @NotBlank(message = "Slug обов'язковий")
        @Size(min = 3, max = 50, message = "Slug: від 3 до 50 символів")
        @Pattern(regexp = OrganizationRules.SLUG_REGEX, message = OrganizationRules.SLUG_MESSAGE)
        String slug,

        @Schema(description = "Опис діяльності", example = "Студентська IT-спільнота: хакатони та лекції")
        @Size(max = 2000, message = "Опис: не довше 2000 символів")
        String description,

        @Schema(description = "Instagram-акаунт без @", example = "fido_naukma")
        @Pattern(regexp = OrganizationRules.INSTAGRAM_REGEX, message = OrganizationRules.INSTAGRAM_MESSAGE)
        String instagramHandle,

        @Schema(description = "Контактна пошта", example = "fido@ukma.edu.ua")
        @Email(message = "Некоректна адреса пошти")
        String contactEmail,

        @Schema(description = "Пошта першого голови: зареєстрований користувач з підтвердженою поштою. "
                + "Організація без голови не створюється (FR-011, FR-014)", example = "head@ukma.edu.ua",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Пошта першого голови обов'язкова")
        String headEmail
) {
}
