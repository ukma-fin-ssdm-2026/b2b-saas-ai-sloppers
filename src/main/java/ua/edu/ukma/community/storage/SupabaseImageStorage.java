package ua.edu.ukma.community.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Duration;

/**
 * Картинки в Supabase Storage через його REST API.
 * Бакет має бути публічним, щоб фронтенд міг показувати картинки за прямим посиланням.
 * Секретний ключ ({@code sb_secret_...}) передається лише в заголовку {@code apikey}:
 * це не JWT, тому в {@code Authorization: Bearer} його класти не можна.
 */
public class SupabaseImageStorage implements ImageStorage {

    private static final Logger LOG = LoggerFactory.getLogger(SupabaseImageStorage.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String CACHE_CONTROL = "max-age=31536000";

    private final RestClient client;
    private final String bucket;
    private final String publicPrefix;

    public SupabaseImageStorage(String projectUrl, String secretKey, String bucket) {
        String base = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.client = RestClient.builder()
                .baseUrl(base + "/storage/v1")
                .defaultHeader("apikey", secretKey)
                .requestFactory(requestFactory)
                .build();
        this.bucket = bucket;
        this.publicPrefix = base + "/storage/v1/object/public/" + bucket + "/";
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        try {
            client.post()
                    .uri(objectPath(key))
                    .header("Content-Type", contentType)
                    .header("cache-control", CACHE_CONTROL)
                    .body(data)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            LOG.error("Supabase Storage upload failed for {}/{}: {}", bucket, key, e.getMessage());
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Не вдалося зберегти файл у сховищі");
        }
        return publicPrefix + key;
    }

    private String objectPath(String key) {
        return "/object/" + bucket + "/" + key;
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
            LOG.warn("Supabase Storage delete failed for {}/{}: {}", bucket, key, e.getMessage());
        }
    }
}
