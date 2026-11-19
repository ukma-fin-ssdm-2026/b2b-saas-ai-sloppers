package ua.edu.ukma.community.moderation;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Слово зі списку автофільтра (FR-058). Зберігається в нижньому регістрі, щоб порівняння не залежало
 * від регістру, а {@code UNIQUE} у базі ловив дублікати на кшталт «Спам» / «спам».
 */
public record BannedWord(
        UUID id,
        String word,
        UUID addedBy,
        OffsetDateTime createdAt
) {

    public BannedWord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(word, "word");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static BannedWord of(String word, UUID addedBy, OffsetDateTime now) {
        return new BannedWord(UUID.randomUUID(), normalize(word), addedBy, now);
    }

    public static String normalize(String word) {
        return word.trim().toLowerCase(Locale.ROOT);
    }
}
