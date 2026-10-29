package ua.edu.ukma.community.event;

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


@Repository
@Profile("!inmemory")
public class JdbcEventRepository implements EventRepository {

    private static final String COLUMNS = """
            id, organization_id, title, description, starts_at, location, original_url, image_url, source_id, \
            external_post_id, status, verified, created_at, updated_at""";
    private static final String UPSERT = "INSERT INTO event (" + COLUMNS + ") ";

    private final JdbcClient jdbc;
    private final RowMapper<Event> mapper;

    public JdbcEventRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        ZoneId zone = clock.getZone();
        this.mapper = (rs, rowNum) -> new Event(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                inZone(rs.getObject("starts_at", OffsetDateTime.class), zone),
                rs.getString("location"),
                rs.getString("original_url"),
                rs.getString("image_url"),
                rs.getObject("source_id", UUID.class),
                rs.getString("external_post_id"),
                EventStatus.valueOf(rs.getString("status")),
                rs.getBoolean("verified"),
                inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                inZone(rs.getObject("updated_at", OffsetDateTime.class), zone));
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event WHERE id = ?")
                .param(id)
                .query(mapper)
                .optional();
    }

    @Override
    public List<Event> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event")
                .query(mapper)
                .list();
    }

    @Override
    public List<Event> findByOrganizationId(UUID organizationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event WHERE organization_id = ?")
                .param(organizationId)
                .query(mapper)
                .list();
    }

    @Override
    public Optional<Event> findBySourcePost(UUID sourceId, String externalPostId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM event WHERE source_id = ? AND external_post_id = ?")
                .param(sourceId)
                .param(externalPostId)
                .query(mapper)
                .optional();
    }

    @Override
    public Event save(Event e) {
        jdbc.sql(UPSERT + """
                        VALUES (:id, :organizationId, :title, :description, :startsAt, :location, :originalUrl,
                                :imageUrl, :sourceId, :externalPostId, :status, :verified, :createdAt, :updatedAt)
                        ON CONFLICT (id) DO UPDATE SET
                            title = EXCLUDED.title,
                            description = EXCLUDED.description,
                            starts_at = EXCLUDED.starts_at,
                            location = EXCLUDED.location,
                            original_url = EXCLUDED.original_url,
                            image_url = EXCLUDED.image_url,
                            status = EXCLUDED.status,
                            verified = EXCLUDED.verified,
                            updated_at = EXCLUDED.updated_at""")
                .param("id", e.id())
                .param("organizationId", e.organizationId())
                .param("title", e.title())
                .param("description", e.description())
                .param("startsAt", e.startsAt())
                .param("location", e.location())
                .param("originalUrl", e.originalUrl())
                .param("imageUrl", e.imageUrl())
                .param("sourceId", e.sourceId())
                .param("externalPostId", e.externalPostId())
                .param("status", e.status().name())
                .param("verified", e.verified())
                .param("createdAt", e.createdAt())
                .param("updatedAt", e.updatedAt())
                .update();
        return e;
    }

    @Override
    public void deleteByOrganizationId(UUID organizationId) {
        jdbc.sql("DELETE FROM event WHERE organization_id = ?")
                .param(organizationId)
                .update();
    }

    private static OffsetDateTime inZone(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }
}
