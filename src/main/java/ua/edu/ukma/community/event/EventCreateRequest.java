package ua.edu.ukma.community.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

@Schema(description = "Подія, яку голова створює вручну; публікується одразу")
public record EventCreateRequest(
        @Schema(description = "Назва", example = "Турнір з бравл старсу",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Назва обов'язкова")
        @Size(min = EventRules.TITLE_MIN, max = EventRules.TITLE_MAX, message = "Назва: від 3 до 200 символів")
        String title,

        @Schema(description = "Опис", example = "Жоско рубитимемося в бравл ШД")
        @Size(max = EventRules.DESCRIPTION_MAX, message = "Опис: не довше 5000 символів")
        String description,

        @Schema(description = "Дата й час початку (ISO 8601 зі зсувом)", example = "2026-10-21T18:00:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Час початку обов'язковий")
        @Future(message = "Подія має починатися в майбутньому")
        OffsetDateTime startsAt,

        @Schema(description = "Місце проведення", example = "НаУКМА, фідо серверна")
        @Size(max = EventRules.LOCATION_MAX, message = "Місце: не довше 200 символів")
        String location,

        @Schema(description = "Посилання на анонс", example = "https://www.instagram.com/p/C1a2b3d4/")
        @Size(max = EventRules.URL_MAX, message = "Посилання: не довше 2048 символів")
        @Pattern(regexp = EventRules.URL_REGEX, message = EventRules.URL_MESSAGE)
        String originalUrl
) {
}
