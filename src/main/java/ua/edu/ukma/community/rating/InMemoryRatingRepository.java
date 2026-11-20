package ua.edu.ukma.community.rating;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Оцінки в пам'яті процесу: профіль {@code inmemory} і модульні тести. */
@Repository
@Profile("inmemory")
public class InMemoryRatingRepository implements RatingRepository {

    /** Ключ — пара (учасник, оголошення), як {@code UNIQUE (rater_id, advert_id)} у V9. */
    private final Map<List<UUID>, AuthorRating> authorRatings = new ConcurrentHashMap<>();
    /** Ключ — пара (користувач, організація), як первинний ключ у V9. */
    private final Map<List<UUID>, OrganizationRating> organizationRatings = new ConcurrentHashMap<>();

    @Override
    public boolean insertAuthorRating(AuthorRating rating) {
        return authorRatings.putIfAbsent(List.of(rating.raterId(), rating.advertId()), rating) == null;
    }

    @Override
    public Map<UUID, Tally> authorTallies(Collection<UUID> authorIds) {
        return tallies(authorRatings.values(), Set.copyOf(authorIds), AuthorRating::authorId,
                AuthorRating::score);
    }

    @Override
    public Map<UUID, Integer> scoresGivenBy(UUID raterId, Collection<UUID> advertIds) {
        Set<UUID> wanted = Set.copyOf(advertIds);
        return authorRatings.values().stream()
                .filter(r -> r.raterId().equals(raterId) && wanted.contains(r.advertId()))
                .collect(Collectors.toMap(AuthorRating::advertId, AuthorRating::score));
    }

    @Override
    public void upsertOrganizationRating(OrganizationRating rating) {
        organizationRatings.merge(List.of(rating.userId(), rating.organizationId()), rating,
                (existing, updated) -> new OrganizationRating(existing.userId(), existing.organizationId(),
                        updated.score(), existing.createdAt(), updated.updatedAt()));
    }

    @Override
    public Optional<OrganizationRating> findOrganizationRating(UUID userId, UUID organizationId) {
        return Optional.ofNullable(organizationRatings.get(List.of(userId, organizationId)));
    }

    @Override
    public Map<UUID, Tally> organizationTallies(Collection<UUID> organizationIds) {
        return tallies(organizationRatings.values(), Set.copyOf(organizationIds),
                OrganizationRating::organizationId, OrganizationRating::score);
    }

    private static <T> Map<UUID, Tally> tallies(Collection<T> ratings, Set<UUID> ids, Function<T, UUID> key,
                                                Function<T, Integer> score) {
        Map<UUID, Tally> result = new HashMap<>();
        for (T rating : ratings) {
            UUID id = key.apply(rating);
            if (ids.contains(id)) {
                result.merge(id, new Tally(1, score.apply(rating)),
                        (a, b) -> new Tally(a.count() + b.count(), a.sum() + b.sum()));
            }
        }
        return result;
    }
}
