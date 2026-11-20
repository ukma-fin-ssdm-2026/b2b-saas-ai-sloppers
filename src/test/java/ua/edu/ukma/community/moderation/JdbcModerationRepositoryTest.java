package ua.edu.ukma.community.moderation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ua.edu.ukma.community.auth.JdbcUserRepository;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.board.Advert;
import ua.edu.ukma.community.board.AdvertStatus;
import ua.edu.ukma.community.board.AdvertType;
import ua.edu.ukma.community.board.JdbcAdvertRepository;
import ua.edu.ukma.community.board.JdbcReplyRepository;
import ua.edu.ukma.community.board.Reply;
import ua.edu.ukma.community.board.ReplyStatus;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SQL модерації на справжньому PostgreSQL з міграціями V1–V8 (див. {@link TestDatabase}). Перевіряє те,
 * чого сховище в пам'яті показати не може: перетворення цілі скарги у два зовнішні ключі, часткові
 * унікальні індекси, {@code CHECK} і розширений у V8 статус відгуку.
 */
@DisplayName("JdbcModerationRepository на PostgreSQL")
@Tag("integration")
class JdbcModerationRepositoryTest {

    private MutableClock clock;
    private JdbcUserRepository users;
    private JdbcAdvertRepository adverts;
    private JdbcReplyRepository replies;
    private JdbcModerationRepository moderation;
    private JdbcClient jdbc;
    private User author;
    private User alice;
    private User bob;
    private Advert advert;
    private Reply reply;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        jdbc = JdbcClient.create(TestDatabase.dataSource());
        clock = MutableClock.startingAtFixtureNow();
        users = new JdbcUserRepository(jdbc, clock);
        adverts = new JdbcAdvertRepository(jdbc, clock);
        replies = new JdbcReplyRepository(jdbc, clock);
        moderation = new JdbcModerationRepository(jdbc, clock);
        author = user("author");
        alice = user("alice");
        bob = user("bob");
        advert = adverts.save(new Advert(UUID.randomUUID(), author.id(), AdvertType.TEAM, "Шукаю бекендера",
                "Хакатон", 2, now().plusDays(2), "@author", AdvertStatus.ACTIVE, now(), now()));
        reply = replies.save(Reply.create(advert.id(), alice.id(), "Привіт", now()));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private User user(String name) {
        return users.save(new User(UUID.randomUUID(), name + "@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null,
                now(), now()));
    }

    private Complaint complaint(User reporter, ContentType type, UUID targetId, UUID authorId) {
        return moderation.saveComplaint(Complaint.open(reporter.id(), authorId, type, targetId,
                ComplaintCategory.SPAM, "коментар", now()));
    }

    @Test
    @DisplayName("скарга на оголошення й на відгук: ціль переживає перетворення у два зовнішні ключі")
    void complaintRoundTrip() {
        Complaint onAdvert = complaint(bob, ContentType.ADVERT, advert.id(), author.id());
        Complaint onReply = complaint(bob, ContentType.REPLY, reply.id(), alice.id());

        assertThat(moderation.findComplaint(onAdvert.id())).contains(onAdvert);
        assertThat(moderation.findComplaint(onReply.id())).contains(onReply);
        assertThat(jdbc.sql("SELECT advert_id IS NULL AND response_id = ? FROM complaint WHERE id = ?")
                .param(reply.id()).param(onReply.id()).query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("complaintExists розрізняє ціль і тип цілі")
    void complaintExists() {
        complaint(bob, ContentType.ADVERT, advert.id(), author.id());

        assertThat(moderation.complaintExists(bob.id(), ContentType.ADVERT, advert.id())).isTrue();
        assertThat(moderation.complaintExists(alice.id(), ContentType.ADVERT, advert.id())).isFalse();
        assertThat(moderation.complaintExists(bob.id(), ContentType.REPLY, reply.id())).isFalse();
    }

    @Test
    @DisplayName("повторна скарга на той самий об'єкт відбивається частковим унікальним індексом")
    void duplicateComplaintRejectedByIndex() {
        complaint(bob, ContentType.ADVERT, advert.id(), author.id());
        complaint(bob, ContentType.REPLY, reply.id(), alice.id());

        // саме DuplicateKeyException: на нього ModerationService відповідає 409 замість 500
        assertThatThrownBy(() -> complaint(bob, ContentType.ADVERT, advert.id(), author.id()))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> complaint(bob, ContentType.REPLY, reply.id(), alice.id()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("скарга на неіснуючий об'єкт не проходить зовнішній ключ")
    void danglingTargetRejected() {
        assertThatThrownBy(() -> complaint(bob, ContentType.ADVERT, UUID.randomUUID(), author.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("черга: фільтр за станом, найновіші спершу, пагінація")
    void queue() {
        Complaint older = complaint(bob, ContentType.ADVERT, advert.id(), author.id());
        clock.advance(Duration.ofMinutes(1));
        Complaint newer = complaint(bob, ContentType.REPLY, reply.id(), alice.id());
        moderation.saveComplaint(older.resolve(ComplaintStatus.DISMISSED, author.id(), "ок", now()));

        assertThat(moderation.findComplaints(null, 10, 0)).extracting(Complaint::id)
                .containsExactly(newer.id(), older.id());
        assertThat(moderation.findComplaints(ComplaintStatus.OPEN, 10, 0)).containsExactly(newer);
        assertThat(moderation.findComplaints(null, 1, 1)).extracting(Complaint::id).containsExactly(older.id());
        assertThat(moderation.countComplaints(null)).isEqualTo(2);
        assertThat(moderation.countComplaints(ComplaintStatus.DISMISSED)).isEqualTo(1);
    }

    @Test
    @DisplayName("скаржники на автора рахуються без повторів і без відхилених скарг")
    void countComplainants() {
        Advert second = adverts.save(new Advert(UUID.randomUUID(), author.id(), AdvertType.LEISURE, "Настолки",
                "Вечір", 4, now().plusDays(1), "@author", AdvertStatus.ACTIVE, now(), now()));
        complaint(bob, ContentType.ADVERT, advert.id(), author.id());
        complaint(bob, ContentType.ADVERT, second.id(), author.id());
        Complaint dismissed = complaint(alice, ContentType.ADVERT, advert.id(), author.id());

        assertThat(moderation.countComplainantsAgainst(author.id())).isEqualTo(2);
        moderation.saveComplaint(dismissed.resolve(ComplaintStatus.DISMISSED, bob.id(), null, now()));
        assertThat(moderation.countComplainantsAgainst(author.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("resolveComplaintsOn закриває лише відкриті скарги на цей об'єкт")
    void resolveOnTarget() {
        Complaint fromBob = complaint(bob, ContentType.ADVERT, advert.id(), author.id());
        Complaint fromAlice = complaint(alice, ContentType.ADVERT, advert.id(), author.id());
        Complaint onReply = complaint(bob, ContentType.REPLY, reply.id(), alice.id());
        moderation.saveComplaint(fromAlice.resolve(ComplaintStatus.DISMISSED, bob.id(), "раніше", now()));

        int closed = moderation.resolveComplaintsOn(ContentType.ADVERT, advert.id(), ComplaintStatus.RESOLVED,
                bob.id(), "прибрано", now());

        assertThat(closed).isEqualTo(1);
        assertThat(moderation.findComplaint(fromBob.id()).orElseThrow().status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(moderation.findComplaint(fromAlice.id()).orElseThrow().status())
                .isEqualTo(ComplaintStatus.DISMISSED);
        assertThat(moderation.findComplaint(onReply.id()).orElseThrow().isOpen()).isTrue();
    }

    @Test
    @DisplayName("бан: збереження, зняття, вибірка прострочених і заблокованих на момент часу")
    void bans() {
        Ban temporary = moderation.saveBan(Ban.create(alice.id(), author.id(), "Спам", now().plusHours(1),
                UserStatus.ACTIVE, now()));
        Ban permanent = moderation.saveBan(Ban.create(bob.id(), null, "Авто", null, UserStatus.PENDING, now()));

        assertThat(moderation.findActiveBan(alice.id())).contains(temporary);
        assertThat(moderation.findBannedUsers(now())).containsExactlyInAnyOrder(alice.id(), bob.id());
        assertThat(moderation.findExpiredBans(now())).isEmpty();

        clock.advance(Duration.ofHours(2));
        assertThat(moderation.findBannedUsers(now())).containsExactly(bob.id());
        assertThat(moderation.findExpiredBans(now())).containsExactly(temporary);

        Ban lifted = moderation.saveBan(temporary.lift(null, now()));
        assertThat(moderation.findActiveBan(alice.id())).isEmpty();
        assertThat(moderation.findBans(alice.id())).containsExactly(lifted);
        assertThat(moderation.findActiveBan(bob.id()).orElseThrow().previousStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(permanent.isAutomatic()).isTrue();
    }

    @Test
    @DisplayName("другий незнятий бан на ту саму людину база не пускає навіть повз сервіс")
    void oneActiveBanPerUser() {
        moderation.saveBan(Ban.create(alice.id(), author.id(), "Перший", null, UserStatus.ACTIVE, now()));

        assertThatThrownBy(() -> moderation.saveBan(Ban.create(alice.id(), author.id(), "Другий", null,
                UserStatus.ACTIVE, now()))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("заборонені слова: нормалізоване слово унікальне, сортування за словом")
    void bannedWords() {
        BannedWord spam = moderation.saveBannedWord(BannedWord.of("Спам", author.id(), now()));
        moderation.saveBannedWord(BannedWord.of("казино", author.id(), now()));

        assertThat(moderation.findBannedWords()).extracting(BannedWord::word).containsExactly("казино", "спам");
        assertThatThrownBy(() -> moderation.saveBannedWord(BannedWord.of("СПАМ", null, now())))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(moderation.deleteBannedWord(spam.id())).isTrue();
        assertThat(moderation.deleteBannedWord(spam.id())).isFalse();
    }

    @Test
    @DisplayName("lower() у PostgreSQL залежить від локалі: тому регістр слова нормалізує застосунок")
    void lowerDependsOnLocale() {
        String collation = jdbc.sql("SELECT datctype FROM pg_database WHERE datname = current_database()")
                .query(String.class).single();
        String lowered = jdbc.sql("SELECT lower('СПАМ')").query(String.class).single();

        // у локалі C кирилиця не змінюється; CHECK (word = lower(word)) пропустив би «СПАМ»
        assertThat(lowered).isEqualTo("C".equals(collation) ? "СПАМ" : "спам");
        assertThat(BannedWord.normalize(" СПАМ ")).isEqualTo("спам");
    }

    @Test
    @DisplayName("відмови автофільтра рахуються у вікні")
    void rejections() {
        moderation.saveRejection(FilterRejection.of(alice.id(), ContentType.REPLY, "спам", now()));
        clock.advance(Duration.ofHours(2));
        moderation.saveRejection(FilterRejection.of(alice.id(), ContentType.ADVERT, "спам", now()));

        assertThat(moderation.countRejectionsSince(alice.id(), now().minusHours(1))).isEqualTo(1);
        assertThat(moderation.countRejectionsSince(alice.id(), now().minusHours(3))).isEqualTo(2);
        assertThat(moderation.countRejectionsSince(bob.id(), now().minusHours(3))).isZero();
    }

    @Test
    @DisplayName("V8: відгук у статусі REMOVED проходить CHECK і читається назад")
    void removedReplyPersists() {
        replies.save(reply.remove(now()));

        assertThat(replies.findById(reply.id()).orElseThrow().status()).isEqualTo(ReplyStatus.REMOVED);
    }

    @Test
    @DisplayName("FR-059: лічильники дошки враховують вікно, а видалені оголошення — теж")
    void boardCountersForLimits() {
        adverts.save(advert.delete(now()));
        OffsetDateTime hourAgo = now().minusHours(1);

        assertThat(adverts.countByAuthorSince(author.id(), hourAgo)).isEqualTo(1);
        assertThat(replies.countByUserSince(alice.id(), hourAgo)).isEqualTo(1);
        clock.advance(Duration.ofHours(2));
        assertThat(adverts.countByAuthorSince(author.id(), now().minusHours(1))).isZero();
        assertThat(replies.countByUserSince(alice.id(), now().minusHours(1))).isZero();
    }
}
