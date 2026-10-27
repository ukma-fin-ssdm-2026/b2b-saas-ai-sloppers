package ua.edu.ukma.community.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.error.FieldViolation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Перевіряє завантажений файл і кладе його в {@link ImageStorage}.
 */
@Service
public class ImageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final int RANDOM_SUFFIX_LENGTH = 12;

    private final ImageStorage storage;

    public ImageService(ImageStorage storage) {
        this.storage = storage;
    }

    public String store(String folder, String name, MultipartFile file) {
        byte[] data = readValidated(file);
        ImageFormat format = ImageFormat.detect(data).orElseThrow(() -> new ApiException(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Підтримуються лише картинки JPEG, PNG і WebP",
                List.of(new FieldViolation("file", "Невідомий формат файлу", file.getOriginalFilename()))));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, RANDOM_SUFFIX_LENGTH);
        String key = folder + "/" + name + "-" + suffix + "." + format.extension();
        return storage.upload(key, data, format.contentType());
    }

    public void deleteQuietly(String publicUrl) {
        storage.deleteQuietly(publicUrl);
    }

    private static byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file", "Файл порожній", null);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Файл завеликий: не більше 5 МБ",
                    List.of(new FieldViolation("file", "Розмір " + file.getSize() + " байт",
                            file.getOriginalFilename())));
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
