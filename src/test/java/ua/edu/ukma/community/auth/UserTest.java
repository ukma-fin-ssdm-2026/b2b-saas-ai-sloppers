package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void compactConstructorLeavesNamesNullAndWithGlobalAdminPreservesEmail() {
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        User user = new User(
                UUID.randomUUID(),
                "a@ukma.edu.ua",
                "hash",
                UserStatus.ACTIVE,
                false,
                null,
                now,
                now);

        assertThat(user.firstName()).isNull();
        assertThat(user.lastName()).isNull();
        assertThat(user.isActive()).isTrue();

        User admin = user.withGlobalAdmin(true, now.plusSeconds(1));
        assertThat(admin.globalAdmin()).isTrue();
        assertThat(admin.firstName()).isNull();
        assertThat(admin.fullName()).isNull();
    }
}
