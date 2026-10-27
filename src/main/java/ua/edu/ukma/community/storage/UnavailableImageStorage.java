package ua.edu.ukma.community.storage;

import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;


public class UnavailableImageStorage implements ImageStorage {

    @Override
    public String upload(String key, byte[] data, String contentType) {
        throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                "Сховище файлів не налаштоване: задайте SUPABASE_URL і SUPABASE_SECRET_KEY");
    }

    @Override
    public void deleteQuietly(String publicUrl) {
    }
}
