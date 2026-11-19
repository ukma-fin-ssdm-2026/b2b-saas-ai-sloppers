package ua.edu.ukma.community.auth;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Дані користувача про себе. Поки що лише ім'я та прізвище (FR-069); решту профілю веде account-server (FR-066). */
@Service
public class ProfileService {

    private final UserRepository users;
    private final Clock clock;

    public ProfileService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public User updateName(User user, String firstName, String lastName) {
        return users.save(user.withName(Names.normalize(firstName), Names.normalize(lastName),
                OffsetDateTime.now(clock)));
    }
}
