package ua.edu.ukma.community.auth;


public final class Emails {

    private Emails() {
    }

    private static final int ASCII_LIMIT = 128;

    public static String normalize(String email) {
        if (email == null) {
            return "";
        }
        String trimmed = email.trim();
        StringBuilder result = new StringBuilder(trimmed.length());
        for (char c : trimmed.toCharArray()) {
            result.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return result.toString();
    }

    public static boolean hasDomain(String normalizedEmail, String domain) {
        int at = normalizedEmail.indexOf('@');
        return normalizedEmail.chars().allMatch(c -> c < ASCII_LIMIT)
                && at > 0
                && at == normalizedEmail.lastIndexOf('@')
                && normalizedEmail.substring(at + 1).equals(normalize(domain));
    }
}
