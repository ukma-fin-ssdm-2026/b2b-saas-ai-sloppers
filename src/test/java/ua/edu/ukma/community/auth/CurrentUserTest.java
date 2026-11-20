package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentUserTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AuthProperties properties = mock(AuthProperties.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CurrentUser currentUser = new CurrentUser(users, properties, clock);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireSyncsGlobalAdminFlag() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);
        User stored = new User(id, "admin@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null, now, now);
        User saved = stored.withGlobalAdmin(true, now);

        when(users.findById(id)).thenReturn(Optional.of(stored));
        when(properties.isAdminEmail("admin@ukma.edu.ua")).thenReturn(true);
        when(users.save(any(User.class))).thenReturn(saved);

        Jwt jwt = new Jwt(
                "token",
                Instant.now(clock),
                Instant.now(clock).plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", id.toString()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        User result = currentUser.require();
        assertThat(result.globalAdmin()).isTrue();
        verify(users).save(any(User.class));
    }

    @Test
    void requireRejectsMissingAuth() {
        assertThatThrownBy(currentUser::require)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
