package ua.edu.ukma.community.event;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DedupKey {

    private static final int BUCKET_MINUTES = 15;

    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private DedupKey() {
    }

    public static String of(String title, OffsetDateTime startsAt) {
        return normalizeTitle(title) + "|" + roundDown(startsAt);
    }

    private static OffsetDateTime roundDown(OffsetDateTime startsAt) {
        int minute = startsAt.getMinute() / BUCKET_MINUTES * BUCKET_MINUTES;
        return startsAt.withMinute(minute).withSecond(0).withNano(0);
    }

    private static String normalizeTitle(String title) {
        return NOT_LETTER_OR_DIGIT.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
