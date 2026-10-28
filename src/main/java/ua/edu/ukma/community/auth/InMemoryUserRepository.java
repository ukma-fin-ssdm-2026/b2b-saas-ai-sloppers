package ua.edu.ukma.community.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Користувачі в пам'яті процесу (профіль {@code inmemory} і тести).
 */
@Repository
@Profile("inmemory")
public class InMemoryUserRepository implements UserRepository {

    private final Map<UUID, User> store = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return store.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }

    @Override
    public User save(User user) {
        store.put(user.id(), user);
        return user;
    }
}
