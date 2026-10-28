package ua.edu.ukma.community.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Код підтвердження — Студентське ком'юніті НаУКМА");
        message.setText("Ваш код підтвердження: " + code + "\n\n"
                + "Код дійсний " + codeTtl.toMinutes() + " хв. "
                + "Якщо ви не реєструвалися, просто проігноруйте цей лист.");
        try {
            mail.send(message);
        } catch (MailException e) {
            LOG.error("Failed to send verification code to {}: {}", email, e.getMessage());
            throw new ApiException(ErrorCode.MAIL_UNAVAILABLE,
                    "Не вдалося надіслати лист з кодом. Спробуйте запросити код ще раз трохи згодом");
        }
    }
}
