package ua.edu.ukma.community.common.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Регресійний тест до знахідки SpotBugs DM_CONVERT_CASE (ErrorCode.anchor())
 */
class ErrorCodeAnchorTest {

    private Locale original;

    @BeforeEach
    void rememberLocale() {
        original = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void anchorIsStableUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));

        assertEquals("invalid-parameter", ErrorCode.INVALID_PARAMETER.anchor());
        assertEquals("validation-error", ErrorCode.VALIDATION_ERROR.anchor());
        assertEquals("internal-error", ErrorCode.INTERNAL_ERROR.anchor());
    }

    @Test
    void anchorMatchesDocumentedFormatInDefaultLocale() {
        assertEquals("organization-not-found", ErrorCode.ORGANIZATION_NOT_FOUND.anchor());
        assertEquals("slug-already-exists", ErrorCode.SLUG_ALREADY_EXISTS.anchor());
    }
}
