package ua.edu.ukma.community.auth;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Імена користувачів для показу в інших модулях (FR-069): дошка показує, хто автор і хто відгукнувся.
 * Один запит на список, а не по запиту на рядок.
 */
@Component
public class UserDirectory {

    private final UserRepository users;

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    /** id → «Ім'я Прізвище»; користувачів без імені в мапі немає. */
    public Map<UUID, String> fullNames(Collection<UUID> ids) {
        return users.findAllById(ids).stream()
                .filter(u -> u.fullName() != null)
                .collect(Collectors.toMap(User::id, User::fullName, (a, b) -> a));
    }

    public String fullName(UUID id) {
        return users.findById(id).map(User::fullName).filter(Objects::nonNull).orElse(null);
    }
}
