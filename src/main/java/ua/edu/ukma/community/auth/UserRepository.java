package ua.edu.ukma.community.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Сховище користувачів.
 */
public interface UserRepository {

    Optional<User> findById(UUID id);

    /** Пошта вже нормалізована (нижній регістр, без пробілів). */
    Optional<User> findByEmail(String email);

    User save(User user);
}
