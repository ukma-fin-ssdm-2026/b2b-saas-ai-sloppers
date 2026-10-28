package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.support.MutableClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.ukma.community.auth.AuthTestSupport.ADMIN_EMAIL;
import static ua.edu.ukma.community.auth.AuthTestSupport.FAST_BCRYPT;
import static ua.edu.ukma.community.auth.AuthTestSupport.PROPERTIES;

@DisplayName("RegistrationService: реєстрація і підтвердження пошти (FR-001–FR-003, US-001)")
class RegistrationServiceTest {

    static final String EMAIL = "student@ukma.edu.ua";

    InMemoryUserRepository users;
    MutableClock clock;
    List<String> sentCodes;
    RegistrationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        clock = MutableClock.startingAtFixtureNow();
        sentCodes = new ArrayList<>();
        CodeSender sender = (email, code) -> sentCodes.add(code);
        service = new RegistrationService(users, FAST_BCRYPT, sender, PROPERTIES, clock);
    }

    private String lastCode() {
        return sentCodes.get(sentCodes.size() - 1);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).code();
    }

    @Test
    @DisplayName("TC-001: пошта @ukma.edu.ua → неактивний акаунт, надіслано 6-значний код, пароль лише хешем")
    void registersPendingUserAndSendsCode() {
        User user = service.register("  Student@UKMA.edu.ua ", "password123");

        assertThat(user.email()).isEqualTo(EMAIL);
        assertThat(user.status()).isEqualTo(UserStatus.PENDING);
        assertThat(user.passwordHash()).isNotEqualTo("password123");
        assertThat(FAST_BCRYPT.matches("password123", user.passwordHash())).isTrue();
        assertThat(sentCodes).singleElement().asString().matches("\\d{6}");
        assertThat(user.verification().codeHash()).isNotEqualTo(lastCode());
        assertThat(user.toString()).doesNotContain(user.passwordHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {"student@gmail.com", "student@ukma.edu.ua.evil.com", "student@sub.ukma.edu.ua",
            "a@b@ukma.edu.ua", "@ukma.edu.ua"})
    @DisplayName("TC-002: інший домен → 400, акаунт не створено, лист не надіслано")
    void rejectsOtherDomains(String email) {
        assertThatThrownBy(() -> service.register(email, "password123"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(sentCodes).isEmpty();
    }

    @Test
    @DisplayName("повторна реєстрація тієї ж пошти (без урахування регістру) → 409")
    void duplicateEmailRejected() {
        service.register(EMAIL, "password123");

        assertThatThrownBy(() -> service.register("STUDENT@ukma.edu.ua", "other-password"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
    }

    @Test
    @DisplayName("пошта з AUTH_ADMIN_EMAILS отримує Global Admin, інші — ні")
    void adminEmailsBecomeGlobalAdmins() {
        assertThat(service.register(ADMIN_EMAIL, "password123").globalAdmin()).isTrue();
        assertThat(service.register(EMAIL, "password123").globalAdmin()).isFalse();
    }

    @Test
    @DisplayName("правильний код протягом 15 хв активує акаунт і прибирає код")
    void correctCodeActivates() {
        service.register(EMAIL, "password123");
        clock.advance(Duration.ofMinutes(14));

        service.verify(EMAIL, lastCode());

        User user = users.findByEmail(EMAIL).orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.verification()).isNull();
    }

    @Test
    @DisplayName("TC-003: код після 15 хвилин недійсний; новий код активує акаунт")
    void expiredCodeThenNewCode() {
        service.register(EMAIL, "password123");
        String oldCode = lastCode();
        clock.advance(Duration.ofMinutes(15));

        assertThatThrownBy(() -> service.verify(EMAIL, oldCode))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED));

        service.resendCode(EMAIL);
        service.verify(EMAIL, lastCode());
        assertThat(users.findByEmail(EMAIL).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("TC-003: після 5 невдалих спроб навіть правильний код не приймається")
    void fiveFailedAttemptsInvalidateCode() {
        service.register(EMAIL, "password123");
        String code = lastCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify(EMAIL, wrong))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));
        }

        assertThatThrownBy(() -> service.verify(EMAIL, code))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED));
    }

    @Test
    @DisplayName("новий код не частіше ніж раз на хвилину; старий код після цього недійсний")
    void resendCooldownAndOldCodeInvalid() {
        service.register(EMAIL, "password123");
        String first = lastCode();

        assertThatThrownBy(() -> service.resendCode(EMAIL))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));

        clock.advance(Duration.ofSeconds(61));
        service.resendCode(EMAIL);
        String second = lastCode();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> service.verify(EMAIL, first))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));
        }
        service.verify(EMAIL, second);
        assertThat(users.findByEmail(EMAIL).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("resend для невідомої або вже підтвердженої пошти нічого не робить і не кидає помилку")
    void resendIsSilentForUnknownOrActive() {
        assertThatNoException().isThrownBy(() -> service.resendCode("nobody@ukma.edu.ua"));
        service.register(EMAIL, "password123");
        service.verify(EMAIL, lastCode());
        clock.advance(Duration.ofMinutes(5));

        service.resendCode(EMAIL);

        assertThat(sentCodes).hasSize(1);
    }

    @Test
    @DisplayName("повторне підтвердження → 409, невідома пошта → як неправильний код")
    void verifyEdgeCases() {
        assertThatThrownBy(() -> service.verify("nobody@ukma.edu.ua", "123456"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));

        service.register(EMAIL, "password123");
        service.verify(EMAIL, lastCode());
        assertThatThrownBy(() -> service.verify(EMAIL, lastCode()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ACCOUNT_ALREADY_VERIFIED));
    }
}
