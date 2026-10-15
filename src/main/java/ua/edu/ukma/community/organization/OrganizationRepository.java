package ua.edu.ukma.community.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface OrganizationRepository {

    Optional<Organization> findById(UUID id);

    List<Organization> findAll();

    boolean existsBySlug(String slug, UUID excludedId);

    Organization save(Organization organization);

    boolean deleteById(UUID id);
}
