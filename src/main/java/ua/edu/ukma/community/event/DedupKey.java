package ua.edu.ukma.community.event;

import java.time.OffsetDateTime;
import java.util.Locale;

public final class DedupKey {

    private DedupKey() {
    }

    public static String of(String title, OffsetDateTime startsAt) {
        return normalizeTitle(title) + "|" + startsAt;
    }

    private static String normalizeTitle(String title) {
        return title.toLowerCase(Locale.ROOT);
    }
}
