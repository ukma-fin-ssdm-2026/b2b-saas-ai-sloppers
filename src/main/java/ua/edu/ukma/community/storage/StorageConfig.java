package ua.edu.ukma.community.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Обирає реалізацію {@link ImageStorage}: Supabase, якщо задані URL проєкту й секретний ключ, інакше заглушку.
 */
@Configuration
public class StorageConfig {

    private static final Logger LOG = LoggerFactory.getLogger(StorageConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    /** Health check має відповідати швидко: Kubernetes і моніторинг не чекають 30 секунд. */
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    ImageStorage imageStorage(@Value("${storage.supabase.url:}") String url,
                              @Value("${storage.supabase.secret-key:}") String secretKey,
                              @Value("${storage.supabase.bucket:images}") String bucket) {
        if (url.isBlank() || secretKey.isBlank()) {
            LOG.warn("Supabase Storage is not configured; image uploads will return 503");
            return new UnavailableImageStorage();
        }
        return new SupabaseImageStorage(builder(CONNECT_TIMEOUT, READ_TIMEOUT), url, secretKey, bucket);
    }

    /**
     * Компонент {@code storage} в {@code /actuator/health}. Без налаштованого Supabase — {@code UNKNOWN}:
     * перевіряти нічого, і загальний статус через це не падає.
     */
    @Bean
    HealthIndicator storageHealthIndicator(@Value("${storage.supabase.url:}") String url,
                                           @Value("${storage.supabase.secret-key:}") String secretKey,
                                           @Value("${storage.supabase.bucket:images}") String bucket) {
        if (url.isBlank() || secretKey.isBlank()) {
            return () -> Health.unknown().withDetail("reason", "Supabase Storage is not configured").build();
        }
        return new SupabaseStorageHealthIndicator(builder(HEALTH_TIMEOUT, HEALTH_TIMEOUT), url, secretKey, bucket);
    }

    private static RestClient.Builder builder(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
