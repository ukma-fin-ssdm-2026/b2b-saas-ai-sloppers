package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ua.edu.ukma.community.storage.SupabaseImageStorage;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static ua.edu.ukma.community.storage.SupabaseImageStorage.MAX_KEYS;
import static ua.edu.ukma.community.storage.SupabaseImageStorage.PAGE_SIZE;

/**
 * Lab 09, R2 — <b>граничні випадки</b> обходу папки в Supabase Storage.
 *
 * <p>До виправлення {@code collectKeys} мав {@code while (true)}: якщо Supabase ігнорує {@code offset}
 * і щоразу віддає ту саму повну сторінку, цикл не завершувався, а видалення організації висіло.
 * Тепер обхід завжди закінчується, а сервер не отримує жодного зайвого запиту
 * ({@link MockRestServiceServer#verify()} падає на кожному неочікуваному).
 */
@DisplayName("Надійність R2: обхід папки в сховищі завжди завершується")
class StorageCleanupBoundaryTest {

    private static final String PROJECT = "https://demo.supabase.co";
    private static final String LIST = PROJECT + "/storage/v1/object/list/images";
    private static final String DELETE = PROJECT + "/storage/v1/object/images";
    private static final String FOLDER = "organizations/42";
    /** Якщо обхід зациклиться, тест упаде за тайм-аутом, а не зависне назавжди. */
    private static final Duration GUARD = Duration.ofSeconds(10);

    private MockRestServiceServer server;
    private SupabaseImageStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabaseImageStorage(builder, PROJECT, "sb_secret_test", "images");
    }

    @Test
    @DisplayName("Supabase ігнорує offset (та сама повна сторінка) → 2 запити списку, видаляємо без дублів")
    void repeatedFullPageStopsTheLoop() {
        String samePage = fullPage("logo-");
        server.expect(ExpectedCount.times(2), requestTo(LIST))
                .andRespond(withSuccess(samePage, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DELETE))
                .andExpect(method(HttpMethod.DELETE))
                // кожен файл рівно один раз, хоча сторінку отримали двічі
                .andExpect(jsonPath("$.prefixes.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.prefixes[0]").value(FOLDER + "/logo-0.png"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTimeoutPreemptively(GUARD, () -> storage.deleteFolderQuietly(FOLDER));

        server.verify();
    }

    @Test
    @DisplayName("остання сторінка рівно PAGE_SIZE файлів → ще один запит, порожня відповідь, один DELETE")
    void exactlyFullLastPage() {
        server.expect(requestTo(LIST))
                .andExpect(content().json("{\"offset\":0}"))
                .andRespond(withSuccess(fullPage("a-"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(LIST))
                .andExpect(content().json("{\"offset\":" + PAGE_SIZE + "}"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DELETE))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTimeoutPreemptively(GUARD, () -> storage.deleteFolderQuietly(FOLDER));

        server.verify();
    }

    @Test
    @DisplayName("сховище без кінця віддає нові повні сторінки → зупинка на MAX_KEYS, видаляємо зібране")
    void endlessDistinctPagesStopAtKeyLimit() {
        int pagesUntilLimit = MAX_KEYS / PAGE_SIZE;
        for (int page = 0; page < pagesUntilLimit; page++) {
            server.expect(requestTo(LIST))
                    .andExpect(content().json("{\"offset\":" + page * PAGE_SIZE + "}"))
                    .andRespond(withSuccess(fullPage("p" + page + "-"), MediaType.APPLICATION_JSON));
        }
        server.expect(ExpectedCount.times(pagesUntilLimit), requestTo(DELETE))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertTimeoutPreemptively(GUARD, () -> storage.deleteFolderQuietly(FOLDER));

        server.verify();
    }

    /** Повна сторінка списку: {@link SupabaseImageStorage#PAGE_SIZE} файлів з іменами {@code <prefix>N.png}. */
    private static String fullPage(String prefix) {
        return IntStream.range(0, PAGE_SIZE)
                .mapToObj(i -> "{\"name\":\"" + prefix + i + ".png\",\"id\":\"" + prefix + i + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
