package ua.edu.ukma.community.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Repository
@Profile("inmemory")
public class InMemoryHeadRepository implements HeadRepository {

    private record Entry(UUID userId, UUID appointedBy, OffsetDateTime appointedAt) {
    }

    private final UserRepository users;
    private final Map<UUID, Map<UUID, Entry>> heads = new LinkedHashMap<>();
    private final List<RoleChange> log = new ArrayList<>();

    public InMemoryHeadRepository(UserRepository users) {
        this.users = users;
    }

    @Override
    public synchronized List<OrganizationHead> findHeads(UUID organizationId) {
        return heads.getOrDefault(organizationId, Map.of()).values().stream()
                .map(e -> new OrganizationHead(e.userId(), email(e.userId()), e.appointedBy(), e.appointedAt()))
                .toList();
    }

    @Override
    public synchronized boolean isHead(UUID organizationId, UUID userId) {
        return heads.getOrDefault(organizationId, Map.of()).containsKey(userId);
    }

    @Override
    public synchronized List<UUID> findOrganizationsHeadedBy(UUID userId) {
        return heads.entrySet().stream()
                .filter(e -> e.getValue().containsKey(userId))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public synchronized boolean appoint(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at) {
        Map<UUID, Entry> org = heads.computeIfAbsent(organizationId, id -> new LinkedHashMap<>());
        if (org.containsKey(userId)) {
            return false;
        }
        org.put(userId, new Entry(userId, actorId, at));
        record(organizationId, userId, actorId, RoleChange.RoleAction.APPOINTED, at);
        return true;
    }

    @Override
    public synchronized RemoveResult remove(UUID organizationId, UUID userId, UUID actorId, OffsetDateTime at) {
        Map<UUID, Entry> org = heads.getOrDefault(organizationId, new LinkedHashMap<>());
        if (!org.containsKey(userId)) {
            return RemoveResult.NOT_HEAD;
        }
        if (org.size() <= 1) {
            return RemoveResult.LAST_HEAD;
        }
        org.remove(userId);
        record(organizationId, userId, actorId, RoleChange.RoleAction.REMOVED, at);
        return RemoveResult.REMOVED;
    }

    @Override
    public synchronized RemoveResult replace(UUID organizationId, UUID newUserId, UUID oldUserId, UUID actorId,
                                             OffsetDateTime at) {
        if (!isHead(organizationId, oldUserId)) {
            return RemoveResult.NOT_HEAD;
        }
        appoint(organizationId, newUserId, actorId, at);
        heads.get(organizationId).remove(oldUserId);
        record(organizationId, oldUserId, actorId, RoleChange.RoleAction.REMOVED, at);
        return RemoveResult.REMOVED;
    }

    @Override
    public synchronized List<RoleChange> findLog(UUID organizationId) {
        return log.stream()
                .filter(r -> r.organizationId().equals(organizationId))
                .sorted(Comparator.comparing(RoleChange::createdAt).reversed())
                .toList();
    }

    private void record(UUID organizationId, UUID userId, UUID actorId, RoleChange.RoleAction action,
                        OffsetDateTime at) {
        log.add(new RoleChange(UUID.randomUUID(), organizationId, userId, email(userId), actorId,
                actorId == null ? null : email(actorId), action, at));
    }

    private String email(UUID userId) {
        return users.findById(userId).map(User::email).orElse(null);
    }
}
