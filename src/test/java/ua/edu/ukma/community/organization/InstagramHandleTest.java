package ua.edu.ukma.community.organization;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstagramHandleTest {

    @ParameterizedTest(name = "[{0}] -> {1}")
    @CsvSource({
            "fido_naukma, fido_naukma",
            "@fido_naukma, fido_naukma",
            "'  @FIdo_NaUKMA ', fido_naukma",
            "https://www.instagram.com/fido_naukma/, fido_naukma",
            "instagram.com/fido_naukma?igsh=abc, fido_naukma"
    })
    void normalizesHandle(String raw, String expected) {
        assertEquals(expected, InstagramHandle.normalize(raw));
    }

    @ParameterizedTest(name = "[{0}] rejected")
    @ValueSource(strings = {"", "fido naukma", "fido-naukma"})
    void rejectsInvalidHandle(String raw) {
        assertThrows(IllegalArgumentException.class, () -> InstagramHandle.normalize(raw));
    }
}