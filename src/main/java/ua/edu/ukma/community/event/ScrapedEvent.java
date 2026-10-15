package ua.edu.ukma.community.event;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;


public record ScrapedEvent(
        UUID organizationId,
        UUID sourceId,
        String externalPostId,
        String title,
        OffsetDateTime startsAt,
        String originalUrl
) {

    public ScrapedEvent {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(externalPostId, "externalPostId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(originalUrl, "originalUrl");
        title = title.strip();
        if (title.length() < EventRules.TITLE_MIN || title.length() > EventRules.TITLE_MAX) {
            throw new IllegalArgumentException("Назва події має містити від " + EventRules.TITLE_MIN
                    + " до " + EventRules.TITLE_MAX + " символів");
        }
        if (externalPostId.isBlank() || externalPostId.length() > EventRules.EXTERNAL_POST_ID_MAX) {
            throw new IllegalArgumentException("Некоректний ідентифікатор поста");
        }
        if (!EventRules.isValidUrl(originalUrl)) {
            throw new IllegalArgumentException(EventRules.URL_MESSAGE);
        }
    }
}
