package ua.edu.ukma.community.rating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import ua.edu.ukma.community.auth.InMemoryHeadRepository;
import ua.edu.ukma.community.auth.InMemoryUserRepository;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.board.Advert;
import ua.edu.ukma.community.board.AdvertCreateRequest;
import ua.edu.ukma.community.board.AdvertType;
import ua.edu.ukma.community.board.BoardService;
import ua.edu.ukma.community.board.InMemoryAdvertRepository;
import ua.edu.ukma.community.board.InMemoryReplyRepository;
import ua.edu.ukma.community.board.Reply;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.moderation.ModerationGate;
import ua.edu.ukma.community.organization.InMemoryOrganizationRepository;
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.support.MutableClock;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Рейтинги (FR-061–FR-064) разом зі справжньою дошкою: «закрите» й «підтверджений» — її правила. */
@DisplayName("RatingService: оцінка автора й організації")
class RatingServiceTest {

    private MutableClock clock;
    private InMemoryUserRepository users;
    private InMemoryAdvertRepository adverts;
    private InMemoryReplyRepository replies;
    private InMemoryOrganizationRepository organizations;
    private InMemoryHeadRepository heads;
    private BoardService board;
    private RatingService ratings;

    private User author;
    private User alice;
    private User bob;
    private User carol;
    private Organization club;

    @BeforeEach
    void setUp() {
        clock = MutableClock.startingAtFixtureNow();
        users = new InMemoryUserRepository();
        adverts = new InMemoryAdvertRepository();
        replies = new InMemoryReplyRepository();
        organizations = new InMemoryOrganizationRepository(clock);
        heads = new InMemoryHeadRepository(users);
        board = new BoardService(adverts, replies, ModerationGate.permissive(),
                TransactionOperations.withoutTransaction(), clock);
        ratings = new RatingService(new InMemoryRatingRepository(), adverts, replies, organizations,
                new OrgRolePolicy(heads), clock);
        author = user("author");
        alice = user("alice");
        bob = user("bob");
        carol = user("carol");
        club = organizations.findAll().get(0);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private User user(String name) {
        return users.save(new User(UUID.randomUUID(), name + "@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null,
                now(), now()));
    }

    private Advert advert(int slots) {
        return board.create(author, new AdvertCreateRequest(AdvertType.TEAM, "Шукаю бекендера", "Хакатон", slots,
                now().plusDays(2), "@author")).advert();
    }

    /** Оголошення, де {@code participants} підтверджені й місця закінчились — воно закрите (FR-050). */
    private Advert closedWith(User... participants) {
        Advert advert = advert(participants.length);
        for (User participant : participants) {
            Reply reply = board.reply(participant, advert.id(), null);
            board.confirm(author, advert.id(), reply.id());
        }
        return advert;
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("FR-061: оцінка автора учасником")
    class RateAuthor {

        @Test
        @DisplayName("підтверджений учасник закритого оголошення оцінює автора")
        void participantRates() {
            Advert advert = closedWith(alice);

            AuthorRating rating = ratings.rateAuthor(alice, advert.id(), 5);

            assertThat(rating.authorId()).isEqualTo(author.id());
            assertThat(ratings.scoresGivenBy(alice, List.of(advert.id()))).containsEntry(advert.id(), 5);
            assertThat(ratings.authorSummaries(List.of(author.id())))
                    .containsEntry(author.id(), new RatingSummary(5.0, 1));
        }

        @Test
        @DisplayName("лише один раз за оголошення")
        void once() {
            Advert advert = closedWith(alice);
            ratings.rateAuthor(alice, advert.id(), 5);

            assertCode(() -> ratings.rateAuthor(alice, advert.id(), 1), ErrorCode.ALREADY_RATED);
            assertThat(ratings.scoresGivenBy(alice, List.of(advert.id()))).containsEntry(advert.id(), 5);
        }

        @Test
        @DisplayName("поки оголошення відкрите — 409, після дедлайну вже можна")
        void onlyWhenClosed() {
            Advert advert = advert(3);
            Reply reply = board.reply(alice, advert.id(), null);
            board.confirm(author, advert.id(), reply.id());

            assertCode(() -> ratings.rateAuthor(alice, advert.id(), 4), ErrorCode.ADVERT_NOT_CLOSED);

            clock.advance(Duration.ofDays(3));
            assertThat(ratings.rateAuthor(alice, advert.id(), 4).score()).isEqualTo(4);
        }

        @Test
        @DisplayName("не учасник, непідтверджений, відхилений і сам автор оцінити не можуть")
        void onlyConfirmedParticipants() {
            Advert advert = advert(1);
            Reply rejected = board.reply(bob, advert.id(), null);
            board.reject(author, advert.id(), rejected.id());
            board.reply(carol, advert.id(), null);
            board.confirm(author, advert.id(), board.reply(alice, advert.id(), null).id());

            assertCode(() -> ratings.rateAuthor(bob, advert.id(), 1), ErrorCode.FORBIDDEN);
            assertCode(() -> ratings.rateAuthor(carol, advert.id(), 1), ErrorCode.FORBIDDEN);
            assertCode(() -> ratings.rateAuthor(user("stranger"), advert.id(), 1), ErrorCode.FORBIDDEN);
            assertCode(() -> ratings.rateAuthor(author, advert.id(), 5), ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("видалене чи неіснуюче оголошення — 404")
        void missingAdvert() {
            Advert advert = closedWith(alice);
            board.delete(author, advert.id());

            assertCode(() -> ratings.rateAuthor(alice, advert.id(), 5), ErrorCode.ADVERT_NOT_FOUND);
            assertCode(() -> ratings.rateAuthor(alice, UUID.randomUUID(), 5), ErrorCode.ADVERT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("FR-062: рейтинг автора")
    class AuthorSummary {

        @Test
        @DisplayName("середнє за всіма оголошеннями автора, округлене до десятих")
        void averageAcrossAdverts() {
            Advert first = closedWith(alice, bob);
            ratings.rateAuthor(alice, first.id(), 4);
            ratings.rateAuthor(bob, first.id(), 4);
            clock.advance(Duration.ofDays(1));
            Advert second = closedWith(carol);
            ratings.rateAuthor(carol, second.id(), 5);

            assertThat(ratings.authorSummaries(List.of(author.id())).get(author.id()))
                    .isEqualTo(new RatingSummary(4.3, 3));
        }

        @Test
        @DisplayName("автор без оцінок — порожній рейтинг, а не відсутній ключ")
        void noRatings() {
            Map<UUID, RatingSummary> summaries = ratings.authorSummaries(List.of(author.id(), alice.id()));

            assertThat(summaries).containsEntry(author.id(), RatingSummary.NONE).containsEntry(alice.id(),
                    RatingSummary.NONE);
            assertThat(RatingSummary.NONE.average()).isNull();
        }

        @Test
        @DisplayName("глядач бачить лише власні оцінки")
        void scoresGivenByViewerOnly() {
            Advert advert = closedWith(alice, bob);
            ratings.rateAuthor(alice, advert.id(), 2);

            assertThat(ratings.scoresGivenBy(bob, List.of(advert.id()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("FR-063, FR-064: оцінка організації")
    class RateOrganization {

        @Test
        @DisplayName("оцінка зберігається, повторна — змінює її, а не додає нову")
        void rateAndChange() {
            ratings.rateOrganization(alice, club.id(), 2);

            RatingService.OrganizationRatingView changed = ratings.rateOrganization(alice, club.id(), 5);

            assertThat(changed.myScore()).isEqualTo(5);
            assertThat(changed.summary()).isEqualTo(new RatingSummary(5.0, 1));
        }

        @Test
        @DisplayName("середнє за всіма користувачами; хто не оцінював — бачить null")
        void averageAndMine() {
            ratings.rateOrganization(alice, club.id(), 5);
            ratings.rateOrganization(bob, club.id(), 4);

            RatingService.OrganizationRatingView carolView = ratings.organizationRating(carol, club.id());

            assertThat(carolView.myScore()).isNull();
            assertThat(carolView.summary()).isEqualTo(new RatingSummary(4.5, 2));
        }

        @Test
        @DisplayName("голова не оцінює власну організацію, але може оцінити чужу")
        void headCannotRateOwn() {
            heads.appoint(club.id(), alice.id(), author.id(), now());
            Organization other = organizations.findAll().get(1);

            assertCode(() -> ratings.rateOrganization(alice, club.id(), 5), ErrorCode.FORBIDDEN);
            assertThat(ratings.rateOrganization(alice, other.id(), 5).myScore()).isEqualTo(5);
        }

        @Test
        @DisplayName("неіснуюча організація — 404")
        void missingOrganization() {
            assertCode(() -> ratings.rateOrganization(alice, UUID.randomUUID(), 5), ErrorCode.ORGANIZATION_NOT_FOUND);
            assertCode(() -> ratings.organizationRating(alice, UUID.randomUUID()), ErrorCode.ORGANIZATION_NOT_FOUND);
        }

        @Test
        @DisplayName("каталог: рейтинг для кожної організації сторінки")
        void summariesForCatalog() {
            Organization other = organizations.findAll().get(1);
            ratings.rateOrganization(alice, club.id(), 3);

            assertThat(ratings.organizationSummaries(List.of(club.id(), other.id())))
                    .containsEntry(club.id(), new RatingSummary(3.0, 1))
                    .containsEntry(other.id(), RatingSummary.NONE);
        }
    }

    @Test
    @DisplayName("RatingSummary.of округлює до десятих: 13/3 → 4.3, 11/3 → 3.7")
    void rounding() {
        assertThat(RatingSummary.of(3, 13)).isEqualTo(new RatingSummary(4.3, 3));
        assertThat(RatingSummary.of(3, 11)).isEqualTo(new RatingSummary(3.7, 3));
        assertThat(RatingSummary.of(0, 0)).isSameAs(RatingSummary.NONE);
    }
}
