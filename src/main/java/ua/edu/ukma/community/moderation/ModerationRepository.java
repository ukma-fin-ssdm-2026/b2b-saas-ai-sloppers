package ua.edu.ukma.community.moderation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Сховище модерації: {@link JdbcModerationRepository} для PostgreSQL, {@link InMemoryModerationRepository}
 * для тестів і профілю {@code inmemory}.
 */
public interface ModerationRepository {

    // ---- скарги (FR-056, FR-057) ----

    Complaint saveComplaint(Complaint complaint);

    Optional<Complaint> findComplaint(UUID id);

    /**
     * Черга скарг, найновіші спершу.
     *
     * @param status лише скарги в цьому стані, або {@code null} для всіх
     */
    List<Complaint> findComplaints(ComplaintStatus status, int limit, int offset);

    long countComplaints(ComplaintStatus status);

    boolean complaintExists(UUID reporterId, ContentType targetType, UUID targetId);

    /**
     * Скільки різних користувачів поскаржилось на контент цього автора; відхилені скарги не рахуються.
     * За цим числом спрацьовує автоматичне блокування (FR-060).
     */
    long countComplainantsAgainst(UUID authorId);

    /**
     * Закриває всі відкриті скарги на цей об'єкт одним рішенням: прибраний контент не має лишати
     * в черзі скарги, які вже нічого не змінять (FR-057).
     *
     * @return скільки скарг закрито
     */
    int resolveComplaintsOn(ContentType targetType, UUID targetId, ComplaintStatus decision, UUID adminId,
                            String resolution, OffsetDateTime at);

    // ---- блокування (FR-054) ----

    Ban saveBan(Ban ban);

    /** Незнятий бан користувача, навіть якщо його строк уже вийшов: такий знімає планувальник. */
    Optional<Ban> findActiveBan(UUID userId);

    /** Історія блокувань користувача, найновіші спершу. */
    List<Ban> findBans(UUID userId);

    /** Незняті бани зі строком, що вийшов до {@code now} (FR-054, планувальник). */
    List<Ban> findExpiredBans(OffsetDateTime now);

    /** Хто заблокований станом на {@code now} — щоб приховати їхні оголошення (FR-055). */
    Set<UUID> findBannedUsers(OffsetDateTime now);

    // ---- заборонені слова (FR-058) ----

    List<BannedWord> findBannedWords();

    BannedWord saveBannedWord(BannedWord word);

    /** @return {@code false}, якщо слова з таким id немає */
    boolean deleteBannedWord(UUID id);

    // ---- відмови автофільтра (FR-058, FR-060) ----

    FilterRejection saveRejection(FilterRejection rejection);

    long countRejectionsSince(UUID userId, OffsetDateTime since);
}
