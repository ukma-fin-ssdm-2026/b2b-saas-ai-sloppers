package ua.edu.ukma.community.moderation;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ua.edu.ukma.community.auth.UserStatus;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Модерація в PostgreSQL: таблиці {@code complaint}, {@code ban}, {@code banned_word},
 * {@code filter_rejection} (міграція V8).
 *
 * <p>У базі ціль скарги — це два зовнішні ключі, {@code advert_id} і {@code response_id}, щоб цілісність
 * гарантувала сама БД. Доменна модель натомість має пару {@code targetType} + {@code targetId}, і це
 * перетворення робиться тут, у єдиному місці.
 */
@Repository
@Profile("!inmemory")
public class JdbcModerationRepository implements ModerationRepository {

    private static final String SELECT_COMPLAINT = "SELECT id, reporter_id, author_id, advert_id, response_id, "
            + "category, comment, status, resolved_by, resolution, created_at, resolved_at FROM complaint ";
    private static final String SELECT_BAN = "SELECT id, user_id, banned_by, reason, expires_at, "
            + "previous_status, created_at, lifted_at, lifted_by FROM ban ";
    private static final String SELECT_WORD = "SELECT id, word, added_by, created_at FROM banned_word ";

    private final JdbcClient jdbc;
    private final RowMapper<Complaint> complaintMapper;
    private final RowMapper<Ban> banMapper;
    private final RowMapper<BannedWord> wordMapper;

    public JdbcModerationRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        ZoneId zone = clock.getZone();
        this.complaintMapper = (rs, rowNum) -> {
            UUID advertId = rs.getObject("advert_id", UUID.class);
            UUID responseId = rs.getObject("response_id", UUID.class);
            return new Complaint(
                    rs.getObject("id", UUID.class),
                    rs.getObject("reporter_id", UUID.class),
                    rs.getObject("author_id", UUID.class),
                    advertId != null ? ContentType.ADVERT : ContentType.REPLY,
                    advertId != null ? advertId : responseId,
                    ComplaintCategory.valueOf(rs.getString("category")),
                    rs.getString("comment"),
                    ComplaintStatus.valueOf(rs.getString("status")),
                    rs.getObject("resolved_by", UUID.class),
                    rs.getString("resolution"),
                    inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                    inZone(rs.getObject("resolved_at", OffsetDateTime.class), zone));
        };
        this.banMapper = (rs, rowNum) -> new Ban(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("banned_by", UUID.class),
                rs.getString("reason"),
                inZone(rs.getObject("expires_at", OffsetDateTime.class), zone),
                UserStatus.valueOf(rs.getString("previous_status")),
                inZone(rs.getObject("created_at", OffsetDateTime.class), zone),
                inZone(rs.getObject("lifted_at", OffsetDateTime.class), zone),
                rs.getObject("lifted_by", UUID.class));
        this.wordMapper = (rs, rowNum) -> new BannedWord(
                rs.getObject("id", UUID.class),
                rs.getString("word"),
                rs.getObject("added_by", UUID.class),
                inZone(rs.getObject("created_at", OffsetDateTime.class), zone));
    }

    private static OffsetDateTime inZone(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }

    private static UUID advertId(Complaint complaint) {
        return complaint.targetType() == ContentType.ADVERT ? complaint.targetId() : null;
    }

    private static UUID responseId(Complaint complaint) {
        return complaint.targetType() == ContentType.REPLY ? complaint.targetId() : null;
    }

    // ---- скарги ----

    /** Оновлюються лише поля рішення: ціль і автор скарги після створення не змінюються. */
    @Override
    public Complaint saveComplaint(Complaint complaint) {
        jdbc.sql("INSERT INTO complaint (id, reporter_id, author_id, advert_id, response_id, category, comment, "
                        + "status, resolved_by, resolution, created_at, resolved_at) "
                        + "VALUES (:id, :reporterId, :authorId, :advertId, :responseId, :category, :comment, "
                        + ":status, :resolvedBy, :resolution, :createdAt, :resolvedAt) "
                        + "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, "
                        + "resolved_by = EXCLUDED.resolved_by, resolution = EXCLUDED.resolution, "
                        + "resolved_at = EXCLUDED.resolved_at")
                .param("id", complaint.id())
                .param("reporterId", complaint.reporterId())
                .param("authorId", complaint.authorId())
                .param("advertId", advertId(complaint))
                .param("responseId", responseId(complaint))
                .param("category", complaint.category().name())
                .param("comment", complaint.comment())
                .param("status", complaint.status().name())
                .param("resolvedBy", complaint.resolvedBy())
                .param("resolution", complaint.resolution())
                .param("createdAt", complaint.createdAt())
                .param("resolvedAt", complaint.resolvedAt())
                .update();
        return complaint;
    }

    @Override
    public Optional<Complaint> findComplaint(UUID id) {
        return jdbc.sql(SELECT_COMPLAINT + "WHERE id = ?").param(id).query(complaintMapper).optional();
    }

    @Override
    public List<Complaint> findComplaints(ComplaintStatus status, int limit, int offset) {
        return jdbc.sql(SELECT_COMPLAINT + "WHERE (CAST(:status AS varchar) IS NULL OR status = :status) "
                        + "ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
                .param("status", name(status))
                .param("limit", limit)
                .param("offset", offset)
                .query(complaintMapper)
                .list();
    }

    @Override
    public long countComplaints(ComplaintStatus status) {
        return jdbc.sql("SELECT COUNT(*) FROM complaint "
                        + "WHERE (CAST(:status AS varchar) IS NULL OR status = :status)")
                .param("status", name(status))
                .query(Long.class)
                .single();
    }

    @Override
    public boolean complaintExists(UUID reporterId, ContentType targetType, UUID targetId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM complaint WHERE reporter_id = :reporterId "
                        + "AND advert_id IS NOT DISTINCT FROM CAST(:advertId AS uuid) "
                        + "AND response_id IS NOT DISTINCT FROM CAST(:responseId AS uuid))")
                .param("reporterId", reporterId)
                .param("advertId", targetType == ContentType.ADVERT ? targetId : null)
                .param("responseId", targetType == ContentType.REPLY ? targetId : null)
                .query(Boolean.class)
                .single());
    }

    @Override
    public long countComplainantsAgainst(UUID authorId) {
        return jdbc.sql("SELECT COUNT(DISTINCT reporter_id) FROM complaint "
                        + "WHERE author_id = ? AND status <> 'DISMISSED'")
                .param(authorId)
                .query(Long.class)
                .single();
    }

    @Override
    public int resolveComplaintsOn(ContentType targetType, UUID targetId, ComplaintStatus decision, UUID adminId,
                                   String resolution, OffsetDateTime at) {
        return jdbc.sql("UPDATE complaint SET status = :status, resolved_by = :adminId, "
                        + "resolution = :resolution, resolved_at = :at WHERE status = 'OPEN' "
                        + "AND advert_id IS NOT DISTINCT FROM CAST(:advertId AS uuid) "
                        + "AND response_id IS NOT DISTINCT FROM CAST(:responseId AS uuid)")
                .param("status", decision.name())
                .param("adminId", adminId)
                .param("resolution", resolution)
                .param("at", at)
                .param("advertId", targetType == ContentType.ADVERT ? targetId : null)
                .param("responseId", targetType == ContentType.REPLY ? targetId : null)
                .update();
    }

    // ---- блокування ----

    /** Оновлюється лише зняття бану: решта полів незмінна з моменту блокування. */
    @Override
    public Ban saveBan(Ban ban) {
        jdbc.sql("INSERT INTO ban (id, user_id, banned_by, reason, expires_at, previous_status, created_at, "
                        + "lifted_at, lifted_by) VALUES (:id, :userId, :bannedBy, :reason, :expiresAt, "
                        + ":previousStatus, :createdAt, :liftedAt, :liftedBy) "
                        + "ON CONFLICT (id) DO UPDATE SET lifted_at = EXCLUDED.lifted_at, "
                        + "lifted_by = EXCLUDED.lifted_by")
                .param("id", ban.id())
                .param("userId", ban.userId())
                .param("bannedBy", ban.bannedBy())
                .param("reason", ban.reason())
                .param("expiresAt", ban.expiresAt())
                .param("previousStatus", ban.previousStatus().name())
                .param("createdAt", ban.createdAt())
                .param("liftedAt", ban.liftedAt())
                .param("liftedBy", ban.liftedBy())
                .update();
        return ban;
    }

    @Override
    public Optional<Ban> findActiveBan(UUID userId) {
        return jdbc.sql(SELECT_BAN + "WHERE user_id = ? AND lifted_at IS NULL")
                .param(userId)
                .query(banMapper)
                .optional();
    }

    @Override
    public List<Ban> findBans(UUID userId) {
        return jdbc.sql(SELECT_BAN + "WHERE user_id = ? ORDER BY created_at DESC, id")
                .param(userId)
                .query(banMapper)
                .list();
    }

    @Override
    public List<Ban> findExpiredBans(OffsetDateTime now) {
        return jdbc.sql(SELECT_BAN + "WHERE lifted_at IS NULL AND expires_at IS NOT NULL AND expires_at <= :now")
                .param("now", now)
                .query(banMapper)
                .list();
    }

    @Override
    public Set<UUID> findBannedUsers(OffsetDateTime now) {
        return Set.copyOf(jdbc.sql("SELECT DISTINCT user_id FROM ban WHERE lifted_at IS NULL "
                        + "AND (expires_at IS NULL OR expires_at > :now)")
                .param("now", now)
                .query(UUID.class)
                .list());
    }

    // ---- заборонені слова ----

    @Override
    public List<BannedWord> findBannedWords() {
        return jdbc.sql(SELECT_WORD + "ORDER BY word").query(wordMapper).list();
    }

    @Override
    public BannedWord saveBannedWord(BannedWord word) {
        jdbc.sql("INSERT INTO banned_word (id, word, added_by, created_at) "
                        + "VALUES (:id, :word, :addedBy, :createdAt)")
                .param("id", word.id())
                .param("word", word.word())
                .param("addedBy", word.addedBy())
                .param("createdAt", word.createdAt())
                .update();
        return word;
    }

    @Override
    public boolean deleteBannedWord(UUID id) {
        return jdbc.sql("DELETE FROM banned_word WHERE id = ?").param(id).update() > 0;
    }

    // ---- відмови автофільтра ----

    @Override
    public FilterRejection saveRejection(FilterRejection rejection) {
        jdbc.sql("INSERT INTO filter_rejection (id, user_id, target_type, matched_word, created_at) "
                        + "VALUES (:id, :userId, :targetType, :matchedWord, :createdAt)")
                .param("id", rejection.id())
                .param("userId", rejection.userId())
                .param("targetType", rejection.targetType().name())
                .param("matchedWord", rejection.matchedWord())
                .param("createdAt", rejection.createdAt())
                .update();
        return rejection;
    }

    @Override
    public long countRejectionsSince(UUID userId, OffsetDateTime since) {
        return jdbc.sql("SELECT COUNT(*) FROM filter_rejection WHERE user_id = :userId AND created_at > :since")
                .param("userId", userId)
                .param("since", since)
                .query(Long.class)
                .single();
    }

    private static String name(ComplaintStatus status) {
        return status == null ? null : status.name();
    }
}
