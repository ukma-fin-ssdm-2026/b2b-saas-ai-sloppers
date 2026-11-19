package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Листи з кодами: підтвердження пошти й скидання пароля (FR-002, FR-065)")
class MailCodeSenderTest {

    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final MailCodeSender sender = new MailCodeSender(mail, "noreply@ukma.edu.ua", Duration.ofMinutes(15));

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        return message.getValue();
    }

    @Test
    @DisplayName("код підтвердження: адресат, відправник, код і строк у тексті")
    void verificationCode() {
        sender.send("student@ukma.edu.ua", "042517");

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("student@ukma.edu.ua");
        assertThat(message.getFrom()).isEqualTo("noreply@ukma.edu.ua");
        assertThat(message.getSubject()).startsWith("Код підтвердження");
        assertThat(message.getText()).contains("042517", "15 хв", "не реєструвалися");
    }

    @Test
    @DisplayName("скидання пароля: окрема тема й текст, без «ви реєструвалися»")
    void passwordResetCode() {
        sender.sendPasswordReset("student@ukma.edu.ua", "731904");

        SimpleMailMessage message = sent();
        assertThat(message.getSubject()).startsWith("Скидання пароля");
        assertThat(message.getText()).contains("731904", "15 хв", "не просили скинути пароль")
                .doesNotContain("реєструвалися");
    }

    @Test
    @DisplayName("SMTP недоступний → 503 MAIL_UNAVAILABLE, без коду в повідомленні помилки")
    void mailFailure() {
        doThrow(new MailSendException("smtp down")).when(mail).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.sendPasswordReset("student@ukma.edu.ua", "731904"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.MAIL_UNAVAILABLE);
                    assertThat(e.getMessage()).doesNotContain("731904");
                });
    }

    @Test
    @DisplayName("без SMTP коди йдуть у лог — обидва види листів")
    void logSenderAcceptsBothKinds() {
        LogCodeSender log = new LogCodeSender();

        assertThatNoException().isThrownBy(() -> {
            log.send("student@ukma.edu.ua", "042517");
            log.sendPasswordReset("student@ukma.edu.ua", "731904");
        });
    }
}
