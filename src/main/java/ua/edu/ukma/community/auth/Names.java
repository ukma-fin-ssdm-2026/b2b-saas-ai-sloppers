package ua.edu.ukma.community.auth;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Правила для імені та прізвища (FR-069). */
public final class Names {

    public static final int MAX_LENGTH = 50;

    /**
     * Літери будь-якої абетки, апостроф (' ʼ ’), дефіс і пробіл; починається з літери.
     * Достатньо для «Анна-Марія», «Ольга-Лʼвівна», «O'Brien», «Ван Гог».
     */
    public static final String REGEX = "^\\s*\\p{L}[\\p{L}\\p{M}'ʼ’ -]*\\s*$";
    public static final String MESSAGE = "Лише літери, апостроф, дефіс і пробіл; починається з літери";

    private Names() {
    }

    /** «Ім'я Прізвище» з того, що задано; {@code null}, якщо нічого. */
    public static String full(String firstName, String lastName) {
        String joined = Stream.of(firstName, lastName).filter(Objects::nonNull).collect(Collectors.joining(" "));
        return joined.isEmpty() ? null : joined;
    }

    /** Обрізає пробіли по краях і стискає повтори всередині; порожній рядок → {@code null}. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip().replaceAll("\\s+", " ");
    }
}
