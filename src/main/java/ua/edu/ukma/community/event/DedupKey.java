package ua.edu.ukma.community.event;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DedupKey {

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private DedupKey() {
    }

    public static String of(String title, OffsetDateTime startsAt) {
        return normalizeTitle(title) + "|" + startsAt;
    }

    private static String normalizeTitle(String title) {
        return NOT_LETTER_OR_DIGIT.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
