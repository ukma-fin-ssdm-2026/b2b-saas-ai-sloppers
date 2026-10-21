package ua.edu.ukma.community.organization;

import java.util.Locale;

final class InstagramHandle {

    private InstagramHandle() {
    }

    static String normalize(String raw) {
        String handle = raw.strip().toLowerCase(Locale.ROOT);
        return handle.startsWith("@") ? handle.substring(1) : handle;
    }
}