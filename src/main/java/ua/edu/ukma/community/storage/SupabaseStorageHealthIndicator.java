package ua.edu.ukma.community.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ua.edu.ukma.community.common.LogSafe;

/**
 * Перевірка Supabase Storage для {@code /actuator/health} (компонент {@code storage}, Lab 09, бонус 2).
 *
 * <p>Робить один легкий запит {@code GET /storage/v1/bucket/{bucket}} з коротким тайм-аутом
 * (див. {@link StorageConfig}): бакет існує й ключ дійсний → {@code UP}; таймаут, обрив з'єднання,
 * 4xx/5xx → {@code DOWN}. Причину збою пишемо лише в лог, назовні йде тільки статус.
 *
 * <p>Сховище свідомо <b>не</b> входить у групу {@code readiness}: без картинок сайт працює,
 * тому збій Supabase не має виводити под з балансування (graceful degradation).
 */
public class SupabaseStorageHealthIndicator implements HealthIndicator {

    private static final Logger LOG = LoggerFactory.getLogger(SupabaseStorageHealthIndicator.class);

    private final RestClient client;
    private final String bucket;

    /**
     * @param builder    білдер з коротким тайм-аутом; у тестах — з моком сервера
     * @param projectUrl {@code https://<project-ref>.supabase.co}
     */
    public SupabaseStorageHealthIndicator(RestClient.Builder builder, String projectUrl, String secretKey,
                                          String bucket) {
        String base = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        this.client = builder
                .baseUrl(base + "/storage/v1")
                .defaultHeader("apikey", secretKey)
                .build();
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        try {
            client.get()
                    .uri("/bucket/" + bucket)
                    .retrieve()
                    .toBodilessEntity();
            return Health.up().withDetail("bucket", bucket).build();
        } catch (RestClientException e) {
            LOG.warn("Health check: Supabase Storage bucket {} is unavailable: {}", LogSafe.of(bucket),
                    LogSafe.of(e.getMessage()));
            return Health.down().withDetail("bucket", bucket).build();
        }
    }
}
