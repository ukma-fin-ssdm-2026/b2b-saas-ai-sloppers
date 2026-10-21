package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.NOW;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.OTHER_ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.organization;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService")
class OrganizationServiceTest {

    private static final UUID THIRD_ORG_ID = UUID.fromString("c0ffee00-1234-4abc-9def-56789abcdef0");

    @Mock
    private OrganizationRepository repository;
    @Mock
    private ApplicationEventPublisher publisher;

    private OrganizationService service;

    private Organization debate;
    private Organization coding;
    private Organization volunteers;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(repository, publisher, FIXED_CLOCK);
        debate = organization(ORG_ID, "Дебатний клуб НаУКМА", "debate-club");
        coding = organization(OTHER_ORG_ID, "Клуб програмування", "coding-club");
        volunteers = organization(THIRD_ORG_ID, "Волонтерський рух", "volunteers");
    }

    @Nested
    @DisplayName("list")
    class ListOrganizations {

        @ParameterizedTest(name = "порожній запит [{0}] повертає всі організації за назвою")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void blankQueryReturnsAllSortedByName(String query) {
            when(repository.findAll()).thenReturn(List.of(debate, coding, volunteers));

            OffsetPage<Organization> page = service.list(query, 20, 0);

            assertThat(page.data()).extracting(Organization::name)
                    .containsExactly("Волонтерський рух", "Дебатний клуб НаУКМА", "Клуб програмування");
            assertThat(page.pagination().total()).isEqualTo(3);
        }

        @Test
        @DisplayName("пошук нечутливий до регістру й обрізає пробіли навколо запиту")
        void searchIsCaseInsensitiveAndTrimmed() {
            when(repository.findAll()).thenReturn(List.of(debate, coding, volunteers));

            OffsetPage<Organization> page = service.list("  КЛУБ ", 20, 0);

            assertThat(page.data()).containsExactly(debate, coding);
        }

        @Test
        @DisplayName("пошук знаходить організацію за slug")
        void searchMatchesSlug() {
            when(repository.findAll()).thenReturn(List.of(debate, coding, volunteers));

            assertThat(service.list("coding", 20, 0).data()).containsExactly(coding);
        }
    }

    @Test
    @DisplayName("get: неіснуюча організація → ORGANIZATION_NOT_FOUND")
    void getMissingThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(missing))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND))
                .hasMessageContaining(missing.toString());
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("обрізає назву, вимикає автопублікацію, бере час із годинника")
        void createsWithDefaults() {
            when(repository.existsBySlug("fido", null)).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Organization created = service.create(new OrganizationCreateRequest(
                    "  FIDo  ", "fido", "IT-спільнота", "fido_naukma", "fido@ukma.edu.ua"));

            assertThat(created.id()).isNotNull();
            assertThat(created.name()).isEqualTo("FIDo");
            assertThat(created.autoPublish()).isFalse();
            assertThat(created.createdAt()).isEqualTo(NOW);
            assertThat(created.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("зайнятий slug → SLUG_ALREADY_EXISTS і нічого не зберігається")
        void duplicateSlugIsRejected() {
            when(repository.existsBySlug("debate-club", null)).thenReturn(true);

            assertThatThrownBy(() -> service.create(new OrganizationCreateRequest(
                    "Ще один дебатний клуб", "debate-club", null, null, null)))
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.code()).isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
                        assertThat(ex.details()).singleElement()
                                .satisfies(v -> assertThat(v.field()).isEqualTo("slug"));
                    });
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("змінює лише передані поля, createdAt не чіпає, slug не перевіряє")
        void partialUpdateKeepsOtherFields() {
            when(repository.findById(ORG_ID)).thenReturn(Optional.of(debate));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Organization updated = service.update(ORG_ID,
                    new OrganizationUpdateRequest(null, null, "Новий опис", null, null, true));

            assertThat(updated.description()).isEqualTo("Новий опис");
            assertThat(updated.autoPublish()).isTrue();
            assertThat(updated).usingRecursiveComparison()
                    .ignoringFields("description", "autoPublish", "updatedAt")
                    .isEqualTo(debate);
            assertThat(updated.updatedAt()).isEqualTo(NOW);
            verify(repository, never()).existsBySlug(anyString(), any());
        }

        @Test
        @DisplayName("перевірка slug виключає саму організацію")
        void slugCheckExcludesOwnId() {
            when(repository.findById(ORG_ID)).thenReturn(Optional.of(debate));
            when(repository.existsBySlug("debate-club", ORG_ID)).thenReturn(false);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Organization updated = service.update(ORG_ID,
                    new OrganizationUpdateRequest(null, "debate-club", null, null, null, null));

            assertThat(updated.slug()).isEqualTo("debate-club");
            verify(repository).existsBySlug(eq("debate-club"), eq(ORG_ID));
            verify(repository, never()).existsBySlug(anyString(), isNull());
        }

        @Test
        @DisplayName("неіснуюча організація → ORGANIZATION_NOT_FOUND, без збереження")
        void updateMissingThrows() {
            UUID missing = UUID.randomUUID();
            when(repository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(missing,
                    new OrganizationUpdateRequest("Назва", null, null, null, null, null)))
                    .isInstanceOf(ApiException.class);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("видаляє й публікує OrganizationDeleted для каскаду подій")
        void deletePublishesEvent() {
            when(repository.deleteById(ORG_ID)).thenReturn(true);

            service.delete(ORG_ID);

            ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
            verify(publisher).publishEvent(published.capture());
            assertThat(published.getValue()).isEqualTo(new OrganizationDeleted(ORG_ID));
        }

        @Test
        @DisplayName("неіснуюча організація → 404 і жодної події")
        void deleteMissingDoesNotPublish() {
            when(repository.deleteById(ORG_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(ORG_ID))
                    .isInstanceOfSatisfying(ApiException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
            verifyNoInteractions(publisher);
        }
    }
}