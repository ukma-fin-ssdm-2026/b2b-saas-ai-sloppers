package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import ua.edu.ukma.community.auth.AuthConfig;
import ua.edu.ukma.community.auth.AuthProperties;
import ua.edu.ukma.community.auth.CodeSender;
import ua.edu.ukma.community.auth.LogCodeSender;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lab 09, R5 — <b>fail fast</b> instead of silent stubs outside {@code dev}/{@code test}/{@code inmemory}.
 *
 * <p>Without {@code AUTH_JWT_SECRET} / SMTP the app used to start “healthy” with a random JWT key and
 * codes written to the log. Production-like profiles must refuse that and name the missing variable.
 */
@DisplayName("Надійність R5: заглушки auth лише в dev/test/inmemory")
class AuthStubGuardTest {

    private static final AuthProperties EMPTY_SECRETS = new AuthProperties(
            "", Duration.ofMinutes(60), List.of(), "", "ukma.edu.ua",
            Duration.ofMinutes(15), 5, Duration.ofSeconds(60));

    @Test
    @DisplayName("без AUTH_JWT_SECRET поза stub-профілями → IllegalStateException")
    void missingJwtSecretFailsOutsideStubProfiles() {
        AuthConfig config = new AuthConfig(envAccepting(false));

        assertThatThrownBy(() -> config.jwtKey(EMPTY_SECRETS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_JWT_SECRET");
    }

    @Test
    @DisplayName("без SMTP поза stub-профілями → IllegalStateException")
    void missingSmtpFailsOutsideStubProfiles() {
        AuthConfig config = new AuthConfig(envAccepting(false));
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
        when(mail.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> config.codeSender(mail, EMPTY_SECRETS, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST");
    }

    @Test
    @DisplayName("профіль inmemory без секретів → випадковий JWT і LogCodeSender")
    void stubProfilesStillAllowDevFallbacks() {
        AuthConfig config = new AuthConfig(envAccepting(true));
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
        when(mail.getIfAvailable()).thenReturn(null);

        SecretKey key = config.jwtKey(EMPTY_SECRETS);
        CodeSender sender = config.codeSender(mail, EMPTY_SECRETS, "", "");

        assertThat(key.getEncoded()).hasSize(32);
        assertThat(sender).isInstanceOf(LogCodeSender.class);
    }

    private static Environment envAccepting(boolean stubsAllowed) {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(stubsAllowed);
        return env;
    }
}
