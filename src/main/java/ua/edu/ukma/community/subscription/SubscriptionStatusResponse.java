package ua.edu.ukma.community.subscription;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Стан підписки поточного користувача на організацію")
public record SubscriptionStatusResponse(
        @Schema(description = "Чи підписаний користувач", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean subscribed
) {
}
