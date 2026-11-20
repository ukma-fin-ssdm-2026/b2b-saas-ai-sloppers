package ua.edu.ukma.community.rating;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Оцінка автора оголошення його підтвердженим учасником (FR-061), таблиця {@code author_rating}. */
public record AuthorRating(
        UUID id,
        UUID raterId,
        UUID authorId,
        UUID advertId,
        int score,
        OffsetDateTime createdAt
) {

    public AuthorRating {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(raterId, "raterId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(advertId, "advertId");
        Objects.requireNonNull(createdAt, "createdAt");
        Scores.requireValid(score);
    }

    public static AuthorRating create(UUID raterId, UUID authorId, UUID advertId, int score, OffsetDateTime now) {
        return new AuthorRating(UUID.randomUUID(), raterId, authorId, advertId, score, now);
    }
}
