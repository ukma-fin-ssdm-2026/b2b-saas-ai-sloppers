package ua.edu.ukma.community.board;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Сховище оголошень: {@link JdbcAdvertRepository} для PostgreSQL, {@link InMemoryAdvertRepository} для тестів. */
public interface AdvertRepository {

    Optional<Advert> findById(UUID id);

    /**
     * Те саме, що {@link #findById}, але в транзакції ще й блокує рядок до її кінця
     * ({@code SELECT ... FOR UPDATE}): так зміни відгуків одного оголошення виконуються по черзі (NFR-006).
     */
    Optional<Advert> lock(UUID id);

    List<Advert> findAllById(Collection<UUID> ids);

    /**
     * Відкриті оголошення: {@code ACTIVE} з дедлайном після {@code now}, без заданого порядку (FR-045, FR-052).
     *
     * @param type лише цей тип, або {@code null} для всіх
     */
    List<Advert> findOpen(AdvertType type, OffsetDateTime now);

    /** Оголошення автора в будь-якому статусі, крім {@code DELETED}. */
    List<Advert> findByAuthor(UUID authorId);

    /**
     * Скільки оголошень автор створив після {@code since} — для ліміту частоти (FR-059). Видалені
     * теж рахуються: інакше ліміт обходився б створенням і видаленням оголошення.
     */
    long countByAuthorSince(UUID authorId, OffsetDateTime since);

    Advert save(Advert advert);

    /**
     * Переводить у {@code CLOSED} усі {@code ACTIVE} оголошення з дедлайном не пізніше {@code now} (FR-050).
     *
     * @return скільки оголошень закрито
     */
    int closeExpired(OffsetDateTime now);
}
