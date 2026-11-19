package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;


@Schema(description = "Зміна ролі голови: хто, кому, що, коли (FR-013)")
public record RoleChange(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID organizationId,

        @Schema(description = "Кому змінили роль", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID targetUserId,

        @Schema(example = "head@ukma.edu.ua", requiredMode = Schema.RequiredMode.REQUIRED)
        String targetEmail,

        @Schema(description = "Ім'я та прізвище того, кому змінили роль (FR-069)", example = "Теодора Гречко",
                nullable = true)
        String targetName,

        @Schema(description = "Хто змінив; null, якщо цього користувача вже видалено")
        UUID actorUserId,

        @Schema(example = "admin@ukma.edu.ua")
        String actorEmail,

        @Schema(description = "Ім'я та прізвище того, хто змінив (FR-069)", example = "Олена Коваль", nullable = true)
        String actorName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RoleAction action,

        @Schema(description = "Коли (ISO 8601)", requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt
) {

    public enum RoleAction {
        APPOINTED,
        REMOVED
    }
}
