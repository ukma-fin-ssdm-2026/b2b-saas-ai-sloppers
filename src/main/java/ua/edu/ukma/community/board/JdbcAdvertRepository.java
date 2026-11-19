package ua.edu.ukma.community.board;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Оголошення в PostgreSQL (міграція V6). */
@Repository
@Profile("!inmemory")
public class JdbcAdvertRepository implements AdvertRepository {

    private static final String COLUMNS = """
            id, author_id, type, title, description, slots, deadline, contact, status, created_at, updated_at""";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM advert ";

    private final JdbcClient jdbc;
    private final RowMapper<Advert> mapper;

    public JdbcAdvertRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        ZoneId zone = clock.getZone();
        this.mapper = (rs, rowNum) -> new Advert(
                rs.getObject("id", UUID.class),
                rs.getObject("author_id", UUID.class),
                AdvertType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("slots"),
                inZone(rs.getObject("deadline", OffsetDateTime.class), zone),
                rs.getString("contact"),
                AdvertStatus.valueOf(rs.getString("status")),
                inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                inZone(rs.getObject("updated_at", OffsetDateTime.class), zone));
    }

    @Override
    public Optional<Advert> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = ?")
                .param(id)
                .query(mapper)
                .optional();
    }

    @Override
    public Optional<Advert> lock(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = ? FOR UPDATE")
                .param(id)
                .query(mapper)
                .optional();
    }

    @Override
    public List<Advert> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + "WHERE id IN (:ids)")
                .param("ids", ids)
                .query(mapper)
                .list();
    }

    /** Фільтри в SQL під індекс {@code advert_status_deadline_idx}; сортування — у сервісі, як і для подій. */
    @Override
    public List<Advert> findOpen(AdvertType type, OffsetDateTime now) {
        return jdbc.sql(SELECT + """
                        WHERE status = 'ACTIVE'
                          AND deadline > :now
                          AND (CAST(:type AS varchar) IS NULL OR type = :type)""")
                .param("now", now)
                .param("type", type == null ? null : type.name())
                .query(mapper)
                .list();
    }

    @Override
    public List<Advert> findByAuthor(UUID authorId) {
        return jdbc.sql(SELECT + "WHERE author_id = ? AND status <> 'DELETED'")
                .param(authorId)
                .query(mapper)
                .list();
    }

    /** Разом із видаленими (FR-059): ліміт не має обходитися створенням і видаленням оголошення. */
    @Override
    public long countByAuthorSince(UUID authorId, OffsetDateTime since) {
        return jdbc.sql("SELECT count(*) FROM advert WHERE author_id = ? AND created_at > ?")
                .param(authorId)
                .param(since)
                .query(Long.class)
                .single();
    }

    @Override
    public Advert save(Advert a) {
        jdbc.sql("""
                        INSERT INTO advert (id, author_id, type, title, description, slots, deadline, contact,
                                            status, created_at, updated_at)
                        VALUES (:id, :authorId, :type, :title, :description, :slots, :deadline, :contact,
                                :status, :createdAt, :updatedAt)
                        ON CONFLICT (id) DO UPDATE SET
                            status = EXCLUDED.status,
                            updated_at = EXCLUDED.updated_at""")
                .param("id", a.id())
                .param("authorId", a.authorId())
                .param("type", a.type().name())
                .param("title", a.title())
                .param("description", a.description())
                .param("slots", a.slots())
                .param("deadline", a.deadline())
                .param("contact", a.contact())
                .param("status", a.status().name())
                .param("createdAt", a.createdAt())
                .param("updatedAt", a.updatedAt())
                .update();
        return a;
    }

    @Override
    public int closeExpired(OffsetDateTime now) {
        return jdbc.sql("""
                        UPDATE advert SET status = 'CLOSED', updated_at = :now
                        WHERE status = 'ACTIVE' AND deadline <= :now""")
                .param("now", now)
                .update();
    }

    private static OffsetDateTime inZone(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }
}
