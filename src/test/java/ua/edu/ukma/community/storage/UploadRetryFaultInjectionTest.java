package ua.edu.ukma.community.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Ін'єкція збою в завантаження картинки: Supabase Storage відповідає помилками, а тест перевіряє
 * обмежені повтори з відступом і повним джитером (бонус 1). Пауза, джитер і годинник підмінені,
 * тому тест не чекає насправді.
 */
@DisplayName("Повтори завантаження з відступом і джитером (ін'єкція збою)")
class UploadRetryFaultInjectionTest {

    private static final String PROJECT = "https://demo.supabase.co";
    private static final String OBJECT_URL = PROJECT + "/storage/v1/object/images/organizations/42/logo-abc.png";
    private static final String KEY = "organizations/42/logo-abc.png";
    private static final byte[] DATA = {1, 2, 3};
    /** Будь-яка частка в межах відступу: цей тест перевіряє одужання, а не сам джитер. */
    private static final double MID_JITTER = 0.5;

    private final List<Duration> pauses = new ArrayList<>();
    private MockRestServiceServer server;
    /** Фальшивий годинник: рухається лише там, де тест цього хоче. */
    private long nanos;

    /** @param jitter частка відступу, яку «випадково» обирає джитер: 1.0 — максимум, 0.0 — без паузи */
    private SupabaseImageStorage storageWithJitter(double jitter) {
        return storage(jitter, Duration.ZERO);
    }

    /** @param perAttempt скільки «триває» кожна спроба за фальшивим годинником */
    private SupabaseImageStorage storage(double jitter, Duration perAttempt) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DoubleSupplier fixedJitter = () -> jitter;
        LongSupplier clock = () -> {
            long current = nanos;
            nanos += perAttempt.toNanos();
            return current;
        };
        return new SupabaseImageStorage(builder, PROJECT, "sb_secret_test", "images",
                new SupabaseImageStorage.RetrySeams(pauses::add, fixedJitter, clock));
    }

    @Test
    @DisplayName("сервер постійно віддає 500: рівно UPLOAD_ATTEMPTS спроб, далі 503 користувачу")
    void retriesAreBoundedAndThenFailFast() {
        SupabaseImageStorage storage = storageWithJitter(1.0);
        server.expect(ExpectedCount.times(SupabaseImageStorage.UPLOAD_ATTEMPTS), requestTo(OBJECT_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> storage.upload(KEY, DATA, "image/png"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.STORAGE_UNAVAILABLE));

        server.verify();
        assertThat(pauses).hasSize(SupabaseImageStorage.UPLOAD_ATTEMPTS - 1);
    }

    @Test
    @DisplayName("відступ подвоюється і не перевищує стелі; джитер лишає паузу в його межах")
    void backoffIsExponentialAndJitterStaysWithinIt() {
        SupabaseImageStorage atCeiling = storageWithJitter(1.0);
        server.expect(ExpectedCount.times(SupabaseImageStorage.UPLOAD_ATTEMPTS), requestTo(OBJECT_URL))
                .andRespond(withServerError());
        assertThatThrownBy(() -> atCeiling.upload(KEY, DATA, "image/png")).isInstanceOf(ApiException.class);

        // при джитері 1.0 пауза дорівнює самому відступу: 100 мс, потім 200 мс
        assertThat(pauses).containsExactly(SupabaseImageStorage.BACKOFF_BASE,
                SupabaseImageStorage.BACKOFF_BASE.multipliedBy(2));
        assertThat(pauses).allSatisfy(pause ->
                assertThat(pause).isLessThanOrEqualTo(SupabaseImageStorage.BACKOFF_CAP));

        pauses.clear();
        SupabaseImageStorage atFloor = storageWithJitter(0.0);
        server.expect(ExpectedCount.times(SupabaseImageStorage.UPLOAD_ATTEMPTS), requestTo(OBJECT_URL))
                .andRespond(withServerError());
        assertThatThrownBy(() -> atFloor.upload(KEY, DATA, "image/png")).isInstanceOf(ApiException.class);

        // при джитері 0.0 пауза нульова: повний джитер розкидає паузи по всьому діапазону [0, відступ]
        assertThat(pauses).containsExactly(Duration.ZERO, Duration.ZERO);
    }

    @Test
    @DisplayName("тимчасовий збій: після 500 друга спроба успішна, користувач помилки не бачить")
    void transientFailureIsRecovered() {
        SupabaseImageStorage storage = storageWithJitter(MID_JITTER);
        server.expect(ExpectedCount.once(), requestTo(OBJECT_URL)).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(OBJECT_URL)).andRespond(withSuccess());

        String url = storage.upload(KEY, DATA, "image/png");

        server.verify();
        assertThat(url).isEqualTo(PROJECT + "/storage/v1/object/public/images/" + KEY);
        assertThat(pauses).hasSize(1);
    }

    @Test
    @DisplayName("помилка клієнта не повторюється: 403 → рівно одна спроба")
    void clientErrorIsNotRetried() {
        SupabaseImageStorage storage = storageWithJitter(1.0);
        server.expect(ExpectedCount.once(), requestTo(OBJECT_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> storage.upload(KEY, DATA, "image/png"))
                .isInstanceOf(ApiException.class);

        server.verify();
        assertThat(pauses).isEmpty();
    }

    @Test
    @DisplayName("вичерпане вікно повторів: друга спроба не робиться, хоч ліміт спроб ще не досягнуто")
    void retryWindowStopsFurtherAttempts() {
        // кожна спроба «триває» все вікно: повторювати запит, що вже з'їв бюджет, означало б
        // змусити користувача чекати ще стільки само
        SupabaseImageStorage storage = storage(1.0, SupabaseImageStorage.RETRY_WINDOW);
        server.expect(ExpectedCount.once(), requestTo(OBJECT_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> storage.upload(KEY, DATA, "image/png"))
                .isInstanceOf(ApiException.class);

        server.verify();
        assertThat(pauses).isEmpty();
    }

    @Test
    @DisplayName("повтор безпечний, бо запит ідемпотентний: заголовок x-upsert присутній")
    void uploadIsIdempotentByUpsertHeader() {
        SupabaseImageStorage storage = storageWithJitter(1.0);
        server.expect(ExpectedCount.once(), requestTo(OBJECT_URL))
                .andExpect(header("x-upsert", "true"))
                .andRespond(withSuccess());

        storage.upload(KEY, DATA, "image/png");

        server.verify();
    }
}
