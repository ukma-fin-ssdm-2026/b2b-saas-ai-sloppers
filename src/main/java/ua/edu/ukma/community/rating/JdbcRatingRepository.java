package ua.edu.ukma.community.rating;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Оцінки в PostgreSQL: таблиці {@code author_rating} і {@code organization_rating} (міграція V9). */
@Repository
@Profile("!inmemory")
public class JdbcRatingRepository implements RatingRepository {

    private final JdbcClient jdbc;
    private final ZoneId zone;

    public JdbcRatingRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.zone = clock.getZone();
    }

    private OffsetDateTime inZone(OffsetDateTime value) {
        return value.atZoneSameInstant(zone).toOffsetDateTime();
    }

    @Override
    public boolean insertAuthorRating(AuthorRating r) {
        return jdbc.sql("INSERT INTO author_rating (id, rater_id, author_id, advert_id, score, created_at) "
                        + "VALUES (:id, :raterId, :authorId, :advertId, :score, :createdAt) "
                        + "ON CONFLICT (rater_id, advert_id) DO NOTHING")
                .param("id", r.id())
                .param("raterId", r.raterId())
                .param("authorId", r.authorId())
                .param("advertId", r.advertId())
                .param("score", r.score())
                .param("createdAt", r.createdAt())
                .update() == 1;
    }

    @Override
    public Map<UUID, Tally> authorTallies(Collection<UUID> authorIds) {
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        return tallies("SELECT author_id AS id, count(*) AS n, sum(score) AS total FROM author_rating "
                + "WHERE author_id IN (:ids) GROUP BY author_id", authorIds);
    }

    @Override
    public Map<UUID, Integer> scoresGivenBy(UUID raterId, Collection<UUID> advertIds) {
        if (advertIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> scores = new HashMap<>();
        jdbc.sql("SELECT advert_id, score FROM author_rating WHERE rater_id = :raterId AND advert_id IN (:ids)")
                .param("raterId", raterId)
                .param("ids", advertIds)
                .query(rs -> {
                    scores.put(rs.getObject("advert_id", UUID.class), rs.getInt("score"));
                });
        return scores;
    }

    /** Повторна оцінка змінює бал і час зміни; час першої оцінки лишається. */
    @Override
    public void upsertOrganizationRating(OrganizationRating r) {
        jdbc.sql("INSERT INTO organization_rating (user_id, organization_id, score, created_at, updated_at) "
                        + "VALUES (:userId, :organizationId, :score, :createdAt, :updatedAt) "
                        + "ON CONFLICT (user_id, organization_id) DO UPDATE SET score = EXCLUDED.score, "
                        + "updated_at = EXCLUDED.updated_at")
                .param("userId", r.userId())
                .param("organizationId", r.organizationId())
                .param("score", r.score())
                .param("createdAt", r.createdAt())
                .param("updatedAt", r.updatedAt())
                .update();
    }

    @Override
    public Optional<OrganizationRating> findOrganizationRating(UUID userId, UUID organizationId) {
        return jdbc.sql("SELECT user_id, organization_id, score, created_at, updated_at FROM organization_rating "
                        + "WHERE user_id = ? AND organization_id = ?")
                .param(userId)
                .param(organizationId)
                .query((rs, rowNum) -> new OrganizationRating(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getInt("score"),
                        inZone(rs.getObject("created_at", OffsetDateTime.class)),
                        inZone(rs.getObject("updated_at", OffsetDateTime.class))))
                .optional();
    }

    @Override
    public Map<UUID, Tally> organizationTallies(Collection<UUID> organizationIds) {
        if (organizationIds.isEmpty()) {
            return Map.of();
        }
        return tallies("SELECT organization_id AS id, count(*) AS n, sum(score) AS total FROM organization_rating "
                + "WHERE organization_id IN (:ids) GROUP BY organization_id", organizationIds);
    }

    private Map<UUID, Tally> tallies(String sql, Collection<UUID> ids) {
        Map<UUID, Tally> result = new HashMap<>();
        jdbc.sql(sql)
                .param("ids", ids)
                .query(rs -> {
                    result.put(rs.getObject("id", UUID.class), new Tally(rs.getLong("n"), rs.getLong("total")));
                });
        return result;
    }
}
