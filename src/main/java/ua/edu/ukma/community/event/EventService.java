package ua.edu.ukma.community.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.organization.OrganizationDeleted;
import ua.edu.ukma.community.organization.OrganizationService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;


@Service
public class EventService {

    private static final Comparator<Event> BY_START =
            Comparator.comparing(Event::startsAt).thenComparing(Event::id);

    private final EventRepository events;
    private final OrganizationService organizations;
    private final Clock clock;

    public EventService(EventRepository events, OrganizationService organizations, Clock clock) {
        this.events = events;
        this.organizations = organizations;
        this.clock = clock;
    }


    public OffsetPage<Event> digest(UUID organizationId, OffsetDateTime from, OffsetDateTime to,
                                    int limit, int offset) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw ApiException.invalidParameter("to", "Кінець діапазону має бути пізніше за початок", to.toString());
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime lowerBound = from != null && from.isAfter(now) ? from : now;
        List<Event> upcoming = events.findAll().stream()
                .filter(Event::isPublished)
                .filter(e -> e.startsAt().isAfter(lowerBound))
                .filter(e -> to == null || e.startsAt().isBefore(to))
                .filter(e -> organizationId == null || organizationId.equals(e.organizationId()))
                .sorted(BY_START)
                .toList();
        return OffsetPage.of(upcoming, limit, offset);
    }

    public Event getPublished(UUID id) {
        return events.findById(id).filter(Event::isPublished).orElseThrow(() -> notFound(id));
    }

    public Event get(UUID id) {
        return events.findById(id).orElseThrow(() -> notFound(id));
    }

    public OffsetPage<Event> listForOrganization(UUID organizationId, EventStatus status, Boolean verified,
                                                 int limit, int offset) {
        organizations.get(organizationId);
        List<Event> matching = events.findByOrganizationId(organizationId).stream()
                .filter(e -> status == null || e.status() == status)
                .filter(e -> verified == null || e.verified() == verified)
                .sorted(BY_START)
                .toList();
        return OffsetPage.of(matching, limit, offset);
    }

    public synchronized Event create(UUID organizationId, EventCreateRequest request) {
        organizations.get(organizationId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return events.save(new Event(UUID.randomUUID(), organizationId, request.title().trim(),
                request.description(), request.startsAt(), request.location(), request.originalUrl(),
                null, null, EventStatus.PUBLISHED, true, now, now));
    }

    public synchronized Event update(UUID id, EventUpdateRequest request) {
        return change(id, event -> event.withDetails(request.title(), request.description(),
                request.startsAt(), request.location(), request.originalUrl(), OffsetDateTime.now(clock)));
    }

    public synchronized Event approve(UUID id) {
        return change(id, event -> event.approve(OffsetDateTime.now(clock)));
    }

    public synchronized Event reject(UUID id) {
        return change(id, event -> event.reject(OffsetDateTime.now(clock)));
    }

    public synchronized Event unpublish(UUID id) {
        return change(id, event -> event.unpublish(OffsetDateTime.now(clock)));
    }


    public synchronized Optional<Event> ingest(ScrapedEvent scraped) {
        if (events.findBySourcePost(scraped.sourceId(), scraped.externalPostId()).isPresent()) {
            return Optional.empty();
        }
        Organization organization = organizations.get(scraped.organizationId());
        EventStatus status = organization.autoPublish() ? EventStatus.PUBLISHED : EventStatus.PENDING;
        OffsetDateTime now = OffsetDateTime.now(clock);
        return Optional.of(events.save(new Event(UUID.randomUUID(), organization.id(), scraped.title(),
                null, scraped.startsAt(), null, scraped.originalUrl(), scraped.sourceId(),
                scraped.externalPostId(), status, false, now, now)));
    }

    @EventListener
    public synchronized void onOrganizationDeleted(OrganizationDeleted deleted) {
        events.deleteByOrganizationId(deleted.organizationId());
    }

    private Event change(UUID id, UnaryOperator<Event> transition) {
        return events.save(transition.apply(get(id)));
    }

    private static ApiException notFound(UUID id) {
        return new ApiException(ErrorCode.EVENT_NOT_FOUND, "Подію " + id + " не знайдено");
    }
}
