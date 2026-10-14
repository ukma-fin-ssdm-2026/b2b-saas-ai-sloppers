package ua.edu.ukma.community.common.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Сторінка результатів, пагінація зі зсувом (offset)")
public record OffsetPage<T>(
        List<T> data,
        OffsetPagination pagination
) {

    public OffsetPage {
        data = List.copyOf(data);
    }

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
        return new OffsetPage<>(all.subList(from, to),
                new OffsetPagination(limit, offset, all.size(), to < all.size()));
    }

    public <R> OffsetPage<R> map(Function<? super T, ? extends R> mapper) {
        return new OffsetPage<>(data.stream().<R>map(mapper).toList(), pagination);
    }
}
