package ua.edu.ukma.community.board;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Відгук у відповіді API. */
@Schema(description = "Відгук на оголошення")
public record ReplyResponse(
        @Schema(description = "Ідентифікатор відгуку", example = "c1000000-0000-4000-8000-000000000001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Оголошення", example = "b1000000-0000-4000-8000-000000000001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID advertId,

        @Schema(description = "Хто відгукнувся", example = "0f8d6a3e-2b1c-4d5e-9f0a-1b2c3d4e5f60",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,

        @Schema(description = "Повідомлення автору", example = "Привіт! Пишу на Spring третій рік, готовий.")
        String message,

        @Schema(description = "Статус", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
        ReplyStatus status,

        @Schema(description = "Час подання (ISO 8601)", example = "2026-11-16T12:30:00+02:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(description = "Коли автор підтвердив/відхилив або відгук відкликано",
                example = "2026-11-16T13:00:00+02:00")
        OffsetDateTime decidedAt
) {

    public static ReplyResponse from(Reply reply) {
        return new ReplyResponse(reply.id(), reply.advertId(), reply.userId(), reply.message(), reply.status(),
                reply.createdAt(), reply.decidedAt());
    }
}
