package ua.edu.ukma.community.subscription;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import ua.edu.ukma.community.auth.UserRepository;
import ua.edu.ukma.community.auth.UserStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("inmemory")
public class InMemorySubscriptionRepository implements SubscriptionRepository {

    private final Map<UUID, Set<UUID>> byOrg = new ConcurrentHashMap<>();
    private final UserRepository users;

    public InMemorySubscriptionRepository(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean exists(UUID organizationId, UUID userId) {
        Set<UUID> members = byOrg.get(organizationId);
        return members != null && members.contains(userId);
    }

    @Override
    public void subscribe(UUID organizationId, UUID userId) {
        byOrg.computeIfAbsent(organizationId, id -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    @Override
    public void unsubscribe(UUID organizationId, UUID userId) {
        Set<UUID> members = byOrg.get(organizationId);
        if (members != null) {
            members.remove(userId);
        }
    }

    @Override
    public List<String> findActiveSubscriberEmails(UUID organizationId) {
        Set<UUID> members = byOrg.getOrDefault(organizationId, Set.of());
        List<String> emails = new ArrayList<>();
        for (UUID userId : members) {
            users.findById(userId)
                    .filter(u -> u.status() == UserStatus.ACTIVE)
                    .ifPresent(u -> emails.add(u.email()));
        }
        return emails;
    }
}
