package ua.edu.ukma.community.subscription;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Profile("!inmemory")
public class JdbcSubscriptionRepository implements SubscriptionRepository {

    private final JdbcClient jdbc;

    public JdbcSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UUID organizationId, UUID userId) {
        Integer count = jdbc.sql("""
                        SELECT COUNT(*) FROM organization_subscription
                        WHERE organization_id = :organizationId AND user_id = :userId
                        """)
                .param("organizationId", organizationId)
                .param("userId", userId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void subscribe(UUID organizationId, UUID userId) {
        jdbc.sql("""
                        INSERT INTO organization_subscription (organization_id, user_id)
                        VALUES (:organizationId, :userId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("organizationId", organizationId)
                .param("userId", userId)
                .update();
    }

    @Override
    public void unsubscribe(UUID organizationId, UUID userId) {
        jdbc.sql("""
                        DELETE FROM organization_subscription
                        WHERE organization_id = :organizationId AND user_id = :userId
                        """)
                .param("organizationId", organizationId)
                .param("userId", userId)
                .update();
    }

    @Override
    public List<String> findActiveSubscriberEmails(UUID organizationId) {
        return jdbc.sql("""
                        SELECT u.email
                        FROM organization_subscription s
                        JOIN app_user u ON u.id = s.user_id
                        WHERE s.organization_id = :organizationId
                          AND u.status = 'ACTIVE'
                        """)
                .param("organizationId", organizationId)
                .query(String.class)
                .list();
    }
}
