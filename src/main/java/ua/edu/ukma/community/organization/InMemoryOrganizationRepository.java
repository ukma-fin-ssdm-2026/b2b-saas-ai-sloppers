package ua.edu.ukma.community.organization;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Repository
@Profile("inmemory")
public class InMemoryOrganizationRepository implements OrganizationRepository {

    public static final UUID DEBATE_CLUB_ID = UUID.fromString("3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60");
    public static final UUID CODING_CLUB_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    public static final UUID VOLUNTEERS_ID = UUID.fromString("c0ffee00-1234-4abc-9def-56789abcdef0");

    private static final int SEED_AGE_DAYS = 30;

    private final Map<UUID, Organization> store = new ConcurrentHashMap<>();

    public InMemoryOrganizationRepository(Clock clock) {
        OffsetDateTime seededAt = OffsetDateTime.now(clock).minusDays(SEED_AGE_DAYS);
        seed(new Organization(DEBATE_CLUB_ID, "Дебатний клуб НаУКМА", "debate-club",
                "Щотижневі тренування з британського парламентського формату", "ukma.debate",
                "debate@ukma.edu.ua", null, false, seededAt, seededAt));
        seed(new Organization(CODING_CLUB_ID, "Клуб програмування", "coding-club",
                "Хакатони, лекції та підготовка до олімпіад", "ukma.coding",
                "coding@ukma.edu.ua", null, true, seededAt, seededAt));
        seed(new Organization(VOLUNTEERS_ID, "Волонтерський рух Могилянки", "volunteers",
                "Збори, допомога ветеранам і переселенцям", "mohyla.volunteers",
                null, null, false, seededAt, seededAt));
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Organization> findBySlug(String slug) {
        return store.values().stream()
                .filter(o -> o.slug().equals(slug))
                .findFirst();
    }

    @Override
    public List<Organization> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public boolean existsBySlug(String slug, UUID excludedId) {
        return store.values().stream()
                .anyMatch(o -> o.slug().equals(slug) && !o.id().equals(excludedId));
    }

    @Override
    public Organization save(Organization organization) {
        store.put(organization.id(), organization);
        return organization;
    }

    @Override
    public boolean deleteById(UUID id) {
        return store.remove(id) != null;
    }

    private void seed(Organization organization) {
        store.put(organization.id(), organization);
    }
}
