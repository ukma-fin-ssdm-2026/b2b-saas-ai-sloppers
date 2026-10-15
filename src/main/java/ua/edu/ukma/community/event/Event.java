package ua.edu.ukma.community.event;

import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;


public record Event(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        OffsetDateTime startsAt,
        String location,
        String originalUrl,
        UUID sourceId,
        String externalPostId,
        EventStatus status,
        boolean verified,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public Event {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean isPublished() {
        return status == EventStatus.PUBLISHED;
    }

    public boolean isScraped() {
        return sourceId != null;
    }

    public Event approve(OffsetDateTime now) {
        boolean allowed = status == EventStatus.PENDING
                || status == EventStatus.UNPUBLISHED
                || status == EventStatus.PUBLISHED && !verified;
        if (!allowed) {
            throw transitionNotAllowed("схвалити");
        }
        return withStatus(EventStatus.PUBLISHED, true, now);
    }

    public Event reject(OffsetDateTime now) {
        if (status != EventStatus.PENDING) {
            throw transitionNotAllowed("відхилити");
        }
        return withStatus(EventStatus.REJECTED, true, now);
    }

    public Event unpublish(OffsetDateTime now) {
        if (status != EventStatus.PUBLISHED) {
            throw transitionNotAllowed("зняти з публікації");
        }
        return withStatus(EventStatus.UNPUBLISHED, verified, now);
    }

    public Event withDetails(String newTitle, String newDescription, OffsetDateTime newStartsAt,
                             String newLocation, String newOriginalUrl, OffsetDateTime now) {
        if (status == EventStatus.REJECTED) {
            throw transitionNotAllowed("редагувати");
        }
        return new Event(id, organizationId,
                newTitle != null ? newTitle.trim() : title,
                newDescription != null ? newDescription : description,
                newStartsAt != null ? newStartsAt : startsAt,
                newLocation != null ? newLocation : location,
                newOriginalUrl != null ? newOriginalUrl : originalUrl,
                sourceId, externalPostId, status, verified, createdAt, now);
    }

    private Event withStatus(EventStatus newStatus, boolean newVerified, OffsetDateTime now) {
        return new Event(id, organizationId, title, description, startsAt, location, originalUrl,
                sourceId, externalPostId, newStatus, newVerified, createdAt, now);
    }

    private ApiException transitionNotAllowed(String action) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                "Подію у статусі " + status + (verified ? "" : " (неперевірена)") + " не можна " + action);
    }
}
