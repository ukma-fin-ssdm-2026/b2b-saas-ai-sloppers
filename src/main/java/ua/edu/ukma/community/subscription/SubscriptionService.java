package ua.edu.ukma.community.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.LogSafe;
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

    /** Notify active subscribers that a newly published event exists. Failures are logged, never thrown. */
    public void notifyNewEvent(Event event) {
        Organization organization;
        try {
            organization = organizations.get(event.organizationId());
        } catch (RuntimeException e) {
            LOG.warn("Skip subscriber notify for event {}: organization missing ({})",
                    LogSafe.of(event.id()), LogSafe.of(e.getMessage()));
            return;
        }
        List<String> emails = subscriptions.findActiveSubscriberEmails(organization.id());
        if (emails.isEmpty()) {
            return;
        }
        LOG.info("Announcing event '{}' to {} subscriber(s) of org {}",
                LogSafe.of(event.title()), emails.size(), LogSafe.of(organization.slug()));
        for (String email : emails) {
            try {
                mailer.sendNewEvent(email, organization, event);
            } catch (RuntimeException e) {
                LOG.error("Failed announcing event {} to {}: {}",
                        LogSafe.of(event.id()), LogSafe.of(email), LogSafe.of(e.getMessage()));
            }
        }
    }
}
