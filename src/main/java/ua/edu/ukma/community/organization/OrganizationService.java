package ua.edu.ukma.community.organization;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.error.FieldViolation;
import ua.edu.ukma.community.common.pagination.OffsetPage;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


@Service
public class OrganizationService {

    private static final Comparator<Organization> BY_NAME =
            Comparator.comparing(Organization::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Organization::id);

    private final OrganizationRepository organizations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizations, ApplicationEventPublisher events, Clock clock) {
        this.organizations = organizations;
        this.events = events;
        this.clock = clock;
    }

    public OffsetPage<Organization> list(String query, int limit, int offset) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Organization> matching = organizations.findAll().stream()
                .filter(o -> matches(o, needle))
                .sorted(BY_NAME)
                .toList();
        return OffsetPage.of(matching, limit, offset);
    }

    public Organization get(UUID id) {
        return organizations.findById(id).orElseThrow(() -> notFound(id.toString()));
    }

    public Organization getBySlug(String slug) {
        String normalized = normalizeSlug(slug);
        return organizations.findBySlug(normalized).orElseThrow(() -> notFound(normalized));
    }

    public boolean exists(UUID id) {
        return organizations.findById(id).isPresent();
    }

    public synchronized Organization create(OrganizationCreateRequest request) {
        ensureSlugFree(request.slug(), null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return organizations.save(new Organization(UUID.randomUUID(), request.name().trim(), request.slug(),
                request.description(), request.instagramHandle(), request.contactEmail(), false, now, now));
    }

    public synchronized Organization update(UUID id, OrganizationUpdateRequest request) {
        Organization current = get(id);
        if (request.slug() != null) {
            ensureSlugFree(request.slug(), id);
        }
        return organizations.save(new Organization(current.id(),
                request.name() != null ? request.name().trim() : current.name(),
                request.slug() != null ? request.slug() : current.slug(),
                request.description() != null ? request.description() : current.description(),
                request.instagramHandle() != null ? request.instagramHandle() : current.instagramHandle(),
                request.contactEmail() != null ? request.contactEmail() : current.contactEmail(),
                request.autoPublish() != null ? request.autoPublish() : current.autoPublish(),
                current.createdAt(), OffsetDateTime.now(clock)));
    }

    public synchronized void delete(UUID id) {
        if (!organizations.deleteById(id)) {
            throw notFound(id.toString());
        }
        events.publishEvent(new OrganizationDeleted(id));
    }

    private static boolean matches(Organization organization, String needle) {
        return needle.isEmpty()
                || organization.name().toLowerCase(Locale.ROOT).contains(needle)
                || organization.slug().contains(needle);
    }

    private void ensureSlugFree(String slug, UUID exceptId) {
        if (organizations.existsBySlug(slug, exceptId)) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS, "Організація з таким slug уже існує",
                    List.of(new FieldViolation("slug", "Slug зайнятий", slug)));
        }
    }

    private static String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException notFound(String key) {
        return new ApiException(ErrorCode.ORGANIZATION_NOT_FOUND, "Організацію " + key + " не знайдено");
    }
}
