package ua.edu.ukma.community.moderation;

import ua.edu.ukma.community.auth.User;

import java.util.Set;
import java.util.UUID;

/**
 * Що модерація вимагає від модулів, які приймають користувацький контент. Дошка оголошень викликає
 * {@code before*} перед записом і {@link #hiddenAuthors()} перед показом списків — більше вона про
 * модерацію нічого не знає.
 *
 * <p>Сам факт блокування людини перевіряється раніше й дешевше:
 * {@code CurrentUser} відмовляє заблокованому акаунту на вході в будь-який захищений маршрут (FR-055).
 */
public interface ModerationGate {

    /**
     * Перед створенням оголошення: ліміт частоти (FR-059) і автофільтр заборонених слів (FR-058).
     *
     * @throws ua.edu.ukma.community.common.error.ApiException {@code TOO_MANY_REQUESTS} або
     *         {@code CONTENT_REJECTED}
     */
    void beforeAdvert(User author, String title, String description);

    /** Те саме перед відгуком (FR-058, FR-059). */
    void beforeReply(User user, String message);

    /** Чиї оголошення не показувати в списку активних (FR-055). */
    Set<UUID> hiddenAuthors();

    /** Те саме для одного автора: дешевіше, ніж тягнути весь список при читанні одного оголошення. */
    boolean isHidden(UUID authorId);

    /** Заглушка для тестів, яким модерація не цікава: пропускає все. */
    static ModerationGate permissive() {
        return new ModerationGate() {
            @Override
            public void beforeAdvert(User author, String title, String description) {
                // пропускаємо
            }

            @Override
            public void beforeReply(User user, String message) {
                // пропускаємо
            }

            @Override
            public Set<UUID> hiddenAuthors() {
                return Set.of();
            }

            @Override
            public boolean isHidden(UUID authorId) {
                return false;
            }
        };
    }
}
