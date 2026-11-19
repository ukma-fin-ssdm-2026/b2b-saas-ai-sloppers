package ua.edu.ukma.community.board;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Оголошення у відповіді API; контакт уже відфільтровано {@link AdvertView}. */
@Schema(description = "Оголошення на дошці")
public record AdvertResponse(
        @Schema(description = "Ідентифікатор", example = "b1000000-0000-4000-8000-000000000001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Тип", example = "TEAM", requiredMode = Schema.RequiredMode.REQUIRED)
        AdvertType type,

        @Schema(description = "Заголовок", example = "Шукаю бекендера на хакатон",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Опис", example = "Команда з трьох людей, бракує Java/Spring.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String description,

        @Schema(description = "Кількість місць", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int slots,

        @Schema(description = "Дедлайн (ISO 8601)", example = "2026-11-20T18:00:00+02:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime deadline,

        @Schema(description = "ACTIVE або CLOSED; оголошення з дедлайном, що минув, — CLOSED",
                example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        AdvertStatus status,

        @Schema(description = "Контакт автора. null для всіх, крім автора й підтверджених відгукувачів (FR-049)",
                example = "@hackathon_seeker", nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String contact,

        @Schema(description = "Чи ви автор оголошення", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean own,

        @Schema(description = "Ім'я та прізвище автора; null, якщо автор їх не задав (FR-069)",
                example = "Теодора Гречко", nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String authorName,

        @Schema(description = "Статус вашого відгуку на це оголошення; null, якщо ви не відгукувалися",
                example = "PENDING", nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        ReplyStatus myReplyStatus,

        @Schema(description = "Час створення (ISO 8601)", example = "2026-11-16T12:00:00+02:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt
) {

    public static AdvertResponse from(AdvertView view, String authorName) {
        Advert a = view.advert();
        return new AdvertResponse(a.id(), a.type(), a.title(), a.description(), a.slots(), a.deadline(),
                view.status(), view.visibleContact(), view.own(), authorName, view.myReplyStatus(), a.createdAt());
    }
}
