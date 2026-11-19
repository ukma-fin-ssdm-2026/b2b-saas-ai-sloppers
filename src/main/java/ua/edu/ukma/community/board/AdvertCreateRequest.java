package ua.edu.ukma.community.board;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Тіло {@code POST /adverts}. */
@Schema(description = "Нове оголошення на дошці (FR-043, FR-044)")
public record AdvertCreateRequest(
        @Schema(description = "TEAM — пошук команди, LEISURE — компанія для дозвілля", example = "TEAM",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Тип обов'язковий: TEAM або LEISURE")
        AdvertType type,

        @Schema(description = "Заголовок", example = "Шукаю бекендера на хакатон",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Заголовок обов'язковий")
        @Size(min = BoardRules.TITLE_MIN, max = BoardRules.TITLE_MAX, message = "Заголовок: від 3 до 200 символів")
        String title,

        @Schema(description = "Опис", example = "Команда з трьох людей, бракує Java/Spring. Хакатон 22–23 листопада.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Опис обов'язковий")
        @Size(max = BoardRules.DESCRIPTION_MAX, message = "Опис: не довше 2000 символів")
        String description,

        @Schema(description = "Скільки людей шукаєте, 1–50", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Кількість місць обов'язкова")
        @Min(value = BoardRules.SLOTS_MIN, message = "Кількість місць: щонайменше 1")
        @Max(value = BoardRules.SLOTS_MAX, message = "Кількість місць: не більше 50")
        Integer slots,

        @Schema(description = "До коли збираєте людей (ISO 8601 зі зсувом); після нього оголошення закриється",
                example = "2026-11-20T18:00:00+02:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Дедлайн обов'язковий")
        @Future(message = "Дедлайн має бути в майбутньому")
        OffsetDateTime deadline,

        @Schema(description = "Як з вами зв'язатися: Telegram, телефон або email. Бачать лише ви й ті, "
                + "чий відгук ви підтвердили (FR-049)", example = "@hackathon_seeker",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Контакт обов'язковий: Telegram, телефон або email")
        @Size(max = BoardRules.CONTACT_MAX, message = "Контакт: не довше 200 символів")
        @Pattern(regexp = BoardRules.CONTACT_REGEX, message = BoardRules.CONTACT_MESSAGE)
        String contact
) {
}
