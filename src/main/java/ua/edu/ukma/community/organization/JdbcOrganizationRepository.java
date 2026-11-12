package ua.edu.ukma.community.organization;

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
public class JdbcOrganizationRepository implements OrganizationRepository {

    private static final String COLUMNS = """
            id, name, slug, description, instagram_handle, contact_email, logo_url, auto_publish, \
            created_at, updated_at""";
    private static final String UPSERT = "INSERT INTO organization (" + COLUMNS + ") ";

    private final JdbcClient jdbc;
    private final RowMapper<Organization> mapper;

    public JdbcOrganizationRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        ZoneId zone = clock.getZone();
        this.mapper = (rs, rowNum) -> new Organization(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                rs.getString("instagram_handle"),
                rs.getString("contact_email"),
                rs.getString("logo_url"),
                rs.getBoolean("auto_publish"),
                inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                inZone(rs.getObject("updated_at", OffsetDateTime.class), zone));
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organization WHERE id = ?")
                .param(id)
                .query(mapper)
                .optional();
    }

    @Override
    public Optional<Organization> findBySlug(String slug) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organization WHERE slug = ?")
                .param(slug)
                .query(mapper)
                .optional();
    }

    @Override
    public List<Organization> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM organization")
                .query(mapper)
                .list();
    }

    /**
     * Фільтр у SQL, щоб каталог не тягнув усю таблицю (R3). Індексу тут немає й бути не може:
     * {@code LIKE '%...%'} з ведучим шаблоном btree не використовує. Виграш у тому, що рядки
     * відсіюються в БД, а не передаються й не перетворюються в об'єкти.
     *
     * <p>Сортування лишається в сервісі: {@code String.CASE_INSENSITIVE_ORDER} і {@code ORDER BY}
     * з колацією Postgres дають різний порядок для української.
     */
    @Override
    public List<Organization> findMatching(String needle) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM organization
                         WHERE :needle = ''
                            OR lower(name) LIKE :pattern ESCAPE '!'
                            OR slug LIKE :pattern ESCAPE '!'""")
                .param("needle", needle)
                .param("pattern", "%" + escapeLike(needle) + "%")
                .query(mapper)
                .list();
    }

    /**
     * Знешкоджує символи шаблону {@code LIKE}, щоб пошук «50%» чи «a_b» означав те саме, що
     * {@code String.contains}, а не підстановку.
     */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Override
    public boolean existsBySlug(String slug, UUID excludedId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM organization
                            WHERE slug = :slug AND (CAST(:excludedId AS uuid) IS NULL OR id <> :excludedId)
                        )""")
                .param("slug", slug)
                .param("excludedId", excludedId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Organization save(Organization o) {
        jdbc.sql(UPSERT + """
                        VALUES (:id, :name, :slug, :description, :instagramHandle, :contactEmail,
                                :logoUrl, :autoPublish, :createdAt, :updatedAt)
                        ON CONFLICT (id) DO UPDATE SET
                            name = EXCLUDED.name,
                            slug = EXCLUDED.slug,
                            description = EXCLUDED.description,
                            instagram_handle = EXCLUDED.instagram_handle,
                            contact_email = EXCLUDED.contact_email,
                            logo_url = EXCLUDED.logo_url,
                            auto_publish = EXCLUDED.auto_publish,
                            updated_at = EXCLUDED.updated_at""")
                .param("id", o.id())
                .param("name", o.name())
                .param("slug", o.slug())
                .param("description", o.description())
                .param("instagramHandle", o.instagramHandle())
                .param("contactEmail", o.contactEmail())
                .param("logoUrl", o.logoUrl())
                .param("autoPublish", o.autoPublish())
                .param("createdAt", o.createdAt())
                .param("updatedAt", o.updatedAt())
                .update();
        return o;
    }

    @Override
    public boolean deleteById(UUID id) {
        return jdbc.sql("DELETE FROM organization WHERE id = ?")
                .param(id)
                .update() > 0;
    }

    private static OffsetDateTime inZone(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }
}
