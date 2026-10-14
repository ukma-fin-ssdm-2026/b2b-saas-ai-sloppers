package ua.edu.ukma.community.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface EventRepository {

    Optional<Event> findById(UUID id);

    List<Event> findAll();

    List<Event> findByOrganizationId(UUID organizationId);

    Optional<Event> findBySourcePost(UUID sourceId, String externalPostId);

    Event save(Event event);

    void deleteByOrganizationId(UUID organizationId);
}
