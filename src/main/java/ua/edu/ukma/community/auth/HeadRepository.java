package ua.edu.ukma.community.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


public interface HeadRepository {

    enum RemoveResult {
        REMOVED,
        NOT_HEAD,
        LAST_HEAD
    }

    List<OrganizationHead> findHeads(UUID organizationId);

    boolean isHead(UUID organizationId, UUID userId);

    List<UUID> findOrganizationsHeadedBy(UUID userId);

    boolean appoint(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at);

    RemoveResult remove(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at);

    RemoveResult replace(UUID organizationId, UUID newUserId, UUID oldUserId, UUID actorId, OffsetDateTime at);

    List<RoleChange> findLog(UUID organizationId);
}
