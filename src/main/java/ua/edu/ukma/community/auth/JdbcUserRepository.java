package ua.edu.ukma.community.auth;

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
import java.util.Set;
import java.util.UUID;


@Repository
@Profile("!inmemory")
public class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS = """
            id, email, password_hash, status, global_admin, verification_code_hash, verification_expires_at, \
            verification_attempts, verification_sent_at, created_at, updated_at, first_name, last_name""";
    private static final String UPSERT = "INSERT INTO app_user (" + COLUMNS + ") ";

    private final JdbcClient jdbc;
    private final RowMapper<User> mapper;

    public JdbcUserRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        ZoneId zone = clock.getZone();
        this.mapper = (rs, rowNum) -> {
            String codeHash = rs.getString("verification_code_hash");
            VerificationCode code = codeHash == null ? null : new VerificationCode(codeHash,
                    inZone(rs.getObject("verification_expires_at", OffsetDateTime.class), zone),
                    rs.getInt("verification_attempts"),
                    inZone(rs.getObject("verification_sent_at", OffsetDateTime.class), zone));
            return new User(
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    UserStatus.valueOf(rs.getString("status")),
                    rs.getBoolean("global_admin"),
                    code,
                    inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                    inZone(rs.getObject("updated_at", OffsetDateTime.class), zone),
                    rs.getString("first_name"),
                    rs.getString("last_name"));
        };
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = ?")
                .param(id)
                .query(mapper)
                .optional();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = ?")
                .param(email)
                .query(mapper)
                .optional();
    }

    @Override
    public List<User> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id IN (:ids)")
                .param("ids", Set.copyOf(ids))
                .query(mapper)
                .list();
    }

    @Override
    public User save(User u) {
        VerificationCode code = u.verification();
        jdbc.sql(UPSERT + """
                        VALUES (:id, :email, :passwordHash, :status, :globalAdmin, :codeHash, :codeExpiresAt,
                                :codeAttempts, :codeSentAt, :createdAt, :updatedAt, :firstName, :lastName)
                        ON CONFLICT (id) DO UPDATE SET
                            email = EXCLUDED.email,
                            password_hash = EXCLUDED.password_hash,
                            status = EXCLUDED.status,
                            global_admin = EXCLUDED.global_admin,
                            verification_code_hash = EXCLUDED.verification_code_hash,
                            verification_expires_at = EXCLUDED.verification_expires_at,
                            verification_attempts = EXCLUDED.verification_attempts,
                            verification_sent_at = EXCLUDED.verification_sent_at,
                            updated_at = EXCLUDED.updated_at,
                            first_name = EXCLUDED.first_name,
                            last_name = EXCLUDED.last_name""")
                .param("id", u.id())
                .param("email", u.email())
                .param("passwordHash", u.passwordHash())
                .param("status", u.status().name())
                .param("globalAdmin", u.globalAdmin())
                .param("codeHash", code == null ? null : code.codeHash())
                .param("codeExpiresAt", code == null ? null : code.expiresAt())
                .param("codeAttempts", code == null ? null : code.attemptsLeft())
                .param("codeSentAt", code == null ? null : code.sentAt())
                .param("createdAt", u.createdAt())
                .param("updatedAt", u.updatedAt())
                .param("firstName", u.firstName())
                .param("lastName", u.lastName())
                .update();
        return u;
    }

    private static OffsetDateTime inZone(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }
}
