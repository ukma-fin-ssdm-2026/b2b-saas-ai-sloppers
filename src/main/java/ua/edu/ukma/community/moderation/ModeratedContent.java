package ua.edu.ukma.community.moderation;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Усе, що модерації потрібно знати про сам контент. Інтерфейс тримає межу між модулями: модерація
 * не знає ні про {@code Advert}, ні про {@code Reply}, а дошка не знає про скарги й бани. Єдина
 * реалізація — {@link BoardContent}; у тестах підставляється заглушка.
 */
public interface ModeratedContent {

    /** Автор об'єкта, або {@code empty}, якщо об'єкта немає чи він уже прибраний. */
    Optional<UUID> authorOf(ContentType type, UUID id);

    /** Прибирає об'єкт після підтвердженої скарги (FR-057). Повторний виклик нічого не змінює. */
    void remove(ContentType type, UUID id);

    /** Скільки оголошень автор створив після {@code since}, зокрема видалені (FR-059). */
    long countAdvertsSince(UUID authorId, OffsetDateTime since);

    /** Скільки відгуків користувач подав після {@code since} (FR-059). */
    long countRepliesSince(UUID userId, OffsetDateTime since);
}
