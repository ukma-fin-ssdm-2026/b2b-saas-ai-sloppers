package ua.edu.ukma.community.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Голови в PostgreSQL (таблиці {@code organization_head} і {@code role_change}, міграція V4).
 *
 * <p>Зміни складу голів ідуть у транзакції, що спершу блокує рядок організації ({@code SELECT ... FOR UPDATE}).
 * Тому паралельні запити до однієї організації виконуються по черзі, і перевірка «чи не останній голова»
 * завжди бачить актуальну кількість (FR-011, NFR-006, TC-016), навіть якщо працює кілька копій бекенду.
 */
@Repository
@Profile("!inmemory")
public class JdbcHeadRepository implements HeadRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ZoneId zone;

    public JdbcHeadRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.zone = clock.getZone();
    }

    @Override
    public List<OrganizationHead> findHeads(UUID organizationId) {
        return jdbc.sql("""
                        SELECT h.user_id, u.email, h.appointed_by, h.appointed_at
                        FROM organization_head h JOIN app_user u ON u.id = h.user_id
                        WHERE h.organization_id = ?
                        ORDER BY h.appointed_at, u.email""")
                .param(organizationId)
                .query((rs, n) -> new OrganizationHead(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("email"),
                        rs.getObject("appointed_by", UUID.class),
                        inZone(rs.getObject("appointed_at", OffsetDateTime.class))))
                .list();
    }

    @Override
    public boolean isHead(UUID organizationId, UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM organization_head WHERE organization_id = ? AND user_id = ?)")
                .param(organizationId)
                .param(userId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<UUID> findOrganizationsHeadedBy(UUID userId) {
        return jdbc.sql("SELECT organization_id FROM organization_head WHERE user_id = ? ORDER BY appointed_at")
                .param(userId)
                .query(UUID.class)
                .list();
    }

    @Override
    public boolean appoint(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at) {
        Boolean appointed = tx.execute(status -> {
            lockOrganization(organizationId);
            return insertHead(organizationId, userId, actorId, at);
        });
        return Boolean.TRUE.equals(appointed);
    }

    @Override
    public RemoveResult remove(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at) {
        return tx.execute(status -> {
            lockOrganization(organizationId);
            if (!isHead(organizationId, userId)) {
                return RemoveResult.NOT_HEAD;
            }
            if (countHeads(organizationId) <= 1) {
                return RemoveResult.LAST_HEAD;
            }
            deleteHead(organizationId, userId, actorId, at);
            return RemoveResult.REMOVED;
        });
    }

    @Override
    public RemoveResult replace(UUID organizationId, UUID newUserId, UUID oldUserId, UUID actorId,
                                OffsetDateTime at) {
        return tx.execute(status -> {
            lockOrganization(organizationId);
            if (!isHead(organizationId, oldUserId)) {
                return RemoveResult.NOT_HEAD;
            }
            insertHead(organizationId, newUserId, actorId, at);
            deleteHead(organizationId, oldUserId, actorId, at);
            return RemoveResult.REMOVED;
        });
    }

    @Override
    public List<RoleChange> findLog(UUID organizationId) {
        return jdbc.sql("""
                        SELECT r.id, r.organization_id, r.target_user_id, t.email AS target_email,
                               r.actor_user_id, a.email AS actor_email, r.action, r.created_at
                        FROM role_change r
                        JOIN app_user t ON t.id = r.target_user_id
                        LEFT JOIN app_user a ON a.id = r.actor_user_id
                        WHERE r.organization_id = ?
                        ORDER BY r.created_at DESC
                        LIMIT 500""")
                .param(organizationId)
                .query((rs, n) -> new RoleChange(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("target_user_id", UUID.class),
                        rs.getString("target_email"),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("actor_email"),
                        RoleChange.RoleAction.valueOf(rs.getString("action")),
                        inZone(rs.getObject("created_at", OffsetDateTime.class))))
                .list();
    }

    private void lockOrganization(UUID organizationId) {
        jdbc.sql("SELECT id FROM organization WHERE id = ? FOR UPDATE")
                .param(organizationId)
                .query(UUID.class)
                .optional();
    }

    private int countHeads(UUID organizationId) {
        return jdbc.sql("SELECT count(*) FROM organization_head WHERE organization_id = ?")
                .param(organizationId)
                .query(Integer.class)
                .single();
    }

    private boolean insertHead(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at) {
        int inserted = jdbc.sql("""
                        INSERT INTO organization_head (organization_id, user_id, appointed_by, appointed_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (organization_id, user_id) DO NOTHING""")
                .param(organizationId)
                .param(userId)
                .param(actorId)
                .param(at)
                .update();
        if (inserted > 0) {
            log(organizationId, userId, actorId, RoleChange.RoleAction.APPOINTED, at);
        }
        return inserted > 0;
    }

    private void deleteHead(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at) {
        jdbc.sql("DELETE FROM organization_head WHERE organization_id = ? AND user_id = ?")
                .param(organizationId)
                .param(userId)
                .update();
        log(organizationId, userId, actorId, RoleChange.RoleAction.REMOVED, at);
    }

    private void log(UUID organizationId, UUID userId, UUID actorId, RoleChange.RoleAction action,
                     OffsetDateTime at) {
        jdbc.sql("""
                        INSERT INTO role_change (id, organization_id, target_user_id, actor_user_id, action, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .param(UUID.randomUUID())
                .param(organizationId)
                .param(userId)
                .param(actorId)
                .param(action.name())
                .param(at)
                .update();
    }

    private OffsetDateTime inZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(zone).toOffsetDateTime();
    }
}
