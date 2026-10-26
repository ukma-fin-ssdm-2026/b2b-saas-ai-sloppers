package ua.edu.ukma.community.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageService: перевірка й збереження картинок")
class ImageServiceTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    @Mock
    ImageStorage storage;

    ImageService service;

    @BeforeEach
    void setUp() {
        service = new ImageService(storage);
    }

    @Test
    @DisplayName("формат визначається за вмістом: JPEG, PNG, WebP")
    void detectsFormatByMagicBytes() {
        assertThat(ImageFormat.detect(JPEG)).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(PNG)).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(WEBP)).contains(ImageFormat.WEBP);
        assertThat(ImageFormat.detect("<svg></svg>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF})).isEmpty();
    }

    @Test
    @DisplayName("PNG зберігається з правильним типом, розширенням і унікальним іменем у папці")
    void storesWithDetectedTypeAndUniqueName() {
        when(storage.upload(anyString(), any(), anyString())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        // клієнт бреше про тип: вирішує вміст, а не заголовок
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", PNG);

        String first = service.store("organizations/42", "logo", file);
        String second = service.store("organizations/42", "logo", file);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).upload(key.capture(), any(byte[].class), eq("image/png"));
        assertThat(key.getAllValues()).allMatch(k -> k.matches("organizations/42/logo-[0-9a-f]{12}\\.png"));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("не картинка → 415, у сховище нічого не йде")
    void rejectsNonImage() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.store("organizations/42", "logo", file))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("порожній файл → 400, більший за 5 МБ → 413")
    void rejectsEmptyAndTooLarge() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        byte[] big = new byte[(int) ImageService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        MockMultipartFile tooLarge = new MockMultipartFile("file", "x.png", "image/png", big);

        assertThatThrownBy(() -> service.store("f", "logo", empty))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> service.store("f", "logo", tooLarge))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("без налаштувань сховища завантаження дає 503")
    void unavailableStorageFails() {
        ImageService unconfigured = new ImageService(new UnavailableImageStorage());
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", PNG);

        assertThatThrownBy(() -> unconfigured.store("f", "logo", file))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.STORAGE_UNAVAILABLE);
    }
}
