package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** SQL користувачів на справжньому PostgreSQL: коди підтвердження, скидання пароля, ім'я та прізвище. */
@DisplayName("JdbcUserRepository на PostgreSQL")
@Tag("integration")
class JdbcUserRepositoryTest {

    private MutableClock clock;
    private JdbcUserRepository users;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        clock = MutableClock.startingAtFixtureNow();
        users = new JdbcUserRepository(JdbcClient.create(TestDatabase.dataSource()), clock);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    @Test
    @DisplayName("користувач з кодом і іменем зберігається й читається без втрат — за id і за поштою")
    void roundTripWithCodeAndName() {
        VerificationCode code = new VerificationCode("code-hash", now().plusMinutes(15), 5, now());
        User saved = users.save(new User(UUID.randomUUID(), "student@ukma.edu.ua", "hash", UserStatus.PENDING, false,
                code, now(), now(), "Теодора", "Гречко"));

        assertThat(users.findById(saved.id())).contains(saved);
        assertThat(users.findByEmail("student@ukma.edu.ua")).contains(saved);
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("FR-065, FR-069: зміна пароля, коду й імені тим самим id оновлює рядок, а не створює новий")
    void upsertUpdatesPasswordCodeAndName() {
        User user = users.save(new User(UUID.randomUUID(), "student@ukma.edu.ua", "old-hash", UserStatus.PENDING,
                false, null, now(), now()));
        assertThat(users.findById(user.id()).orElseThrow().fullName()).isNull();

        clock.advance(Duration.ofMinutes(1));
        users.save(user.withVerification(new VerificationCode("reset-hash", now().plusMinutes(15), 5, now()), now()));
        assertThat(users.findById(user.id()).orElseThrow().verification().codeHash()).isEqualTo("reset-hash");

        User updated = users.save(user.withNewPassword("new-hash", now()).withName("Олена", "Коваль", now()));

        User loaded = users.findById(user.id()).orElseThrow();
        assertThat(loaded).isEqualTo(updated);
        assertThat(loaded.passwordHash()).isEqualTo("new-hash");
        assertThat(loaded.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.verification()).isNull();
        assertThat(loaded.fullName()).isEqualTo("Олена Коваль");
    }

    @Test
    @DisplayName("findAllById: порожній список, кілька id, невідомі id пропускаються")
    void findAllById() {
        User a = users.save(new User(UUID.randomUUID(), "a@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null,
                now(), now(), "Анна", null));
        User b = users.save(new User(UUID.randomUUID(), "b@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null,
                now(), now()));

        assertThat(users.findAllById(List.of())).isEmpty();
        assertThat(users.findAllById(List.of(a.id(), b.id(), a.id(), UUID.randomUUID())))
                .containsExactlyInAnyOrder(a, b);
    }
}
