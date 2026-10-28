package ua.edu.ukma.community.auth;

import java.util.Locale;

/**
 * Нормалізація пошти: без пробілів по краях і в нижньому регістрі, щоб {@code Ivan@UKMA.edu.ua}
 * і {@code ivan@ukma.edu.ua} були одним акаунтом.
 */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Рівно один {@code @}, непорожня локальна частина й точний збіг домену (без піддоменів). */
    public static boolean hasDomain(String normalizedEmail, String domain) {
        int at = normalizedEmail.indexOf('@');
        return at > 0
                && at == normalizedEmail.lastIndexOf('@')
                && normalizedEmail.substring(at + 1).equals(domain.toLowerCase(Locale.ROOT));
    }
}
