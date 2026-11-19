package ua.edu.ukma.community.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import ua.edu.ukma.community.common.LogSafe;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Duration;


public class MailCodeSender implements CodeSender {

    private static final Logger LOG = LoggerFactory.getLogger(MailCodeSender.class);

    private final JavaMailSender mail;
    private final String from;
    private final Duration codeTtl;

    public MailCodeSender(JavaMailSender mail, String from, Duration codeTtl) {
        this.mail = mail;
        this.from = from;
        this.codeTtl = codeTtl;
    }

    @Override
    public void send(String email, String code) {
        deliver(email, "Код підтвердження — Студентське ком'юніті НаУКМА",
                "Ваш код підтвердження: " + code + "\n\n"
                        + "Код дійсний " + codeTtl.toMinutes() + " хв. "
                        + "Якщо ви не реєструвалися, просто проігноруйте цей лист.");
    }

    @Override
    public void sendPasswordReset(String email, String code) {
        deliver(email, "Скидання пароля — Студентське ком'юніті НаУКМА",
                "Код для скидання пароля: " + code + "\n\n"
                        + "Код дійсний " + codeTtl.toMinutes() + " хв. "
                        + "Якщо ви не просили скинути пароль, проігноруйте цей лист: пароль лишиться тим самим.");
    }

    private void deliver(String email, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        try {
            mail.send(message);
        } catch (MailException e) {
            LOG.error("Failed to send verification code to {}: {}", LogSafe.of(email),
                    LogSafe.of(e.getMessage()));
            throw new ApiException(ErrorCode.MAIL_UNAVAILABLE,
                    "Не вдалося надіслати лист з кодом. Спробуйте запросити код ще раз трохи згодом");
        }
    }
}
