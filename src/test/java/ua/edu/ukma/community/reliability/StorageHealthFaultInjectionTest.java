package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ua.edu.ukma.community.storage.SupabaseStorageHealthIndicator;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Lab 09, бонус 2 — health check зовнішньої залежності з <b>fault injection</b>:
 * дубль Supabase відповідає успішно, повертає 503 або обриває з'єднання за тайм-аутом.
 */
@DisplayName("Надійність, бонус: health check Supabase Storage")
class StorageHealthFaultInjectionTest {

    private static final String PROJECT = "https://demo.supabase.co";
    private static final String BUCKET_URL = PROJECT + "/storage/v1/bucket/images";
    private static final String KEY = "sb_secret_test";

    private MockRestServiceServer server;
    private SupabaseStorageHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        indicator = new SupabaseStorageHealthIndicator(builder, PROJECT, KEY, "images");
    }

    @Test
    @DisplayName("бакет доступний → UP")
    void availableStorageIsUp() {
        server.expect(requestTo(BUCKET_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", KEY))
                .andRespond(withSuccess("{\"id\":\"images\",\"public\":true}", MediaType.APPLICATION_JSON));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        server.verify();
    }

    @Test
    @DisplayName("Supabase відповідає 503 → DOWN, без винятку назовні")
    void serverErrorIsDown() {
        server.expect(requestTo(BUCKET_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        // назовні лише статус і назва бакета: жодних повідомлень про помилку чи ключів
        assertThat(health.getDetails()).containsOnlyKeys("bucket");
    }

    @Test
    @DisplayName("з'єднання обривається за тайм-аутом → DOWN")
    void timeoutIsDown() {
        server.expect(requestTo(BUCKET_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
