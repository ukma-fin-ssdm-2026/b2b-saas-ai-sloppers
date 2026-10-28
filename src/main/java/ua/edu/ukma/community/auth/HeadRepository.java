package ua.edu.ukma.community.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Голови організацій і журнал змін ролей.
 * Методи, що змінюють склад голів, атомарні й самі пишуть у журнал (FR-011, FR-013, NFR-006).
 */
public interface HeadRepository {

    /** Результат зняття голови. */
    enum RemoveResult {
        REMOVED,
        /** Користувач не є головою цієї організації: нічого не змінено. */
        NOT_HEAD,
        /** Це останній голова: зняти не можна (FR-011), нічого не змінено. */
        LAST_HEAD
    }

    List<OrganizationHead> findHeads(UUID organizationId);

    boolean isHead(UUID organizationId, UUID userId);

    List<UUID> findOrganizationsHeadedBy(UUID userId);

    /**
     * @return {@code false}, якщо користувач уже голова (нічого не змінено)
     */
    boolean appoint(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at);

    RemoveResult remove(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at);

    /**
     * Призначає {@code newUserId} і знімає {@code oldUserId} однією операцією (FR-012), тож організація
     * ні на мить не лишається без голови. Якщо {@code oldUserId} не голова — {@link RemoveResult#NOT_HEAD}.
     */
    RemoveResult replace(UUID organizationId, UUID newUserId, UUID oldUserId, UUID actorId, OffsetDateTime at);

    /** Журнал змін, нові записи першими. */
    List<RoleChange> findLog(UUID organizationId);
}
