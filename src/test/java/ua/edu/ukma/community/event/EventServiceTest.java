package ua.edu.ukma.community.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.organization.OrganizationDeleted;
import ua.edu.ukma.community.organization.OrganizationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.NOW;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.OTHER_ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.SOURCE_ID;
import static ua.edu.ukma.community.support.TestFixtures.event;
import static ua.edu.ukma.community.support.TestFixtures.organization;
import static ua.edu.ukma.community.support.TestFixtures.publishedEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventService")
class EventServiceTest {

    @Mock
    private EventRepository repository;
    @Mock
    private OrganizationService organizations;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(repository, organizations, FIXED_CLOCK);
    }

    private static ApiException notFound(ErrorCode code) {
        return new ApiException(code, "не знайдено");
    }

    @Nested
    @DisplayName("digest")
    class Digest {

        @Test
        @DisplayName("лише опубліковані майбутні події, відсортовані за часом початку")
        void onlyPublishedUpcomingSortedByStart() {
            Event later = publishedEvent(NOW.plusDays(5));
            Event sooner = publishedEvent(NOW.plusHours(2));
            Event past = publishedEvent(NOW.minusHours(1));
            Event pending = event(ORG_ID, EventStatus.PENDING, false, NOW.plusDays(1));
            Event unpublished = event(ORG_ID, EventStatus.UNPUBLISHED, true, NOW.plusDays(1));
            when(repository.findAll()).thenReturn(List.of(later, past, pending, sooner, unpublished));

            OffsetPage<Event> page = service.digest(null, null, null, 20, 0);

            assertThat(page.data()).containsExactly(sooner, later);
            assertThat(page.pagination().total()).isEqualTo(2);
        }

        @Test
        @DisplayName("межі: подія рівно «зараз» входить, подія рівно о `to` — ні")
        void boundariesAreInclusiveLowerExclusiveUpper() {
            Event startsNow = publishedEvent(NOW);
            Event startsAtTo = publishedEvent(NOW.plusDays(1));
            when(repository.findAll()).thenReturn(List.of(startsNow, startsAtTo));

            OffsetPage<Event> page = service.digest(null, null, NOW.plusDays(1), 20, 0);

            assertThat(page.data()).containsExactly(startsNow);
        }

        @Test
        @DisplayName("`from` у минулому не повертає минулих подій — нижня межа завжди не раніше «зараз»")
        void pastFromIsClampedToNow() {
            Event yesterdayEvening = publishedEvent(NOW.minusHours(12));
            Event tomorrow = publishedEvent(NOW.plusDays(1));
            when(repository.findAll()).thenReturn(List.of(yesterdayEvening, tomorrow));

            OffsetPage<Event> page = service.digest(null, NOW.minusDays(2), null, 20, 0);

            assertThat(page.data()).containsExactly(tomorrow);
        }

        @Test
        @DisplayName("фільтр за організацією")
        void filtersByOrganization() {
            Event ours = publishedEvent(NOW.plusDays(1));
            Event theirs = event(OTHER_ORG_ID, EventStatus.PUBLISHED, true, NOW.plusDays(1));
            when(repository.findAll()).thenReturn(List.of(ours, theirs));

            assertThat(service.digest(OTHER_ORG_ID, null, null, 20, 0).data()).containsExactly(theirs);
        }

        @Test
        @DisplayName("from == to → INVALID_PARAMETER по полю `to`, сховище не викликається")
        void emptyRangeIsRejected() {
            assertThatThrownBy(() -> service.digest(null, NOW.plusDays(1), NOW.plusDays(1), 20, 0))
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_PARAMETER);
                        assertThat(ex.details()).singleElement()
                                .satisfies(v -> assertThat(v.field()).isEqualTo("to"));
                    });
            verifyNoInteractions(repository);
        }
    }

    @Test
    @DisplayName("getPublished: подія в черзі модерації для гостей не існує (404)")
    void getPublishedHidesPendingEvent() {
        Event pending = event(ORG_ID, EventStatus.PENDING, false, NOW.plusDays(1));
        when(repository.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.getPublished(pending.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.EVENT_NOT_FOUND));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("ручна подія публікується одразу, перевірена, без джерела скрейпера")
        void manualEventIsPublishedAndVerified() {
            when(organizations.get(ORG_ID)).thenReturn(organization(ORG_ID, "Дебатний клуб", "debate-club"));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event created = service.create(ORG_ID, new EventCreateRequest("  Турнір  ", null,
                    NOW.plusDays(3), "Корпус 1", null));

            assertThat(created.title()).isEqualTo("Турнір");
            assertThat(created.status()).isEqualTo(EventStatus.PUBLISHED);
            assertThat(created.verified()).isTrue();
            assertThat(created.isScraped()).isFalse();
            assertThat(created.createdAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("неіснуюча організація → помилка сервісу організацій, нічого не зберігається")
        void unknownOrganizationIsPropagated() {
            UUID missing = UUID.randomUUID();
            when(organizations.get(missing)).thenThrow(notFound(ErrorCode.ORGANIZATION_NOT_FOUND));

            assertThatThrownBy(() -> service.create(missing,
                    new EventCreateRequest("Турнір", null, NOW.plusDays(3), null, null)))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ingest (прийом від скрейпера)")
    class Ingest {

        private final ScrapedEvent scraped = new ScrapedEvent(ORG_ID, SOURCE_ID, "post-42",
                "  Вечір настільних ігор ", NOW.plusDays(2), "https://www.instagram.com/p/post-42/");

        @ParameterizedTest(name = "autoPublish={0} → {1}")
        @CsvSource({"true, PUBLISHED", "false, PENDING"})
        void statusDependsOnAutoPublish(boolean autoPublish, EventStatus expected) {
            when(repository.findBySourcePost(SOURCE_ID, "post-42")).thenReturn(Optional.empty());
            when(organizations.get(ORG_ID))
                    .thenReturn(organization(ORG_ID, "Дебатний клуб", "debate-club", autoPublish));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<Event> ingested = service.ingest(scraped);

            assertThat(ingested).hasValueSatisfying(e -> {
                assertThat(e.status()).isEqualTo(expected);
                assertThat(e.verified()).isFalse();
                assertThat(e.title()).isEqualTo("Вечір настільних ігор");
                assertThat(e.isScraped()).isTrue();
            });
        }

        @Test
        @DisplayName("дублікат поста ігнорується: порожній результат, без збереження")
        void duplicatePostIsSkipped() {
            Event existing = event(ORG_ID, EventStatus.PUBLISHED, false, NOW.plusDays(2));
            when(repository.findBySourcePost(SOURCE_ID, "post-42")).thenReturn(Optional.of(existing));

            assertThat(service.ingest(scraped)).isEmpty();
            verifyNoInteractions(organizations);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("модерація")
    class Moderation {

        @Test
        @DisplayName("approve: PENDING → PUBLISHED, перевірена, updatedAt = зараз")
        void approvePendingSavesPublished() {
            Event pending = event(ORG_ID, EventStatus.PENDING, false, NOW.plusDays(1));
            when(repository.findById(pending.id())).thenReturn(Optional.of(pending));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Event approved = service.approve(pending.id());

            assertThat(approved.status()).isEqualTo(EventStatus.PUBLISHED);
            assertThat(approved.verified()).isTrue();
            assertThat(approved.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("reject опублікованої події → INVALID_STATE_TRANSITION, без збереження")
        void rejectPublishedIsConflict() {
            Event published = publishedEvent(NOW.plusDays(1));
            when(repository.findById(published.id())).thenReturn(Optional.of(published));

            assertThatThrownBy(() -> service.reject(published.id()))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("unpublish неіснуючої події → EVENT_NOT_FOUND")
        void unpublishMissingIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(repository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unpublish(missing))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.EVENT_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("видалення організації каскадно видаляє її події")
    void organizationDeletionCascades() {
        service.onOrganizationDeleted(new OrganizationDeleted(ORG_ID));

        verify(repository).deleteByOrganizationId(ORG_ID);
    }
}