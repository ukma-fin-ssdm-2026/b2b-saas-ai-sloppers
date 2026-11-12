package ua.edu.ukma.community.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface EventRepository {

    Optional<Event> findById(UUID id);

    List<Event> findAll();

    /**
     * Опубліковані події в межах {@code [from, to)}, без заданого порядку.
     *
     * @param organizationId лише ця організація, або {@code null} для всіх
     * @param to             верхня межа, або {@code null} якщо її немає
     */
    List<Event> findPublishedBetween(UUID organizationId, OffsetDateTime from, OffsetDateTime to);

    List<Event> findByOrganizationId(UUID organizationId);

    Optional<Event> findBySourcePost(UUID sourceId, String externalPostId);

    Event save(Event event);

    void deleteByOrganizationId(UUID organizationId);
}
