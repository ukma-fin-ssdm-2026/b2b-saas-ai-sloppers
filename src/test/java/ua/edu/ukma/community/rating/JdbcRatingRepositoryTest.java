package ua.edu.ukma.community.rating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ua.edu.ukma.community.auth.JdbcUserRepository;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.board.Advert;
import ua.edu.ukma.community.board.AdvertStatus;
import ua.edu.ukma.community.board.AdvertType;
import ua.edu.ukma.community.board.JdbcAdvertRepository;
import ua.edu.ukma.community.organization.JdbcOrganizationRepository;
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;
import ua.edu.ukma.community.support.TestFixtures;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SQL рейтингів на справжньому PostgreSQL з міграціями V1–V9 (див. {@link TestDatabase}). */
@DisplayName("JdbcRatingRepository на PostgreSQL")
@Tag("integration")
class JdbcRatingRepositoryTest {

    private MutableClock clock;
    private JdbcRatingRepository ratings;
    private JdbcClient jdbc;
    private User author;
    private User alice;
    private User bob;
    private Advert first;
    private Advert second;
    private Organization club;
    private Organization other;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        jdbc = JdbcClient.create(TestDatabase.dataSource());
        clock = MutableClock.startingAtFixtureNow();
        JdbcUserRepository users = new JdbcUserRepository(jdbc, clock);
        JdbcAdvertRepository adverts = new JdbcAdvertRepository(jdbc, clock);
        JdbcOrganizationRepository organizations = new JdbcOrganizationRepository(jdbc, clock);
        ratings = new JdbcRatingRepository(jdbc, clock);
        author = users.save(user("author"));
        alice = users.save(user("alice"));
        bob = users.save(user("bob"));
        first = adverts.save(advert());
        second = adverts.save(advert());
        club = organizations.save(TestFixtures.organization(UUID.randomUUID(), "Дебатний клуб", "debate-club"));
        other = organizations.save(TestFixtures.organization(UUID.randomUUID(), "Клуб програмування",
                "coding-club"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private User user(String name) {
        return new User(UUID.randomUUID(), name + "@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null, now(),
                now());
    }

    private Advert advert() {
        return new Advert(UUID.randomUUID(), author.id(), AdvertType.TEAM, "Шукаю бекендера", "Хакатон", 2,
                now().plusDays(2), "@author", AdvertStatus.CLOSED, now(), now());
    }

    private AuthorRating rating(User rater, Advert advert, int score) {
        return AuthorRating.create(rater.id(), advert.authorId(), advert.id(), score, now());
    }

    @Test
    @DisplayName("оцінка автора записується один раз: повтор не кидає виняток, а повертає false")
    void authorRatingOnce() {
        assertThat(ratings.insertAuthorRating(rating(alice, first, 5))).isTrue();
        assertThat(ratings.insertAuthorRating(rating(alice, first, 1))).isFalse();
        assertThat(ratings.insertAuthorRating(rating(alice, second, 3))).as("інше оголошення").isTrue();

        assertThat(ratings.scoresGivenBy(alice.id(), List.of(first.id(), second.id())))
                .containsEntry(first.id(), 5)
                .containsEntry(second.id(), 3);
    }

    @Test
    @DisplayName("середнє автора: сума й кількість групуються за автором")
    void authorTallies() {
        ratings.insertAuthorRating(rating(alice, first, 4));
        ratings.insertAuthorRating(rating(bob, first, 5));
        ratings.insertAuthorRating(rating(alice, second, 4));

        assertThat(ratings.authorTallies(List.of(author.id(), alice.id())))
                .containsOnlyKeys(author.id())
                .containsEntry(author.id(), new RatingRepository.Tally(3, 13));
    }

    @Test
    @DisplayName("порожній список id — порожня мапа без запиту з IN ()")
    void emptyCollections() {
        assertThat(ratings.authorTallies(List.of())).isEmpty();
        assertThat(ratings.organizationTallies(List.of())).isEmpty();
        assertThat(ratings.scoresGivenBy(alice.id(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("CHECK: оцінка 1–5 і не самому собі — навіть повз сервіс")
    void databaseChecks() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO author_rating (id, rater_id, author_id, advert_id, score) "
                        + "VALUES (?, ?, ?, ?, 6)")
                .params(UUID.randomUUID(), alice.id(), author.id(), first.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO author_rating (id, rater_id, author_id, advert_id, score) "
                        + "VALUES (?, ?, ?, ?, 5)")
                .params(UUID.randomUUID(), author.id(), author.id(), first.id()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("оцінка організації: повтор змінює бал і час зміни, час першої оцінки лишається")
    void organizationUpsert() {
        OffsetDateTime firstAt = now();
        ratings.upsertOrganizationRating(new OrganizationRating(alice.id(), club.id(), 2, firstAt, firstAt));
        clock.advance(Duration.ofDays(1));
        ratings.upsertOrganizationRating(new OrganizationRating(alice.id(), club.id(), 5, now(), now()));

        OrganizationRating stored = ratings.findOrganizationRating(alice.id(), club.id()).orElseThrow();
        assertThat(stored.score()).isEqualTo(5);
        assertThat(stored.createdAt()).isEqualTo(firstAt);
        assertThat(stored.updatedAt()).isEqualTo(now());
        assertThat(ratings.findOrganizationRating(bob.id(), club.id())).isEmpty();
    }

    @Test
    @DisplayName("рейтинг організацій для сторінки каталогу")
    void organizationTallies() {
        ratings.upsertOrganizationRating(new OrganizationRating(alice.id(), club.id(), 5, now(), now()));
        ratings.upsertOrganizationRating(new OrganizationRating(bob.id(), club.id(), 4, now(), now()));
        ratings.upsertOrganizationRating(new OrganizationRating(bob.id(), other.id(), 1, now(), now()));

        assertThat(ratings.organizationTallies(List.of(club.id(), other.id())))
                .containsEntry(club.id(), new RatingRepository.Tally(2, 9))
                .containsEntry(other.id(), new RatingRepository.Tally(1, 1));
    }
}
