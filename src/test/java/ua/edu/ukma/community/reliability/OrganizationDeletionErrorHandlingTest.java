package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.organization.OrganizationCreateRequest;
import ua.edu.ukma.community.organization.OrganizationService;
import ua.edu.ukma.community.storage.ImageStorage;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Lab 09, R2 — <b>обробка помилок</b>: сховище файлів падає або «висить» під час видалення організації.
 *
 * <p>Обрана реакція — <b>fail safe</b>: організація завжди видаляється з БД і запит завершується одразу;
 * прибирання файлів іде у фоні, а невдача записується в лог (файли-сироти прибирають вручну).
 * Сервіс не падає і не повертає 500 через проблему стороннього сховища.
 */
@SpringBootTest
@ActiveProfiles("inmemory")
@DisplayName("Надійність R2: видалення організації не залежить від сховища файлів")
class OrganizationDeletionErrorHandlingTest {

    private static final Duration FAST = Duration.ofSeconds(1);
    private static final long BACKGROUND_WAIT_MS = 5_000;
    private static final int SLUG_SUFFIX = 8;

    @Autowired
    OrganizationService organizations;

    @MockitoBean
    ImageStorage storage;

    private final CountDownLatch storageReleased = new CountDownLatch(1);

    @AfterEach
    void releaseStorage() {
        storageReleased.countDown();
    }

    @Test
    @DisplayName("сховище кидає виняток → організацію видалено, запит не падає")
    void storageFailureDoesNotBreakDeletion() {
        doThrow(new IllegalStateException("Supabase is down")).when(storage).deleteFolderQuietly(anyString());
        Organization org = newOrganization();

        assertTimeoutPreemptively(FAST, () -> organizations.delete(org.id()));

        assertGone(org);
        verify(storage, timeout(BACKGROUND_WAIT_MS)).deleteFolderQuietly("organizations/" + org.id());
    }

    @Test
    @DisplayName("сховище «висить» → видалення все одно відповідає швидко, прибирання чекає у фоні")
    void hangingStorageDoesNotBlockDeletion() {
        doAnswer(invocation -> storageReleased.await(BACKGROUND_WAIT_MS, TimeUnit.MILLISECONDS))
                .when(storage).deleteFolderQuietly(anyString());
        Organization first = newOrganization();
        Organization second = newOrganization();

        assertTimeoutPreemptively(FAST, () -> organizations.delete(first.id()));
        // монітор OrganizationService вільний: друге видалення не чекає на перше прибирання
        assertTimeoutPreemptively(FAST, () -> organizations.delete(second.id()));

        assertGone(first);
        assertGone(second);
        storageReleased.countDown();
        verify(storage, timeout(BACKGROUND_WAIT_MS)).deleteFolderQuietly("organizations/" + second.id());
    }

    private Organization newOrganization() {
        String slug = "reliability-" + UUID.randomUUID().toString().substring(0, SLUG_SUFFIX);
        return organizations.create(new OrganizationCreateRequest("Тестовий клуб " + slug, slug, null, null,
                null, null));
    }

    private void assertGone(Organization org) {
        assertThatThrownBy(() -> organizations.get(org.id()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ORGANIZATION_NOT_FOUND));
    }
}
