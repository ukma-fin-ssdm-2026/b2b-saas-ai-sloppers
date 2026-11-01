package ua.edu.ukma.community.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import ua.edu.ukma.community.auth.AuthProperties;

@Configuration
public class SubscriptionConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionConfig.class);

    @Bean
    EventAnnouncementMailer eventAnnouncementMailer(ObjectProvider<JavaMailSender> mailSender,
                                                    AuthProperties properties,
                                                    @Value("${spring.mail.host:}") String mailHost,
                                                    @Value("${spring.mail.username:}") String mailUsername) {
        JavaMailSender mail = mailSender.getIfAvailable();
        if (mail == null || mailHost.isBlank()) {
            LOG.warn("SMTP is not configured (spring.mail.host): new-event announcements will be written to the log");
            return new LogEventAnnouncementMailer();
        }
        String from = properties.mailFrom().isBlank() ? mailUsername : properties.mailFrom();
        return new SmtpEventAnnouncementMailer(mail, from);
    }
}
