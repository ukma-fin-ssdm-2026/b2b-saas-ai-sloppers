package ua.edu.ukma.community.storage;

/**
 * Файлове сховище для картинок. Основна реалізація — Supabase Storage ({@link SupabaseImageStorage}).
 */
public interface ImageStorage {

    /**
     * Зберігає файл під ключем {@code key} (шлях усередині бакета) і повертає його публічний URL.
     */
    String upload(String key, byte[] data, String contentType);

    void deleteQuietly(String publicUrl);

    void deleteFolderQuietly(String folder);
}
