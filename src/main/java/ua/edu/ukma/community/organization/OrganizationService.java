package ua.edu.ukma.community.organization;

import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.error.FieldViolation;
import ua.edu.ukma.community.common.pagination.OffsetPage;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class OrganizationService {

    public static final UUID DEBATE_CLUB_ID = UUID.fromString("3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60");
    public static final UUID CODING_CLUB_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    public static final UUID VOLUNTEERS_ID = UUID.fromString("c0ffee00-1234-4abc-9def-56789abcdef0");

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final Comparator<Organization> BY_NAME =
            Comparator.comparing(Organization::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Organization::id);

    private final Map<UUID, Organization> store = new ConcurrentHashMap<>();

    public OrganizationService() {
        OffsetDateTime t = OffsetDateTime.now(KYIV).minusDays(30);
        put(new Organization(DEBATE_CLUB_ID, "Дебатний клуб НаУКМА", "debate-club",
                "Щотижневі тренування з британського парламентського формату", "ukma.debate", "debate@ukma.edu.ua", t, t));
        put(new Organization(CODING_CLUB_ID, "Клуб програмування", "coding-club",
                "Хакатони, лекції та підготовка до олімпіад", "ukma.coding", "coding@ukma.edu.ua", t, t));
        put(new Organization(VOLUNTEERS_ID, "Волонтерський рух Могилянки", "volunteers",
                "Збори, допомога ветеранам і переселенцям", "mohyla.volunteers", null, t, t));
    }

    public OffsetPage<Organization> list(String q, int limit, int offset) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Organization> filtered = store.values().stream()
                .filter(o -> needle.isEmpty()
                        || o.name().toLowerCase(Locale.ROOT).contains(needle)
                        || o.slug().contains(needle))
                .sorted(BY_NAME)
                .toList();
        return OffsetPage.of(filtered, limit, offset);
    }

    public Organization get(UUID id) {
        Organization org = store.get(id);
        if (org == null) {
            throw new ApiException(ErrorCode.ORGANIZATION_NOT_FOUND, "Організацію " + id + " не знайдено");
        }
        return org;
    }

    public boolean exists(UUID id) {
        return store.containsKey(id);
    }

    public synchronized Organization create(OrganizationCreateRequest r) {
        ensureSlugFree(r.slug(), null);
        OffsetDateTime now = OffsetDateTime.now(KYIV);
        return put(new Organization(UUID.randomUUID(), r.name().trim(), r.slug(), r.description(),
                r.instagramHandle(), r.contactEmail(), now, now));
    }

    public synchronized Organization update(UUID id, OrganizationUpdateRequest r) {
        Organization cur = get(id);
        if (r.slug() != null) {
            ensureSlugFree(r.slug(), id);
        }
        return put(new Organization(cur.id(),
                r.name() != null ? r.name().trim() : cur.name(),
                r.slug() != null ? r.slug() : cur.slug(),
                r.description() != null ? r.description() : cur.description(),
                r.instagramHandle() != null ? r.instagramHandle() : cur.instagramHandle(),
                r.contactEmail() != null ? r.contactEmail() : cur.contactEmail(),
                cur.createdAt(), OffsetDateTime.now(KYIV)));
    }

    public void delete(UUID id) {
        if (store.remove(id) == null) {
            throw new ApiException(ErrorCode.ORGANIZATION_NOT_FOUND, "Організацію " + id + " не знайдено");
        }
    }

    private void ensureSlugFree(String slug, UUID exceptId) {
        boolean taken = store.values().stream()
                .anyMatch(o -> o.slug().equals(slug) && !o.id().equals(exceptId));
        if (taken) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS, "Організація з таким slug уже існує",
                    List.of(new FieldViolation("slug", "Slug зайнятий", slug)));
        }
    }

    private Organization put(Organization o) {
        store.put(o.id(), o);
        return o;
    }
}
