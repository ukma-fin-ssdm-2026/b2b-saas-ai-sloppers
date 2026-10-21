package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.ukma.community.organization.InMemoryOrganizationRepository.CODING_CLUB_ID;
import static ua.edu.ukma.community.organization.InMemoryOrganizationRepository.DEBATE_CLUB_ID;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;

/**
 * Сховище в пам'яті як заглушка (fake) замість БД: сервіс працює з реальною реалізацією сховища,
 * а публікатор подій — рукописна заглушка, що лише запам'ятовує події.
 */
@DisplayName("InMemoryOrganizationRepository + OrganizationService (fake)")
class InMemoryOrganizationRepositoryTest {

    private InMemoryOrganizationRepository repository;
    private final List<Object> publishedEvents = new ArrayList<>();
    private OrganizationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrganizationRepository(FIXED_CLOCK);
        ApplicationEventPublisher stubPublisher = publishedEvents::add;
        service = new OrganizationService(repository, stubPublisher, FIXED_CLOCK);
    }

    @Test
    @DisplayName("existsBySlug ігнорує організацію з excludedId")
    void existsBySlugHonoursExcludedId() {
        assertThat(repository.existsBySlug("debate-club", null)).isTrue();
        assertThat(repository.existsBySlug("debate-club", DEBATE_CLUB_ID)).isFalse();
        assertThat(repository.existsBySlug("debate-club", CODING_CLUB_ID)).isTrue();
        assertThat(repository.existsBySlug("no-such-club", null)).isFalse();
    }

    @Test
    @DisplayName("повторне видалення: перше успішне, друге повертає false")
    void deleteIsIdempotentOnRepositoryLevel() {
        assertThat(repository.deleteById(DEBATE_CLUB_ID)).isTrue();
        assertThat(repository.deleteById(DEBATE_CLUB_ID)).isFalse();
        assertThat(repository.deleteById(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("пагінація поверх демо-даних: зсув за межами списку дає порожню сторінку")
    void paginationBeyondSeedData() {
        var page = service.list(null, 2, 10);

        assertThat(page.data()).isEmpty();
        assertThat(page.pagination().total()).isEqualTo(3);
        assertThat(page.pagination().hasMore()).isFalse();
    }

    @Test
    @DisplayName("видалення через сервіс публікує рівно одну подію")
    void serviceDeletePublishesExactlyOnce() {
        service.delete(CODING_CLUB_ID);

        assertThat(publishedEvents).containsExactly(new OrganizationDeleted(CODING_CLUB_ID));
        assertThat(repository.findById(CODING_CLUB_ID)).isEmpty();
    }
}