package ua.edu.ukma.community.moderation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Запис про блокування (FR-054). */
@Schema(description = "Блокування")
public record BanResponse(
        UUID id,
        UUID userId,
        @Schema(description = "Хто заблокував; порожнє — автоматичне блокування (FR-060)") UUID bannedBy,
        @Schema(description = "Чи заблокувала система автоматично") boolean automatic,
        String reason,
        @Schema(description = "Коли спадає; порожнє — безстроково") OffsetDateTime expiresAt,
        @Schema(description = "Чи діє блокування зараз") boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime liftedAt,
        @Schema(description = "Хто зняв; порожнє — строк вийшов") UUID liftedBy
) {

    public static BanResponse from(Ban ban, OffsetDateTime now) {
        return new BanResponse(ban.id(), ban.userId(), ban.bannedBy(), ban.isAutomatic(), ban.reason(),
                ban.expiresAt(), ban.isActive(now), ban.createdAt(), ban.liftedAt(), ban.liftedBy());
    }
}
