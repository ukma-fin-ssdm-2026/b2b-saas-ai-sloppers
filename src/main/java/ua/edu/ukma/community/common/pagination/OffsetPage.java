package ua.edu.ukma.community.common.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Сторінка результатів, пагінація зі зсувом (offset)")
public record OffsetPage<T>(
        List<T> data,
        OffsetPagination pagination
) {

    @Schema(description = "Метадані пагінації зі зсувом")
    public record OffsetPagination(
            @Schema(example = "20") int limit,
            @Schema(example = "0") int offset,
            @Schema(description = "Загальна кількість елементів, що відповідають фільтрам", example = "42") long total,
            @Schema(description = "Чи є ще елементи після цієї сторінки", example = "true") boolean hasMore
    ) {
    }

    public static <T> OffsetPage<T> of(List<T> all, int limit, int offset) {
        int from = Math.min(offset, all.size());
        int to = (int) Math.min((long) from + limit, all.size());
        return new OffsetPage<>(List.copyOf(all.subList(from, to)),
                new OffsetPagination(limit, offset, all.size(), to < all.size()));
    }
}
