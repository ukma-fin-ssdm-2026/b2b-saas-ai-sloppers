package ua.edu.ukma.community.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ua.edu.ukma.community.auth.JdbcUserRepository;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SQL дошки на справжньому PostgreSQL з міграціями V1–V7 (див. {@link TestDatabase}). */
@DisplayName("JdbcAdvertRepository / JdbcReplyRepository на PostgreSQL")
class JdbcBoardRepositoryTest {

    private DataSource dataSource;
    private MutableClock clock;
    private JdbcUserRepository users;
    private JdbcAdvertRepository adverts;
    private JdbcReplyRepository replies;
    private User author;
    private User alice;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.dataSource();
        TestDatabase.truncateAll();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        clock = MutableClock.startingAtFixtureNow();
        users = new JdbcUserRepository(jdbc, clock);
        adverts = new JdbcAdvertRepository(jdbc, clock);
        replies = new JdbcReplyRepository(jdbc, clock);
        author = user("author");
        alice = user("alice");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private User user(String name) {
        return users.save(new User(UUID.randomUUID(), name + "@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null,
                now(), now()));
    }

    private Advert advert(AdvertType type, OffsetDateTime deadline, AdvertStatus status) {
        return adverts.save(new Advert(UUID.randomUUID(), author.id(), type, "Шукаю бекендера", "Хакатон", 2, deadline,
                "@team_seeker", status, now(), now()));
    }

    private Reply reply(Advert advert, User from) {
        return replies.save(Reply.create(advert.id(), from.id(), "Привіт", now()));
    }

    @Test
    @DisplayName("оголошення зберігається й читається без втрат, час — у зоні годинника")
    void advertRoundTrip() {
        Advert saved = advert(AdvertType.TEAM, now().plusDays(2), AdvertStatus.ACTIVE);

        Advert loaded = adverts.findById(saved.id()).orElseThrow();

        assertThat(loaded).isEqualTo(saved);
        assertThat(loaded.contact()).isEqualTo("@team_seeker");
        assertThat(adverts.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findOpen: лише ACTIVE з дедлайном у майбутньому; фільтр за типом і без нього")
    void findOpenFiltersInSql() {
        Advert team = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        Advert leisure = advert(AdvertType.LEISURE, now().plusDays(1), AdvertStatus.ACTIVE);
        advert(AdvertType.TEAM, now().minusMinutes(1), AdvertStatus.ACTIVE);
        advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.CLOSED);
        advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.DELETED);

        assertThat(adverts.findOpen(null, now())).extracting(Advert::id)
                .containsExactlyInAnyOrder(team.id(), leisure.id());
        assertThat(adverts.findOpen(AdvertType.LEISURE, now())).extracting(Advert::id)
                .containsExactly(leisure.id());
    }

    @Test
    @DisplayName("findByAuthor без видалених; findAllById — порожній список і кілька id")
    void findByAuthorAndIds() {
        Advert active = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        Advert closed = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.CLOSED);
        Advert deleted = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.DELETED);

        assertThat(adverts.findByAuthor(author.id())).extracting(Advert::id)
                .containsExactlyInAnyOrder(active.id(), closed.id());
        assertThat(adverts.findByAuthor(alice.id())).isEmpty();
        assertThat(adverts.findAllById(List.of())).isEmpty();
        assertThat(adverts.findAllById(List.of(active.id(), deleted.id(), UUID.randomUUID()))).extracting(Advert::id)
                .containsExactlyInAnyOrder(active.id(), deleted.id());
    }

    @Test
    @DisplayName("save по тому самому id оновлює статус; closeExpired закриває лише прострочені ACTIVE")
    void updateAndCloseExpired() {
        Advert advert = advert(AdvertType.TEAM, now().plusMinutes(30), AdvertStatus.ACTIVE);
        Advert later = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        clock.advance(Duration.ofMinutes(1));
        adverts.save(advert.close(now()));
        assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.CLOSED);

        Advert expiring = advert(AdvertType.TEAM, now().plusMinutes(5), AdvertStatus.ACTIVE);
        clock.advance(Duration.ofMinutes(5));

        assertThat(adverts.closeExpired(now())).isEqualTo(1);
        assertThat(adverts.closeExpired(now())).isZero();
        assertThat(adverts.findById(expiring.id()).orElseThrow().status()).isEqualTo(AdvertStatus.CLOSED);
        assertThat(adverts.findById(later.id()).orElseThrow().status()).isEqualTo(AdvertStatus.ACTIVE);
    }

    @Test
    @DisplayName("lock у транзакції повертає оголошення (SELECT ... FOR UPDATE)")
    void lockInsideTransaction() {
        Advert advert = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Optional<Advert> locked = tx.execute(status -> adverts.lock(advert.id()));
        Optional<Advert> missing = tx.execute(status -> adverts.lock(UUID.randomUUID()));

        assertThat(locked).contains(advert);
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("відгук: вставка, оновлення тим самим id, пошук за оголошенням, користувачем, парою; лічильник")
    void replyRoundTrip() {
        Advert advert = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        Reply fromAlice = reply(advert, alice);
        Reply fromBob = replies.save(Reply.create(advert.id(), user("bob").id(), null, now()));

        assertThat(replies.findById(fromAlice.id())).contains(fromAlice);
        assertThat(replies.findById(fromBob.id()).orElseThrow().message()).isNull();
        assertThat(replies.countConfirmed(advert.id())).isZero();

        Reply confirmed = replies.save(fromAlice.confirm(now()));

        assertThat(replies.findById(fromAlice.id())).contains(confirmed);
        assertThat(replies.countConfirmed(advert.id())).isEqualTo(1);
        assertThat(replies.findByAdvertAndUser(advert.id(), alice.id())).contains(confirmed);
        assertThat(replies.findByAdvertAndUser(advert.id(), author.id())).isEmpty();
        assertThat(replies.findByAdvert(advert.id())).extracting(Reply::id)
                .containsExactlyInAnyOrder(fromAlice.id(), fromBob.id());
        assertThat(replies.findByUser(alice.id())).extracting(Reply::id).containsExactly(fromAlice.id());
    }

    @Test
    @DisplayName("FR-046: другий рядок відгуку того самого користувача на те саме оголошення база не прийме")
    void oneReplyPerUserEnforcedByDatabase() {
        Advert advert = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        reply(advert, alice);

        assertThatThrownBy(() -> reply(advert, alice)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("видалення користувача прибирає його оголошення й відгуки (ON DELETE CASCADE)")
    void cascadeOnUserDelete() {
        Advert advert = advert(AdvertType.TEAM, now().plusDays(1), AdvertStatus.ACTIVE);
        Reply fromAlice = reply(advert, alice);

        JdbcClient.create(dataSource).sql("DELETE FROM app_user WHERE id = ?").param(author.id()).update();

        assertThat(adverts.findById(advert.id())).isEmpty();
        assertThat(replies.findById(fromAlice.id())).isEmpty();
    }
}
