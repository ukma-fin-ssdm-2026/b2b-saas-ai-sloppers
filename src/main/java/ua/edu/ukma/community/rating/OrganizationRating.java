package ua.edu.ukma.community.rating;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Оцінка організації користувачем (FR-063), таблиця {@code organization_rating}; одна на пару, змінювана. */
public record OrganizationRating(
        UUID userId,
        UUID organizationId,
        int score,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public OrganizationRating {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Scores.requireValid(score);
    }
}
