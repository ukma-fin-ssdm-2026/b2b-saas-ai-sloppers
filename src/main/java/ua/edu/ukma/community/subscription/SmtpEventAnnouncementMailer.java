package ua.edu.ukma.community.subscription;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.organization.Organization;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SmtpEventAnnouncementMailer implements EventAnnouncementMailer {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("uk"));

    private final JavaMailSender mail;
    private final String from;

    public SmtpEventAnnouncementMailer(JavaMailSender mail, String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void sendNewEvent(String email, Organization organization, Event event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Нова подія: " + event.title() + " — " + organization.name());
        StringBuilder body = new StringBuilder();
        body.append("Організація «").append(organization.name()).append("» опублікувала нову подію.\n\n");
        body.append(event.title()).append('\n');
        body.append("Коли: ").append(WHEN.format(event.startsAt())).append('\n');
        if (event.location() != null && !event.location().isBlank()) {
            body.append("Де: ").append(event.location()).append('\n');
        }
        if (event.description() != null && !event.description().isBlank()) {
            body.append('\n').append(event.description()).append('\n');
        }
        body.append("\n— Студентське ком'юніті НаУКМА");
        message.setText(body.toString());
        // MailException не ловимо тут: SubscriptionService рахує невдалі листи і пише їх у лог
        mail.send(message);
    }
}
