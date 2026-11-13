package ua.edu.ukma.community.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.LogSafe;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.organization.OrganizationService;

import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final OrganizationService organizations;
    private final EventAnnouncementMailer mailer;

    public SubscriptionService(SubscriptionRepository subscriptions, OrganizationService organizations,
                               EventAnnouncementMailer mailer) {
        this.subscriptions = subscriptions;
        this.organizations = organizations;
        this.mailer = mailer;
    }

    public boolean isSubscribed(UUID organizationId, UUID userId) {
        organizations.get(organizationId);
        return subscriptions.exists(organizationId, userId);
    }

    public void subscribe(UUID organizationId, UUID userId) {
        organizations.get(organizationId);
        subscriptions.subscribe(organizationId, userId);
    }

    public void unsubscribe(UUID organizationId, UUID userId) {
        organizations.get(organizationId);
        subscriptions.unsubscribe(organizationId, userId);
    }

    /**
     * Надсилає лист кожному активному підписнику організації. Викликається у фоні
     * ({@link EventAnnouncementListener}), тому нічого не кидає: кожен збій записується в лог,
     * а в кінці — підсумок, скільки листів пішло і скільки ні (fail safe, без мовчазних збоїв).
     */
    public void notifyNewEvent(Event event) {
        Organization organization;
        List<String> emails;
        try {
            organization = organizations.get(event.organizationId());
            emails = subscriptions.findActiveSubscriberEmails(organization.id());
        } catch (ApiException | DataAccessException e) {
            LOG.warn("Skip subscriber notify for event {}: {}", LogSafe.of(event.id()), LogSafe.of(e.getMessage()));
            return;
        }
        if (emails.isEmpty()) {
            return;
        }
        int failed = 0;
        for (String email : emails) {
            try {
                mailer.sendNewEvent(email, organization, event);
            } catch (MailException e) {
                failed++;
                LOG.error("Failed announcing event {} to {}: {}",
                        LogSafe.of(event.id()), LogSafe.of(email), LogSafe.of(e.getMessage()));
            }
        }
        LOG.info("Announced event {} of org {}: {} sent, {} failed",
                LogSafe.of(event.id()), LogSafe.of(organization.slug()), emails.size() - failed, failed);
    }
}
