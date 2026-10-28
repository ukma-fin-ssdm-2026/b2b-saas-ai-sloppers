package ua.edu.ukma.community.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Користувач у відповідях API. Хеш пароля й код підтвердження назовні не віддаються.
 */
@Schema(description = "Користувач")
public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(example = "student@ukma.edu.ua", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @Schema(example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status,

        @Schema(description = "Чи має роль Global Admin", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean globalAdmin,

        @Schema(description = "Організації, де користувач голова", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UUID> headOf
) {

    public static UserResponse from(User user, List<UUID> headOf) {
        return new UserResponse(user.id(), user.email(), user.status(), user.globalAdmin(), headOf);
    }
}
