package ua.edu.ukma.community.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.organization.InMemoryOrganizationRepository;
import ua.edu.ukma.community.organization.OrganizationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.NOW;
import static ua.edu.ukma.community.support.TestFixtures.event;

/**
 * Відбір подій для дайджесту переїхав із сервіса у сховище (R3), тому ті самі межові випадки, що були
 * в {@code EventServiceTest}, перевіряються тут на справжній реалізації: мок підтвердив би лише те,
 * що сервіс його викликав.
 */
@DisplayName("InMemoryEventRepository + EventService (fake): відбір для дайджесту")
class InMemoryEventRepositoryTest {

    private static final UUID EMPTY_ORG_ID = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    private InMemoryEventRepository repository;
    private EventService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEventRepository(FIXED_CLOCK);
        OrganizationService organizations = new OrganizationService(
                new InMemoryOrganizationRepository(FIXED_CLOCK), noopPublisher(), FIXED_CLOCK);
        service = new EventService(repository, organizations, noopPublisher(), FIXED_CLOCK);
    }

    private static ApplicationEventPublisher noopPublisher() {
        return e -> {
        };
    }

    private Event save(EventStatus status, OffsetDateTime startsAt) {
        return repository.save(event(EMPTY_ORG_ID, status, true, startsAt));
    }

    @Test
    @DisplayName("демо-дані: у дайджест ідуть лише опубліковані події, «На модерації» — ні")
    void onlyPublishedSeedEventsAreListed() {
        OffsetPage<Event> page = service.digest(null, null, null, 20, 0);

        assertThat(page.data()).extracting(Event::id).containsExactly(
                InMemoryEventRepository.DEBATE_TOURNAMENT_ID,
                InMemoryEventRepository.HACKATHON_ID);
        assertThat(page.data()).extracting(Event::status).containsOnly(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("неопубліковані й відхилені події не потрапляють у дайджест")
    void unpublishedAndRejectedAreExcluded() {
        save(EventStatus.UNPUBLISHED, NOW.plusHours(1));
        save(EventStatus.REJECTED, NOW.plusHours(2));
        Event visible = save(EventStatus.PUBLISHED, NOW.plusHours(3));

        OffsetPage<Event> page = service.digest(EMPTY_ORG_ID, null, null, 20, 0);

        assertThat(page.data()).containsExactly(visible);
    }

    @Test
    @DisplayName("межі: подія рівно «зараз» входить, подія рівно о `to` — ні")
    void boundariesAreInclusiveLowerExclusiveUpper() {
        Event startsNow = save(EventStatus.PUBLISHED, NOW);
        save(EventStatus.PUBLISHED, NOW.plusDays(1));

        OffsetPage<Event> page = service.digest(EMPTY_ORG_ID, null, NOW.plusDays(1), 20, 0);

        assertThat(page.data()).containsExactly(startsNow);
    }

    @Test
    @DisplayName("минулі події не повертаються навіть за `from` у минулому")
    void pastEventsStayOut() {
        save(EventStatus.PUBLISHED, NOW.minusHours(12));
        Event tomorrow = save(EventStatus.PUBLISHED, NOW.plusDays(1));

        OffsetPage<Event> page = service.digest(EMPTY_ORG_ID, NOW.minusDays(2), null, 20, 0);

        assertThat(page.data()).containsExactly(tomorrow);
    }

    @Test
    @DisplayName("фільтр за організацією відсікає чужі події")
    void filtersByOrganization() {
        Event ours = save(EventStatus.PUBLISHED, NOW.plusDays(1));

        assertThat(service.digest(EMPTY_ORG_ID, null, null, 20, 0).data()).containsExactly(ours);
        assertThat(service.digest(InMemoryOrganizationRepository.DEBATE_CLUB_ID, null, null, 20, 0).data())
                .extracting(Event::id)
                .containsExactly(InMemoryEventRepository.DEBATE_TOURNAMENT_ID);
    }

    @Test
    @DisplayName("пагінація: зсув за межами набору дає порожню сторінку з точним total")
    void paginationBeyondResultSet() {
        List<Event> saved = List.of(
                save(EventStatus.PUBLISHED, NOW.plusDays(1)),
                save(EventStatus.PUBLISHED, NOW.plusDays(2)),
                save(EventStatus.PUBLISHED, NOW.plusDays(3)));

        OffsetPage<Event> firstPage = service.digest(EMPTY_ORG_ID, null, null, 2, 0);
        assertThat(firstPage.data()).containsExactly(saved.get(0), saved.get(1));
        assertThat(firstPage.pagination().total()).isEqualTo(saved.size());
        assertThat(firstPage.pagination().hasMore()).isTrue();

        OffsetPage<Event> beyond = service.digest(EMPTY_ORG_ID, null, null, 2, 10);
        assertThat(beyond.data()).isEmpty();
        assertThat(beyond.pagination().total()).isEqualTo(saved.size());
        assertThat(beyond.pagination().hasMore()).isFalse();
    }
}
