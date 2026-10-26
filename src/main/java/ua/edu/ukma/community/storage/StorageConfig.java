package ua.edu.ukma.community.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Обирає реалізацію {@link ImageStorage}: Supabase, якщо задані URL проєкту й секретний ключ, інакше заглушку.
 */
@Configuration
public class StorageConfig {

    private static final Logger LOG = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    ImageStorage imageStorage(@Value("${storage.supabase.url:}") String url,
                              @Value("${storage.supabase.secret-key:}") String secretKey,
                              @Value("${storage.supabase.bucket:images}") String bucket) {
        if (url.isBlank() || secretKey.isBlank()) {
            LOG.warn("Supabase Storage is not configured; image uploads will return 503");
            return new UnavailableImageStorage();
        }
        return new SupabaseImageStorage(url, secretKey, bucket);
    }
}
