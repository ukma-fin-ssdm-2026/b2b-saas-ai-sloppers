package ua.edu.ukma.community.rating;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Сховище оцінок: {@link JdbcRatingRepository} для PostgreSQL, {@link InMemoryRatingRepository} для тестів. */
public interface RatingRepository {

    /**
     * Зберігає оцінку автора, якщо цей учасник ще не оцінював автора за це оголошення. Перевірка й запис
     * атомарні ({@code ON CONFLICT DO NOTHING}), тож подвійний клік не дає ні дубля, ні 500.
     *
     * @return {@code false}, якщо оцінка вже була
     */
    boolean insertAuthorRating(AuthorRating rating);

    /** Кількість і сума оцінок кожного з авторів; авторів без оцінок у мапі немає (FR-062). */
    Map<UUID, Tally> authorTallies(Collection<UUID> authorIds);

    /** Оцінки, які {@code raterId} поставив авторам цих оголошень: advertId → оцінка. */
    Map<UUID, Integer> scoresGivenBy(UUID raterId, Collection<UUID> advertIds);

    /** Створює або змінює оцінку організації (FR-063); {@code createdAt} першої оцінки зберігається. */
    void upsertOrganizationRating(OrganizationRating rating);

    Optional<OrganizationRating> findOrganizationRating(UUID userId, UUID organizationId);

    /** Кількість і сума оцінок кожної з організацій; організацій без оцінок у мапі немає (FR-064). */
    Map<UUID, Tally> organizationTallies(Collection<UUID> organizationIds);

    /** Кількість і сума оцінок; середнє рахує {@link RatingSummary#of}. */
    record Tally(long count, long sum) {

        public RatingSummary summary() {
            return RatingSummary.of(count, sum);
        }
    }
}
