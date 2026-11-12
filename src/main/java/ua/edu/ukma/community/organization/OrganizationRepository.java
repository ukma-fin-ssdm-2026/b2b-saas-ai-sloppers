package ua.edu.ukma.community.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface OrganizationRepository {

    Optional<Organization> findById(UUID id);

    Optional<Organization> findBySlug(String slug);

    List<Organization> findAll();

    /**
     * Організації, що відповідають пошуку, без заданого порядку. Фільтр виконує сховище, щоб каталог
     * не завантажував усю таблицю (R3).
     *
     * @param needle підрядок у нижньому регістрі, уже обрізаний; порожній означає «усі»
     */
    List<Organization> findMatching(String needle);

    boolean existsBySlug(String slug, UUID excludedId);

    Organization save(Organization organization);

    boolean deleteById(UUID id);
}
