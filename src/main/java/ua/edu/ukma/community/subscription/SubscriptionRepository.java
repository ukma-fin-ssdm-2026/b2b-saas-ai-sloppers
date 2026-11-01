package ua.edu.ukma.community.subscription;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository {

    boolean exists(UUID organizationId, UUID userId);

    void subscribe(UUID organizationId, UUID userId);

    void unsubscribe(UUID organizationId, UUID userId);

    List<String> findActiveSubscriberEmails(UUID organizationId);
}
