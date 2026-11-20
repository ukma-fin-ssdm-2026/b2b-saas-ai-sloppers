package ua.edu.ukma.community.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the digest SQL against text-block concatenation bugs
 * ({@code updated_atFROM}) and verifies published-window filters.
 */
@DisplayName("JdbcEventRepository на PostgreSQL")
class JdbcEventRepositoryTest {

    private MutableClock clock;
    private JdbcClient jdbc;
    private JdbcEventRepository events;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        DataSource dataSource = TestDatabase.dataSource();
        TestDatabase.truncateAll();
        jdbc = JdbcClient.create(dataSource);
        clock = MutableClock.startingAtFixtureNow();
        events = new JdbcEventRepository(jdbc, clock);
        organizationId = UUID.randomUUID();
        jdbc.sql("INSERT INTO organization (id, name, slug) VALUES (?, 'FIDo', 'fido')")
                .param(organizationId)
                .update();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private Event event(String title, EventStatus status, OffsetDateTime startsAt) {
        OffsetDateTime at = now();
        return events.save(new Event(
                UUID.randomUUID(),
                organizationId,
                title,
                "desc",
                startsAt,
                "Campus",
                null,
                null,
                null,
                null,
                status,
                true,
                at,
                at));
    }

    @Test
    @DisplayName("findPublishedBetween виконується без SQL-помилки (пробіл перед FROM)")
    void findPublishedBetweenSqlIsValid() {
        Event upcoming = event("Hackathon", EventStatus.PUBLISHED, now().plusDays(1));
        event("Draft", EventStatus.PENDING, now().plusDays(2));
        event("Past", EventStatus.PUBLISHED, now().minusDays(1));

        List<Event> found = events.findPublishedBetween(organizationId, now(), null);

        assertThat(found).extracting(Event::id).containsExactly(upcoming.id());
    }

    @Test
    @DisplayName("findPublishedBetween: null organizationId — усі орги; to обрізає вікно")
    void findPublishedBetweenFilters() {
        UUID otherOrg = UUID.randomUUID();
        jdbc.sql("INSERT INTO organization (id, name, slug) VALUES (?, 'Other', 'other')")
                .param(otherOrg)
                .update();

        OffsetDateTime at = now();
        Event ours = events.save(new Event(
                UUID.randomUUID(), organizationId, "Ours", null, now().plusHours(2),
                null, null, null, null, null, EventStatus.PUBLISHED, true, at, at));
        Event theirs = events.save(new Event(
                UUID.randomUUID(), otherOrg, "Theirs", null, now().plusHours(3),
                null, null, null, null, null, EventStatus.PUBLISHED, true, at, at));
        Event later = events.save(new Event(
                UUID.randomUUID(), organizationId, "Later", null, now().plusDays(5),
                null, null, null, null, null, EventStatus.PUBLISHED, true, at, at));

        assertThat(events.findPublishedBetween(null, now(), null))
                .extracting(Event::id)
                .containsExactlyInAnyOrder(ours.id(), theirs.id(), later.id());

        assertThat(events.findPublishedBetween(organizationId, now(), now().plusDays(1)))
                .extracting(Event::id)
                .containsExactly(ours.id());
    }

    @Test
    @DisplayName("digest round-trip: findById після save зберігає поля")
    void saveAndFindById() {
        Event saved = event("Talk", EventStatus.PUBLISHED, now().plusDays(1));
        Event loaded = events.findById(saved.id()).orElseThrow();
        assertThat(loaded).isEqualTo(saved);
    }
}
