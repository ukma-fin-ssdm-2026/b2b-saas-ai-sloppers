package ua.edu.ukma.community.auth;

import java.util.Locale;


public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean hasDomain(String normalizedEmail, String domain) {
        int at = normalizedEmail.indexOf('@');
        return at > 0
                && at == normalizedEmail.lastIndexOf('@')
                && normalizedEmail.substring(at + 1).equals(domain.toLowerCase(Locale.ROOT));
    }
}
