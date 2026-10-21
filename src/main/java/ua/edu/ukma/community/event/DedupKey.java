package ua.edu.ukma.community.event;

import java.time.OffsetDateTime;

public final class DedupKey {

    private DedupKey() {
    }

    public static String of(String title, OffsetDateTime startsAt) {
        return title + "|" + startsAt;
    }
}
