package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static ua.edu.ukma.community.organization.InMemoryOrganizationRepository.CODING_CLUB_ID;

class OrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T09:00:00Z");

    private Clock clock;
    private ApplicationEventPublisher events;
    private OrganizationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        events = mock(ApplicationEventPublisher.class);
        service = new OrganizationService(new InMemoryOrganizationRepository(clock), events, clock);
    }

    @ParameterizedTest(name = "query \"{0}\" знаходить {1}")
    @CsvSource({
            ", 3",
            "'', 3",
            "'  КЛУБ ', 2",
            "coding-club, 1",
            "xyz, 0"
    })
    void listFindsOrganizationsByNameOrSlugIgnoringCaseAndSpaces(String query, int expected) {
        // Act
        OffsetPage<Organization> page = service.list(query, 10, 0);

        // Assert
        assertThat(page.pagination().total()).isEqualTo(expected);
    }

    @Test
    void createRejectsSlugThatIsAlreadyTaken() {
        // Arrange
        var request = new OrganizationCreateRequest("Ще один дебатний", "debate-club", null, null, null);

        // Act + Assert
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
    }

    @Test
    void createWithFreeSlugTrimsNameAndSetsBothTimestampsFromClock() {
        // Arrange
        var request = new OrganizationCreateRequest("  FIDo  ", "fido", null, "fido_naukma", null);

        // Act
        Organization created = service.create(request);

        // Assert
        OffsetDateTime expected = OffsetDateTime.now(clock);
        assertThat(created.name()).isEqualTo("FIDo");
        assertThat(created.autoPublish()).isFalse();
        assertThat(created.createdAt()).isEqualTo(expected);
        assertThat(created.updatedAt()).isEqualTo(expected);
        assertThat(service.exists(created.id())).isTrue();
    }

    @Test
    void deleteRemovesOrganizationAndPublishesOrganizationDeletedEvent() {
        // Act
        service.delete(CODING_CLUB_ID);

        // Assert
        verify(events).publishEvent(new OrganizationDeleted(CODING_CLUB_ID));
        assertThat(service.exists(CODING_CLUB_ID)).isFalse();
    }
}
