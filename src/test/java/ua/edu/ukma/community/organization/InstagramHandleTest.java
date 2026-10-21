package ua.edu.ukma.community.organization;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstagramHandleTest {

    @ParameterizedTest(name = "[{0}] -> {1}")
    @CsvSource({
            "fido_naukma, fido_naukma",
            "@fido_naukma, fido_naukma",
            "'  @FIdo_NaUKMA ', fido_naukma"
    })
    void normalizesHandle(String raw, String expected) {
        assertEquals(expected, InstagramHandle.normalize(raw));
    }
}