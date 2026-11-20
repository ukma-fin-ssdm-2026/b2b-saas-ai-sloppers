package ua.edu.ukma.community.rating;

import org.springframework.stereotype.Service;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.board.Advert;
import ua.edu.ukma.community.board.AdvertRepository;
import ua.edu.ukma.community.board.AdvertStatus;
import ua.edu.ukma.community.board.Reply;
import ua.edu.ukma.community.board.ReplyRepository;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.organization.OrganizationRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Рейтинги авторів оголошень (FR-061, FR-062) і організацій (FR-063, FR-064).
 *
 * <p>Автора оцінюють лише ті, хто з ним справді щось робив: підтверджені учасники вже закритого
 * оголошення, один раз за оголошення. Організацію оцінює будь-який користувач, крім її голів, і може
 * змінити оцінку.
 */
@Service
public class RatingService {

    private final RatingRepository ratings;
    private final AdvertRepository adverts;
    private final ReplyRepository replies;
    private final OrganizationRepository organizations;
    private final OrgRolePolicy policy;
    private final Clock clock;

    public RatingService(RatingRepository ratings, AdvertRepository adverts, ReplyRepository replies,
                         OrganizationRepository organizations, OrgRolePolicy policy, Clock clock) {
        this.ratings = ratings;
        this.adverts = adverts;
        this.replies = replies;
        this.organizations = organizations;
        this.policy = policy;
        this.clock = clock;
    }

    // ---- автор оголошення (FR-061, FR-062) ----

    /**
     * FR-061. Закритим вважається й оголошення з дедлайном, що минув, — так само, як його бачить
     * користувач ({@link Advert#effectiveStatus}). Повторна оцінка — 409, а не заміна: вимога каже
     * «один раз».
     */
    public AuthorRating rateAuthor(User rater, UUID advertId, int score) {
        Advert advert = adverts.findById(advertId)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.ADVERT_NOT_FOUND,
                        "Оголошення " + advertId + " не знайдено"));
        boolean participant = replies.findByAdvertAndUser(advertId, rater.id())
                .filter(Reply::isConfirmed)
                .isPresent();
        if (!participant) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Оцінити автора можуть лише підтверджені учасники цього оголошення");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (advert.effectiveStatus(now) != AdvertStatus.CLOSED) {
            throw new ApiException(ErrorCode.ADVERT_NOT_CLOSED,
                    "Оцінити автора можна після закриття оголошення");
        }
        AuthorRating rating = AuthorRating.create(rater.id(), advert.authorId(), advertId, score, now);
        if (!ratings.insertAuthorRating(rating)) {
            throw new ApiException(ErrorCode.ALREADY_RATED, "Ви вже оцінили автора за це оголошення");
        }
        return rating;
    }

    /** FR-062: рейтинг кожного з авторів; без оцінок — {@link RatingSummary#NONE}. */
    public Map<UUID, RatingSummary> authorSummaries(Collection<UUID> authorIds) {
        return summaries(authorIds, ratings::authorTallies);
    }

    /** Що {@code rater} уже поставив авторам цих оголошень: advertId → оцінка. */
    public Map<UUID, Integer> scoresGivenBy(User rater, Collection<UUID> advertIds) {
        return ratings.scoresGivenBy(rater.id(), Set.copyOf(advertIds));
    }

    // ---- організація (FR-063, FR-064) ----

    /**
     * FR-063: нова оцінка або зміна попередньої. Голова не оцінює власну організацію — так само, як автор
     * не відгукується на власне оголошення: інакше рейтинг накручується зсередини.
     */
    public OrganizationRatingView rateOrganization(User user, UUID organizationId, int score) {
        requireOrganization(organizationId);
        if (policy.isHead(user, organizationId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Голова не може оцінювати власну організацію");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        ratings.upsertOrganizationRating(new OrganizationRating(user.id(), organizationId, score, now, now));
        return organizationRating(user, organizationId);
    }

    /** Рейтинг організації разом з оцінкою, яку поставив {@code user} (або {@code null}). */
    public OrganizationRatingView organizationRating(User user, UUID organizationId) {
        requireOrganization(organizationId);
        Integer mine = ratings.findOrganizationRating(user.id(), organizationId)
                .map(OrganizationRating::score)
                .orElse(null);
        return new OrganizationRatingView(organizationId, organizationSummary(organizationId), mine);
    }

    /** FR-064: рейтинг кожної з організацій; без оцінок — {@link RatingSummary#NONE}. */
    public Map<UUID, RatingSummary> organizationSummaries(Collection<UUID> organizationIds) {
        return summaries(organizationIds, ratings::organizationTallies);
    }

    public RatingSummary organizationSummary(UUID organizationId) {
        return organizationSummaries(Set.of(organizationId)).get(organizationId);
    }

    // ---- допоміжне ----

    private static Map<UUID, RatingSummary> summaries(Collection<UUID> ids,
                                                      Function<Set<UUID>, Map<UUID, RatingRepository.Tally>> query) {
        Set<UUID> unique = Set.copyOf(ids);
        Map<UUID, RatingRepository.Tally> tallies = query.apply(unique);
        return unique.stream().collect(Collectors.toMap(Function.identity(),
                id -> tallies.containsKey(id) ? tallies.get(id).summary() : RatingSummary.NONE));
    }

    private void requireOrganization(UUID organizationId) {
        if (organizations.findById(organizationId).isEmpty()) {
            throw new ApiException(ErrorCode.ORGANIZATION_NOT_FOUND,
                    "Організацію " + organizationId + " не знайдено");
        }
    }

    /** Рейтинг організації з погляду конкретного користувача. */
    public record OrganizationRatingView(UUID organizationId, RatingSummary summary, Integer myScore) {
    }
}
