package ua.edu.ukma.community.rating;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Середня оцінка й кількість оцінок (FR-062, FR-064). Середнє округлюється до десятих: саме так його
 * показують, і клієнту не треба знати про {@code 4.333333}.
 */
@Schema(description = "Рейтинг: середня оцінка 1–5 і кількість оцінок")
public record RatingSummary(
        @Schema(description = "Середня оцінка, округлена до десятих; null, якщо оцінок ще немає",
                example = "4.3", nullable = true)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Double average,

        @Schema(description = "Кількість оцінок", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        long count
) {

    public static final RatingSummary NONE = new RatingSummary(null, 0);

    private static final double TENTHS = 10.0;

    /** @param sum сума всіх оцінок */
    public static RatingSummary of(long count, long sum) {
        if (count == 0) {
            return NONE;
        }
        return new RatingSummary(Math.round(sum * TENTHS / count) / TENTHS, count);
    }
}
