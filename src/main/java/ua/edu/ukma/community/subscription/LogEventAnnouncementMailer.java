package ua.edu.ukma.community.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.ukma.community.common.LogSafe;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.organization.Organization;

/**
 * Fallback when SMTP is not configured: announcement text goes to the application log.
 */
public class LogEventAnnouncementMailer implements EventAnnouncementMailer {

    private static final Logger LOG = LoggerFactory.getLogger(LogEventAnnouncementMailer.class);

    @Override
    public void sendNewEvent(String email, Organization organization, Event event) {
        LOG.warn("SMTP is not configured. New event '{}' for org '{}' would be emailed to {}: startsAt={}",
                LogSafe.of(event.title()),
                LogSafe.of(organization.name()),
                LogSafe.of(email),
                LogSafe.of(event.startsAt()));
    }
}
