package ua.edu.ukma.community.moderation;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Автофільтр заборонених слів (FR-058).
 *
 * <p>Текст розбивається на слова, і кожне порівнюється зі списком цілком. Підрядок не шукаємо
 * навмисно: слово «кіт» у списку інакше блокувало б «кіткою» і «Нікітою». Через це в списку мають
 * сенс лише окремі слова, не фрази — перевірка на це є в {@code ModerationService.addBannedWord}.
 *
 * <p>Список читається з бази на кожну перевірку: таблиця маленька (десятки рядків), а кеш у пам'яті
 * жив би в кожному поді окремо й розходився б після правок адміністратора.
 */
@Component
public class WordFilter {

    /** Межа слова: усе, що не літера й не цифра, зокрема дефіси, емодзі й пунктуація. */
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final ModerationRepository moderation;

    public WordFilter(ModerationRepository moderation) {
        this.moderation = moderation;
    }

    /**
     * Перше заборонене слово, знайдене в переданих текстах.
     *
     * @return слово зі списку, або {@code empty}, якщо текст чистий
     */
    public Optional<String> firstBanned(String... texts) {
        // нормалізуємо й прочитане з бази: регістр там гарантує застосунок, а не обмеження (див. V8)
        Set<String> banned = moderation.findBannedWords().stream()
                .map(word -> BannedWord.normalize(word.word()))
                .collect(Collectors.toUnmodifiableSet());
        if (banned.isEmpty()) {
            return Optional.empty();
        }
        return words(texts).stream().filter(banned::contains).findFirst();
    }

    /** Слова в порядку появи в тексті: повідомлення про помилку має бути однаковим при повторі. */
    private static List<String> words(String... texts) {
        return Arrays.stream(texts)
                .filter(text -> text != null && !text.isBlank())
                .map(text -> text.toLowerCase(Locale.ROOT))
                .flatMap(NOT_WORD::splitAsStream)
                .filter(word -> !word.isEmpty())
                .distinct()
                .toList();
    }
}
