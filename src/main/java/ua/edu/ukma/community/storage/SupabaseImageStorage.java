package ua.edu.ukma.community.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ua.edu.ukma.community.common.LogSafe;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    static final int PAGE_SIZE = 1000;
    /** Захист від нескінченної рекурсії: у нас максимум organizations/{id}/events/{id}/файл. */
    private static final int MAX_DEPTH = 5;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;
    private final String bucket;
    private final String publicPrefix;

    /**
     * @param builder    білдер з налаштованими таймаутами (див. {@link StorageConfig}); у тестах — з моком сервера
     * @param projectUrl {@code https://<project-ref>.supabase.co}
     */
    public SupabaseImageStorage(RestClient.Builder builder, String projectUrl, String secretKey, String bucket) {
        String base = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        this.client = builder
                .baseUrl(base + "/storage/v1")
                .defaultHeader("apikey", secretKey)
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
            LOG.error("Supabase Storage upload failed for {}/{}: {}", bucket, LogSafe.of(key),
                    LogSafe.of(e.getMessage()));
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Не вдалося зберегти файл у сховищі");
        }
        return publicPrefix + key;
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

    @Override
    public void deleteFolderQuietly(String folder) {
        try {
            List<String> keys = new ArrayList<>();
            collectKeys(folder, 0, keys);
            for (int from = 0; from < keys.size(); from += PAGE_SIZE) {
                List<String> batch = keys.subList(from, Math.min(from + PAGE_SIZE, keys.size()));
                client.method(HttpMethod.DELETE)
                        .uri("/object/" + bucket)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("prefixes", batch))
                        .retrieve()
                        .toBodilessEntity();
            }
            LOG.info("Deleted {} file(s) under {}/{}", keys.size(), bucket, LogSafe.of(folder));
        } catch (RestClientException e) {
            LOG.warn("Supabase Storage folder cleanup failed for {}/{}: {}", bucket, LogSafe.of(folder),
                    LogSafe.of(e.getMessage()));
        }
    }

    /**
     * Збирає повні ключі всіх файлів у папці й підпапках. Supabase повертає вміст лише одного рівня:
     * файли мають {@code id}, а підпапки — {@code id = null}.
     */
    private void collectKeys(String folder, int depth, List<String> keys) {
        if (depth > MAX_DEPTH) {
            return;
        }
        int offset = 0;
        while (true) {
            List<Map<String, Object>> page = client.post()
                    .uri("/object/list/" + bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefix", folder, "limit", PAGE_SIZE, "offset", offset))
                    .retrieve()
                    .body(LIST_TYPE);
            if (page == null || page.isEmpty()) {
                return;
            }
            for (Map<String, Object> item : page) {
                String path = folder + "/" + item.get("name");
                if (item.get("id") == null) {
                    collectKeys(path, depth + 1, keys);
                } else {
                    keys.add(path);
                }
            }
            if (page.size() < PAGE_SIZE) {
                return;
            }
            offset += PAGE_SIZE;
        }
    }

    // ключ збираємо рядком, а не через {змінні} шаблону: інакше RestClient закодує "/" у ключі як %2F
    private String objectPath(String key) {
        return "/object/" + bucket + "/" + key;
    }
}
