package ua.edu.ukma.community.event;

import org.springframework.stereotype.Repository;
import ua.edu.ukma.community.organization.InMemoryOrganizationRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Repository
public class InMemoryEventRepository implements EventRepository {

    public static final UUID DEBATE_TOURNAMENT_ID = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    public static final UUID HACKATHON_ID = UUID.fromString("e1000000-0000-4000-8000-000000000002");
    public static final UUID FUNDRAISER_ID = UUID.fromString("e1000000-0000-4000-8000-000000000003");
    public static final UUID DEMO_SOURCE_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    private static final int EVENING_HOUR = 18;

    private final Map<UUID, Event> store = new ConcurrentHashMap<>();

    public InMemoryEventRepository(Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime evening = now.withHour(EVENING_HOUR);
        seed(new Event(DEBATE_TOURNAMENT_ID, InMemoryOrganizationRepository.DEBATE_CLUB_ID,
                "Відкритий дебатний турнір", "Британський парламентський формат, реєстрація команд на місці.",
                evening.plusDays(7), "НаУКМА, корпус 1", null, null, null,
                EventStatus.PUBLISHED, true, now, now));
        seed(new Event(HACKATHON_ID, InMemoryOrganizationRepository.CODING_CLUB_ID,
                "аджентік студіос", null, evening.plusDays(14), null,
                "https://www.instagram.com/p/demo-hackathon/", DEMO_SOURCE_ID, "demo-hackathon",
                EventStatus.PUBLISHED, false, now, now));
        seed(new Event(FUNDRAISER_ID, InMemoryOrganizationRepository.VOLUNTEERS_ID,
                "аукцон побачень", null, evening.plusDays(3), null,
                "https://www.instagram.com/p/demo-fair/", DEMO_SOURCE_ID, "demo-fair",
                EventStatus.PENDING, false, now, now));
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Event> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public List<Event> findByOrganizationId(UUID organizationId) {
        return store.values().stream()
                .filter(e -> e.organizationId().equals(organizationId))
                .toList();
    }

    @Override
    public Optional<Event> findBySourcePost(UUID sourceId, String externalPostId) {
        return store.values().stream()
                .filter(e -> sourceId.equals(e.sourceId()) && externalPostId.equals(e.externalPostId()))
                .findFirst();
    }

    @Override
    public Event save(Event event) {
        store.put(event.id(), event);
        return event;
    }

    @Override
    public void deleteByOrganizationId(UUID organizationId) {
        store.values().removeIf(e -> e.organizationId().equals(organizationId));
    }

    private void seed(Event event) {
        store.put(event.id(), event);
    }
}
