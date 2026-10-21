package ua.edu.ukma.community.organization;

final class InstagramHandle {

    private InstagramHandle() {
    }

    static String normalize(String raw) {
        return raw.startsWith("@") ? raw.substring(1) : raw;
    }
}