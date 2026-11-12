package ua.edu.ukma.community.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ua.edu.ukma.community.common.LogSafe;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Картинки в Supabase Storage через його REST API.
 * Бакет має бути публічним, щоб фронтенд міг показувати картинки за прямим посиланням.
 * Секретний ключ ({@code sb_secret_...}) передається лише в заголовку {@code apikey}:
 * це не JWT, тому в {@code Authorization: Bearer} його класти не можна.
 */
public class SupabaseImageStorage implements ImageStorage {

    private static final Logger LOG = LoggerFactory.getLogger(SupabaseImageStorage.class);
    // імена файлів унікальні, тож кешувати можна довго
    private static final String CACHE_CONTROL = "max-age=31536000";
    /** Скільки записів просимо за один запит списку і скільки видаляємо за один запит видалення. */
    public static final int PAGE_SIZE = 1000;
    /** Межа сторінок на одну папку: 50 × 1000 файлів — у сотні разів більше, ніж має організація. */
    public static final int MAX_PAGES_PER_FOLDER = 50;
    /** Межа ключів за одне прибирання, щоб список не з'їв пам'ять поду. */
    public static final int MAX_KEYS = 10_000;
    /** Захист від нескінченної рекурсії: у нас максимум organizations/{id}/events/{id}/файл. */
    private static final int MAX_DEPTH = 5;
    /** Перша спроба плюс два повтори. Межа обов'язкова: без неї повтори самі стають відмовою. */
    public static final int UPLOAD_ATTEMPTS = 3;
    /** Базовий відступ; далі подвоюється: 100 мс, 200 мс, … */
    public static final Duration BACKOFF_BASE = Duration.ofMillis(100);
    /** Стеля відступу, щоб повтори не затягнули запит користувача. */
    public static final Duration BACKOFF_CAP = Duration.ofSeconds(2);
    /**
     * Сукупне вікно на повтори. Обмеження лише за кількістю спроб тут недостатнє: read-тайм-аут
     * сховища — 30 с, тож три спроби означали б до 90 с очікування замість 30. Коли вікно вичерпано,
     * решта спроб не робиться — а запит, що вже з'їв увесь бюджет, і не був тимчасовим збоєм.
     */
    public static final Duration RETRY_WINDOW = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;
    private final String bucket;
    private final String publicPrefix;
    private final RetrySeams retry;

    /** Пауза між спробами. Окремий тип, щоб тест надійності не спав насправді. */
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /**
     * Підміни для тестів надійності: пауза, джитер і джерело часу. Той самий підхід, що й мок
     * сервера замість справжнього HTTP.
     *
     * @param sleeper  пауза між спробами
     * @param jitter   частка поточного відступу в діапазоні {@code [0, 1)}
     * @param nanoTime джерело часу для вікна повторів
     */
    public record RetrySeams(Sleeper sleeper, DoubleSupplier jitter, LongSupplier nanoTime) {

        public static RetrySeams real() {
            return new RetrySeams(duration -> Thread.sleep(duration.toMillis()),
                    () -> ThreadLocalRandom.current().nextDouble(), System::nanoTime);
        }
    }

    /**
     * @param builder    білдер з налаштованими таймаутами (див. {@link StorageConfig}); у тестах — з моком сервера
     * @param projectUrl {@code https://<project-ref>.supabase.co}
     */
    public SupabaseImageStorage(RestClient.Builder builder, String projectUrl, String secretKey, String bucket) {
        this(builder, projectUrl, secretKey, bucket, RetrySeams.real());
    }

    /** Та сама побудова, але з підміненими паузою, джитером і часом — для тестів надійності. */
    public SupabaseImageStorage(RestClient.Builder builder, String projectUrl, String secretKey, String bucket,
                                RetrySeams retry) {
        this.retry = retry;
        String base = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        this.client = builder
                .baseUrl(base + "/storage/v1")
                .defaultHeader("apikey", secretKey)
                .build();
        this.bucket = bucket;
        this.publicPrefix = base + "/storage/v1/object/public/" + bucket + "/";
    }

    /**
     * Шари для цього виклику складені в порядку §3.5: тайм-аути на з'єднання і читання задає
     * {@link StorageConfig}, поверх них — обмежені повтори з відступом і повним джитером, а шлях
     * деградації — швидка відмова з {@code 503} і зрозумілим повідомленням.
     *
     * <p>Повторювати запис можна лише тому, що виклик ідемпотентний: заголовок {@code x-upsert}
     * перетворює «створити» на «створити або перезаписати», тож повторна доставка тієї самої
     * операції дає той самий результат. Ключ містить випадковий суфікс і унікальний на кожен виклик,
     * тому upsert не маскує справжнього конфлікту імен.
     */
    @Override
    public String upload(String key, byte[] data, String contentType) {
        long startedAt = retry.nanoTime().getAsLong();
        for (int attempt = 1; ; attempt++) {
            try {
                client.post()
                        .uri(objectPath(key))
                        .header("Content-Type", contentType)
                        .header("cache-control", CACHE_CONTROL)
                        .header("x-upsert", "true")
                        .body(data)
                        .retrieve()
                        .toBodilessEntity();
                return publicPrefix + key;
            } catch (RestClientException e) {
                if (attempt == UPLOAD_ATTEMPTS || !worthRetrying(e) || windowSpent(startedAt)) {
                    LOG.error("Supabase Storage upload failed for {}/{} after {} attempt(s): {}",
                            bucket, LogSafe.of(key), attempt, LogSafe.of(e.getMessage()));
                    throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Не вдалося зберегти файл у сховищі");
                }
                LOG.warn("Supabase Storage upload attempt {} of {} for {}/{} failed, retrying: {}",
                        attempt, UPLOAD_ATTEMPTS, bucket, LogSafe.of(key), LogSafe.of(e.getMessage()));
                pauseBefore(attempt);
            }
        }
    }

    /**
     * Повторюємо лише те, що може стати успішним: помилки сервера й мережеві збої разом із тайм-аутами.
     * {@code 4xx} означає неправильний запит, і повтор його не виправить.
     */
    private static boolean worthRetrying(RestClientException e) {
        return e instanceof HttpServerErrorException || e instanceof ResourceAccessException;
    }

    /**
     * Повний джитер: пауза — випадкове число від нуля до поточного відступу. Без джитера клієнти
     * з однаковим експоненційним відступом синхронізуються і б'ють сховище хвилями.
     */
    private boolean windowSpent(long startedAt) {
        return retry.nanoTime().getAsLong() - startedAt >= RETRY_WINDOW.toNanos();
    }

    private void pauseBefore(int attempt) {
        long exponential = BACKOFF_BASE.toMillis() << (attempt - 1);
        long ceiling = Math.min(exponential, BACKOFF_CAP.toMillis());
        try {
            retry.sleeper().sleep(Duration.ofMillis((long) (retry.jitter().getAsDouble() * ceiling)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Збереження файлу перервано");
        }
    }

    @Override
    public void deleteQuietly(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(publicPrefix)) {
            return;
        }
        String key = publicUrl.substring(publicPrefix.length());
        try {
            client.delete()
                    .uri(objectPath(key))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            LOG.warn("Supabase Storage delete failed for {}/{}: {}", bucket, LogSafe.of(key),
                    LogSafe.of(e.getMessage()));
        }
    }

    /**
     * Видаляє всі файли папки (Lab 09, R2, fail safe). Ніколи не кидає {@link RestClientException}:
     * організація вже видалена з БД, а файли, які не вдалося прибрати, лишаються «сиротами»
     * і записуються в WARN, щоб їх можна було прибрати вручну.
     */
    @Override
    public void deleteFolderQuietly(String folder) {
        Listing listing = new Listing();
        int deleted = 0;
        try {
            collectKeys(folder, 0, listing);
            List<String> keys = List.copyOf(listing.keys);
            for (int from = 0; from < keys.size(); from += PAGE_SIZE) {
                List<String> batch = keys.subList(from, Math.min(from + PAGE_SIZE, keys.size()));
                client.method(HttpMethod.DELETE)
                        .uri("/object/" + bucket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("prefixes", batch))
                        .retrieve()
                        .toBodilessEntity();
                deleted += batch.size();
            }
        } catch (RestClientException e) {
            LOG.warn("Supabase Storage cleanup of {}/{} failed after {} deleted file(s); "
                            + "remaining files are orphans: {}",
                    bucket, LogSafe.of(folder), deleted, LogSafe.of(e.getMessage()));
            return;
        }
        if (listing.incomplete) {
            LOG.warn("Supabase Storage cleanup of {}/{} stopped early ({}): deleted {} file(s), "
                    + "the rest are orphans", bucket, LogSafe.of(folder), listing.reason, deleted);
        } else {
            LOG.info("Deleted {} file(s) under {}/{}", deleted, bucket, LogSafe.of(folder));
        }
    }

    /**
     * Збирає повні ключі всіх файлів у папці й підпапках. Supabase повертає вміст лише одного рівня:
     * файли мають {@code id}, а підпапки — {@code id = null}.
     *
     * <p>Раніше тут був {@code while (true)}: якби Supabase проігнорував {@code offset} і віддавав ту саму
     * повну сторінку, цикл ніколи б не завершився. Тепер вихід гарантований трьома межами:
     * не більше {@link #MAX_PAGES_PER_FOLDER} сторінок на папку, не більше {@link #MAX_KEYS} ключів
     * загалом і зупинка, якщо сторінка повторює попередню.
     */
    private void collectKeys(String folder, int depth, Listing listing) {
        if (depth > MAX_DEPTH) {
            listing.skip("folder nesting deeper than " + MAX_DEPTH);
            return;
        }
        List<Object> previousPage = List.of();
        for (int pageNo = 0; pageNo < MAX_PAGES_PER_FOLDER && !listing.truncated; pageNo++) {
            List<Map<String, Object>> page = client.post()
                    .uri("/object/list/" + bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefix", folder, "limit", PAGE_SIZE, "offset", pageNo * PAGE_SIZE))
                    .retrieve()
                    .body(LIST_TYPE);
            if (page == null || page.isEmpty()) {
                return;
            }
            List<Object> names = page.stream().map(item -> item.get("name")).toList();
            if (names.equals(previousPage)) {
                listing.stop("storage returned the same page twice for " + folder);
                return;
            }
            previousPage = names;
            for (Map<String, Object> item : page) {
                String path = folder + "/" + item.get("name");
                if (item.get("id") == null) {
                    collectKeys(path, depth + 1, listing);
                } else {
                    listing.add(path);
                }
                if (listing.truncated) {
                    return;
                }
            }
            if (page.size() < PAGE_SIZE) {
                return;
            }
        }
        if (!listing.truncated) {
            listing.stop("more than " + MAX_PAGES_PER_FOLDER + " pages in " + folder);
        }
    }

    /**
     * Результат обходу: унікальні ключі в порядку знаходження. {@code truncated} — обхід зупинено на межі,
     * {@code incomplete} — якісь файли не потрапили в список (з причиною для логу).
     */
    private static final class Listing {
        private final Set<String> keys = new LinkedHashSet<>();
        private boolean truncated;
        private boolean incomplete;
        private String reason;

        void add(String key) {
            keys.add(key);
            if (keys.size() >= MAX_KEYS) {
                stop("reached " + MAX_KEYS + " files");
            }
        }

        void stop(String why) {
            truncated = true;
            skip(why);
        }

        void skip(String why) {
            incomplete = true;
            reason = why;
        }
    }

    // ключ збираємо рядком, а не через {змінні} шаблону: інакше RestClient закодує "/" у ключі як %2F
    private String objectPath(String key) {
        return "/object/" + bucket + "/" + key;
    }
}
