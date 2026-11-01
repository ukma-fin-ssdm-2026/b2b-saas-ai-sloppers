package ua.edu.ukma.community.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("SupabaseImageStorage: запити до Supabase Storage")
class SupabaseImageStorageTest {

    static final String PROJECT = "https://demo.supabase.co";
    static final String API = PROJECT + "/storage/v1";
    static final String KEY = "sb_secret_test";

    MockRestServiceServer server;
    SupabaseImageStorage storage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabaseImageStorage(builder, PROJECT, KEY, "images");
    }

    @Test
    @DisplayName("upload: ключ у шляху без %2F, секрет лише в apikey, повертає публічний URL")
    void uploadSendsFileAndReturnsPublicUrl() {
        server.expect(requestTo(API + "/object/images/organizations/42/logo-abc.png"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", KEY))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(header("Content-Type", "image/png"))
                .andRespond(withSuccess("{\"Key\":\"images/organizations/42/logo-abc.png\"}",
                        MediaType.APPLICATION_JSON));

        String url = storage.upload("organizations/42/logo-abc.png", new byte[] {1, 2, 3}, "image/png");

        assertThat(url).isEqualTo(PROJECT + "/storage/v1/object/public/images/organizations/42/logo-abc.png");
        server.verify();
    }

    @Test
    @DisplayName("upload: помилка Supabase → 503 STORAGE_UNAVAILABLE")
    void uploadFailureIsStorageUnavailable() {
        server.expect(requestTo(API + "/object/images/x.png"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"Bucket not found\"}"));

        assertThatThrownBy(() -> storage.upload("x.png", new byte[] {1}, "image/png"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("deleteQuietly: видаляє лише свої URL, чужі ігнорує")
    void deleteQuietlyOnlyOwnUrls() {
        server.expect(requestTo(API + "/object/images/organizations/42/logo-abc.png"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"message\":\"Successfully deleted\"}", MediaType.APPLICATION_JSON));

        storage.deleteQuietly(PROJECT + "/storage/v1/object/public/images/organizations/42/logo-abc.png");
        storage.deleteQuietly("https://evil.example.com/whatever.png");
        storage.deleteQuietly(null);

        server.verify();
    }

    @Test
    @DisplayName("deleteFolderQuietly: обходить підпапки й видаляє всі файли одним запитом")
    void deleteFolderWalksSubfoldersAndDeletesAllFiles() {
        server.expect(requestTo(API + "/object/list/images"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"prefix\":\"organizations/42\",\"offset\":0}"))
                .andRespond(withSuccess("""
                        [{"name":"logo-a.png","id":"f1"},{"name":"events","id":null}]""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/object/list/images"))
                .andExpect(content().json("{\"prefix\":\"organizations/42/events\"}"))
                .andRespond(withSuccess("""
                        [{"name":"e1","id":null}]""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/object/list/images"))
                .andExpect(content().json("{\"prefix\":\"organizations/42/events/e1\"}"))
                .andRespond(withSuccess("""
                        [{"name":"image-b.jpg","id":"f2"}]""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/object/images"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("""
                        {"prefixes":["organizations/42/logo-a.png","organizations/42/events/e1/image-b.jpg"]}"""))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        storage.deleteFolderQuietly("organizations/42");

        server.verify();
    }

    @Test
    @DisplayName("deleteFolderQuietly: порожня папка — нічого не видаляємо; помилка сховища не кидає виняток")
    void deleteFolderEmptyAndFailure() {
        server.expect(requestTo(API + "/object/list/images"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        storage.deleteFolderQuietly("organizations/empty");
        server.verify();

        server.reset();
        server.expect(requestTo(API + "/object/list/images"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatNoException().isThrownBy(() -> storage.deleteFolderQuietly("organizations/42"));
        server.verify();
    }
}
