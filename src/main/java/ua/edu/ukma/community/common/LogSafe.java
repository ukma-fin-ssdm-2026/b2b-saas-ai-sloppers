package ua.edu.ukma.community.common;

import java.util.regex.Pattern;

/**
 * Очищення значень перед записом у лог. Переноси рядка в даних ззовні (пошта, відповідь стороннього сервісу)
 * дали б змогу підробити в лозі зайвий рядок (log injection, CWE-117), тож замінюємо їх на {@code _}.
 */
public final class LogSafe {

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n\\u0085\\u2028\\u2029]");

    private LogSafe() {
    }

    public static String of(Object value) {
        return value == null ? "null" : LINE_BREAKS.matcher(value.toString()).replaceAll("_");
    }
}
