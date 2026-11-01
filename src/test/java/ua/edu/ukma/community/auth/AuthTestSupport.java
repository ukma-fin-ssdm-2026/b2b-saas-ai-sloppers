package ua.edu.ukma.community.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Спільне для тестів Auth.
 */
final class AuthTestSupport {

    static final String ADMIN_EMAIL = "admin@ukma.edu.ua";

    /** Мінімальна вартість bcrypt, щоб тести не гальмували; у застосунку — стандартна. */
    static final PasswordEncoder FAST_BCRYPT = new BCryptPasswordEncoder(4);

    static final AuthProperties PROPERTIES = new AuthProperties("", Duration.ofMinutes(60), List.of(ADMIN_EMAIL), "",
            "ukma.edu.ua", Duration.ofMinutes(15), 5, Duration.ofSeconds(60));

    private AuthTestSupport() {
    }

    static User activeUser(String email, boolean globalAdmin, OffsetDateTime now) {
        return new User(UUID.randomUUID(), email, FAST_BCRYPT.encode("password123"), UserStatus.ACTIVE, globalAdmin,
                null, now, now);
    }
}
