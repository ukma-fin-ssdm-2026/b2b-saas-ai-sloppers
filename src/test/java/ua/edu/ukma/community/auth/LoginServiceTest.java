package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.ukma.community.auth.AuthTestSupport.FAST_BCRYPT;
import static ua.edu.ukma.community.auth.AuthTestSupport.PROPERTIES;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;

@DisplayName("LoginService і TokenService: вхід і JWT (FR-004, NFR-010)")
class LoginServiceTest {

    InMemoryUserRepository users;
    AuthConfig config = new AuthConfig();
    javax.crypto.SecretKey key;
    LoginService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        key = config.jwtKey(PROPERTIES);
        TokenService tokens = new TokenService(config.jwtEncoder(key), PROPERTIES, java.time.Clock.systemUTC());
        service = new LoginService(users, FAST_BCRYPT, tokens);
    }

    private User save(String email, UserStatus status) {
        OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
        return users.save(new User(UUID.randomUUID(), email, FAST_BCRYPT.encode("password123"), status, false, null,
                now, now));
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).code();
    }

    @Test
    @DisplayName("активний користувач отримує JWT на 60 хв із sub = id, який приймає декодер застосунку")
    void activeUserGetsValidToken() {
        User user = save("student@ukma.edu.ua", UserStatus.ACTIVE);

        TokenResponse token = service.login(" Student@ukma.edu.ua ", "password123");

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
        Jwt jwt = config.jwtDecoder(key).decode(token.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("student@ukma.edu.ua");
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(3600));
    }

    @Test
    @DisplayName("неправильний пароль і невідома пошта дають однакову помилку 401")
    void wrongPasswordAndUnknownEmailLookTheSame() {
        save("student@ukma.edu.ua", UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.login("student@ukma.edu.ua", "wrong-password"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        assertThatThrownBy(() -> service.login("nobody@ukma.edu.ua", "password123"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("непідтверджений акаунт → 403 ACCOUNT_NOT_VERIFIED, заблокований → 403 ACCOUNT_BLOCKED")
    void pendingAndBlockedCannotLogIn() {
        save("pending@ukma.edu.ua", UserStatus.PENDING);
        save("blocked@ukma.edu.ua", UserStatus.BLOCKED);

        assertThatThrownBy(() -> service.login("pending@ukma.edu.ua", "password123"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCOUNT_NOT_VERIFIED));
        assertThatThrownBy(() -> service.login("blocked@ukma.edu.ua", "password123"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCOUNT_BLOCKED));
    }

    @Test
    @DisplayName("токен, підписаний іншим ключем, декодер відхиляє")
    void foreignTokenRejected() {
        save("student@ukma.edu.ua", UserStatus.ACTIVE);
        String token = service.login("student@ukma.edu.ua", "password123").accessToken();
        javax.crypto.SecretKey otherKey = config.jwtKey(PROPERTIES);

        assertThatThrownBy(() -> config.jwtDecoder(otherKey).decode(token))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
