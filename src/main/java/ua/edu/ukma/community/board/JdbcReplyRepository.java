package ua.edu.ukma.community.board;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Відгуки в PostgreSQL, таблиця {@code advert_response} (міграція V6). */
@Repository
@Profile("!inmemory")
public class JdbcReplyRepository implements ReplyRepository {

    private static final String SELECT =
            "SELECT id, advert_id, user_id, message, status, created_at, decided_at FROM advert_response ";

    private final JdbcClient jdbc;
    private final RowMapper<Reply> mapper;

    public JdbcReplyRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        ZoneId zone = clock.getZone();
        this.mapper = (rs, rowNum) -> new Reply(
                rs.getObject("id", UUID.class),
                rs.getObject("advert_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("message"),
                ReplyStatus.valueOf(rs.getString("status")),
                inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                inZone(rs.getObject("decided_at", OffsetDateTime.class), zone));
    }

    @Override
    public Optional<Reply> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = ?")
                .param(id)
                .query(mapper)
                .optional();
    }

    @Override
    public Optional<Reply> findByAdvertAndUser(UUID advertId, UUID userId) {
        return jdbc.sql(SELECT + "WHERE advert_id = ? AND user_id = ?")
                .param(advertId)
                .param(userId)
                .query(mapper)
                .optional();
    }

    @Override
    public List<Reply> findByAdvert(UUID advertId) {
        return jdbc.sql(SELECT + "WHERE advert_id = ?")
                .param(advertId)
                .query(mapper)
                .list();
    }

    @Override
    public List<Reply> findByUser(UUID userId) {
        return jdbc.sql(SELECT + "WHERE user_id = ?")
                .param(userId)
                .query(mapper)
                .list();
    }

    @Override
    public long countConfirmed(UUID advertId) {
        return jdbc.sql("SELECT count(*) FROM advert_response WHERE advert_id = ? AND status = 'CONFIRMED'")
                .param(advertId)
                .query(Long.class)
                .single();
    }

    @Override
    public long countByUserSince(UUID userId, OffsetDateTime since) {
        return jdbc.sql("SELECT count(*) FROM advert_response WHERE user_id = ? AND created_at > ?")
                .param(userId)
                .param(since)
                .query(Long.class)
                .single();
    }

    @Override
    public Reply save(Reply r) {
        jdbc.sql("""
                        INSERT INTO advert_response (id, advert_id, user_id, message, status, created_at, decided_at)
                        VALUES (:id, :advertId, :userId, :message, :status, :createdAt, :decidedAt)
                        ON CONFLICT (id) DO UPDATE SET
                            message = EXCLUDED.message,
                            status = EXCLUDED.status,
                            created_at = EXCLUDED.created_at,
                            decided_at = EXCLUDED.decided_at""")
                .param("id", r.id())
                .param("advertId", r.advertId())
                .param("userId", r.userId())
                .param("message", r.message())
                .param("status", r.status().name())
                .param("createdAt", r.createdAt())
                .param("decidedAt", r.decidedAt())
                .update();
        return r;
    }

    private static OffsetDateTime inZone(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }
}
