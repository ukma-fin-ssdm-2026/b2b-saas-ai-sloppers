package ua.edu.ukma.community.auth;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    /** Кілька користувачів за раз — щоб показати імена у списках без запиту на кожен рядок (FR-069). */
    List<User> findAllById(Collection<UUID> ids);

    User save(User user);
}
