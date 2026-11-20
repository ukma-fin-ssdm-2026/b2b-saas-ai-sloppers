package ua.edu.ukma.community.moderation;

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
import ua.edu.ukma.community.board.AdvertStatus;
import ua.edu.ukma.community.board.AdvertType;
import ua.edu.ukma.community.board.AdvertView;
import ua.edu.ukma.community.board.BoardService;
import ua.edu.ukma.community.board.InMemoryAdvertRepository;
import ua.edu.ukma.community.board.InMemoryReplyRepository;
import ua.edu.ukma.community.board.Reply;
import ua.edu.ukma.community.board.ReplyStatus;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.support.MutableClock;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Модерація разом із дошкою (FR-054–FR-060): справжні {@link ModerationService} і {@link BoardService}
 * над сховищами в пам'яті, годинник перемотується. Дошку не підмінено заглушкою навмисно — FR-055,
 * FR-058 і FR-059 користувач відчуває саме через неї.
 */
@DisplayName("ModerationService: блокування, скарги, автофільтр, ліміти, автоблокування")
class ModerationServiceTest {

    private static final int ADVERTS_PER_DAY = 1;
    private static final int REPLIES_PER_HOUR = 3;
    private static final int AUTO_BAN_COMPLAINANTS = 3;
    private static final int AUTO_BAN_REJECTIONS = 3;
    private static final Duration AUTO_BAN_DURATION = Duration.ofHours(72);

    private static final ModerationProperties PROPERTIES = new ModerationProperties(
            new ModerationProperties.Limits(ADVERTS_PER_DAY, Duration.ofHours(24), REPLIES_PER_HOUR,
                    Duration.ofHours(1)),
            new ModerationProperties.AutoBan(AUTO_BAN_COMPLAINANTS, AUTO_BAN_REJECTIONS, Duration.ofHours(24),
                    AUTO_BAN_DURATION));

    private MutableClock clock;
    private InMemoryUserRepository users;
    private InMemoryAdvertRepository adverts;
    private InMemoryReplyRepository replies;
    private InMemoryModerationRepository repository;
    private ModerationService moderation;
    private BoardService board;

    private User admin;
    private User author;
    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        clock = MutableClock.startingAtFixtureNow();
        users = new InMemoryUserRepository();
        adverts = new InMemoryAdvertRepository();
        replies = new InMemoryReplyRepository();
        repository = new InMemoryModerationRepository();
        TransactionOperations noTx = TransactionOperations.withoutTransaction();
        moderation = new ModerationService(repository, new BoardContent(adverts, replies, clock), users,
                new OrgRolePolicy(new InMemoryHeadRepository(users)), new WordFilter(repository), PROPERTIES,
                noTx, clock);
        board = new BoardService(adverts, replies, moderation, noTx, clock);

        admin = user("admin", UserStatus.ACTIVE, true);
        author = user("author");
        alice = user("alice");
        bob = user("bob");
        carol = user("carol");
    }

    // ---- допоміжне ----

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private User user(String name) {
        return user(name, UserStatus.ACTIVE, false);
    }

    private User user(String name, UserStatus status, boolean globalAdmin) {
        OffsetDateTime at = now();
        return users.save(new User(UUID.randomUUID(), name + "@ukma.edu.ua", "hash", status, globalAdmin, null,
                at, at));
    }

    private User reload(User user) {
        return users.findById(user.id()).orElseThrow();
    }

    private Advert advert(User by) {
        return advert(by, "Шукаю бекендера", "Хакатон на вихідних");
    }

    private Advert advert(User by, String title, String description) {
        AdvertView view = board.create(by, new AdvertCreateRequest(AdvertType.TEAM, title, description, 3,
                now().plusDays(2), "@" + by.email().split("@")[0]));
        return view.advert();
    }

    private Complaint complain(User reporter, Advert advert) {
        return moderation.complain(reporter, ContentType.ADVERT, advert.id(), ComplaintCategory.SPAM, null);
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((ApiException) thrown).code();
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(expected));
    }

    // ---- FR-054 ----

    @Nested
    @DisplayName("FR-054: блокування Global Admin")
    class Banning {

        @Test
        @DisplayName("бан на строк: запис у журналі й статус BLOCKED з'являються разом")
        void banForTerm() {
            Ban ban = moderation.ban(admin, alice.id(), " Спам ", now().plusDays(7));

            assertThat(ban.isActive(now())).isTrue();
            assertThat(ban.isAutomatic()).isFalse();
            assertThat(ban.reason()).isEqualTo("Спам");
            assertThat(ban.previousStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(reload(alice).status()).isEqualTo(UserStatus.BLOCKED);
            assertThat(moderation.listBans(admin, alice.id())).containsExactly(ban);
        }

        @Test
        @DisplayName("блокувати може лише Global Admin")
        void onlyAdmin() {
            assertCode(() -> moderation.ban(alice, bob.id(), "просто так", null), ErrorCode.FORBIDDEN);
            assertThat(reload(bob).status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("себе й іншого Global Admin заблокувати не можна")
        void notSelfNorAdmin() {
            User otherAdmin = user("admin2", UserStatus.ACTIVE, true);

            assertCode(() -> moderation.ban(admin, admin.id(), "x", null), ErrorCode.FORBIDDEN);
            assertCode(() -> moderation.ban(admin, otherAdmin.id(), "x", null), ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("невідомий користувач — 404, повторний бан — 409, строк у минулому — 400")
        void invalidRequests() {
            moderation.ban(admin, alice.id(), "Спам", null);

            assertCode(() -> moderation.ban(admin, UUID.randomUUID(), "x", null), ErrorCode.USER_NOT_FOUND);
            assertCode(() -> moderation.ban(admin, alice.id(), "ще раз", null), ErrorCode.ALREADY_BANNED);
            assertCode(() -> moderation.ban(admin, bob.id(), "x", now().minusMinutes(1)),
                    ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("розблокування повертає статус, що був до бану: непідтверджений лишається PENDING")
        void unbanRestoresPreviousStatus() {
            User pending = user("pending", UserStatus.PENDING, false);
            moderation.ban(admin, pending.id(), "Спам", null);

            Ban lifted = moderation.unban(admin, pending.id());

            assertThat(lifted.isLifted()).isTrue();
            assertThat(lifted.liftedBy()).isEqualTo(admin.id());
            assertThat(reload(pending).status()).isEqualTo(UserStatus.PENDING);
        }

        @Test
        @DisplayName("розблокувати незаблокованого — 409")
        void unbanWithoutBan() {
            assertCode(() -> moderation.unban(admin, alice.id()), ErrorCode.NOT_BANNED);
        }

        @Test
        @DisplayName("TC-039: після строку планувальник знімає бан, у журналі — без людини, що зняла")
        void schedulerLiftsExpired() {
            moderation.ban(admin, alice.id(), "Спам", now().plusDays(1));
            clock.advance(Duration.ofDays(1).plusMinutes(1));

            assertThat(moderation.liftExpiredBans()).isEqualTo(1);

            assertThat(reload(alice).status()).isEqualTo(UserStatus.ACTIVE);
            Ban ban = moderation.listBans(admin, alice.id()).get(0);
            assertThat(ban.isLifted()).isTrue();
            assertThat(ban.liftedBy()).isNull();
            assertThat(moderation.liftExpiredBans()).as("повторний запуск нічого не змінює").isZero();
        }

        @Test
        @DisplayName("безстроковий бан планувальник не чіпає")
        void permanentBanStays() {
            moderation.ban(admin, alice.id(), "Спам", null);
            clock.advance(Duration.ofDays(365));

            assertThat(moderation.liftExpiredBans()).isZero();
            assertThat(reload(alice).status()).isEqualTo(UserStatus.BLOCKED);
        }

        @Test
        @DisplayName("новий бан після простроченого, якого планувальник ще не зняв, проходить")
        void rebanAfterExpiry() {
            moderation.ban(admin, alice.id(), "Перший", now().plusDays(1));
            clock.advance(Duration.ofDays(2));

            Ban second = moderation.ban(admin, alice.id(), "Другий", null);

            List<Ban> history = moderation.listBans(admin, alice.id());
            assertThat(history).hasSize(2);
            assertThat(history).filteredOn(b -> !b.isLifted()).containsExactly(second);
            assertThat(second.previousStatus()).as("статус до першого бану").isEqualTo(UserStatus.ACTIVE);
        }
    }

    // ---- FR-055 ----

    @Nested
    @DisplayName("FR-055: оголошення заблокованого приховані")
    class Hiding {

        @Test
        @DisplayName("TC-038: зникає зі списку активних і за прямим посиланням")
        void hiddenFromOthers() {
            Advert advert = advert(author);
            moderation.ban(admin, author.id(), "Спам", null);

            assertThat(board.listOpen(alice, null, 20, 0).data()).isEmpty();
            assertCode(() -> board.get(alice, advert.id()), ErrorCode.ADVERT_NOT_FOUND);
        }

        @Test
        @DisplayName("автор і ті, хто вже відгукнувся, оголошення бачать — контакт не губиться")
        void visibleToAuthorAndRepliers() {
            Advert advert = advert(author);
            board.reply(alice, advert.id(), "Я з вами");
            moderation.ban(admin, author.id(), "Спам", null);

            assertThat(board.get(alice, advert.id()).advert().id()).isEqualTo(advert.id());
            assertThat(board.get(author, advert.id()).advert().id()).isEqualTo(advert.id());
        }

        @Test
        @DisplayName("після розблокування оголошення повертається на дошку")
        void returnsAfterUnban() {
            Advert advert = advert(author);
            moderation.ban(admin, author.id(), "Спам", null);
            moderation.unban(admin, author.id());

            assertThat(board.listOpen(alice, null, 20, 0).data())
                    .extracting(v -> v.advert().id())
                    .containsExactly(advert.id());
        }

        @Test
        @DisplayName("прострочений бан нічого не приховує ще до запуску планувальника")
        void expiredBanHidesNothing() {
            advert(author);
            moderation.ban(admin, author.id(), "Спам", now().plusHours(1));
            clock.advance(Duration.ofHours(2));

            assertThat(board.listOpen(alice, null, 20, 0).data()).hasSize(1);
        }
    }

    // ---- FR-056 ----

    @Nested
    @DisplayName("FR-056: скарги користувачів")
    class Complaints {

        @Test
        @DisplayName("скарга на оголошення відкрита й пам'ятає автора контенту")
        void complaintOnAdvert() {
            Advert advert = advert(author);

            Complaint complaint = moderation.complain(alice, ContentType.ADVERT, advert.id(),
                    ComplaintCategory.ABUSE, "  Образи в описі  ");

            assertThat(complaint.isOpen()).isTrue();
            assertThat(complaint.authorId()).isEqualTo(author.id());
            assertThat(complaint.comment()).isEqualTo("Образи в описі");
        }

        @Test
        @DisplayName("порожній коментар зберігається як відсутній")
        void blankComment() {
            Advert advert = advert(author);

            Complaint complaint = moderation.complain(alice, ContentType.ADVERT, advert.id(),
                    ComplaintCategory.OTHER, "   ");

            assertThat(complaint.comment()).isNull();
        }

        @Test
        @DisplayName("на власний контент скаржитися не можна")
        void notOwnContent() {
            Advert advert = advert(author);

            assertCode(() -> complain(author, advert), ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("одна скарга на той самий об'єкт від того самого користувача")
        void oncePerTarget() {
            Advert advert = advert(author);
            complain(alice, advert);

            assertCode(() -> complain(alice, advert), ErrorCode.ALREADY_COMPLAINED);
        }

        @Test
        @DisplayName("на неіснуючий чи прибраний контент — 404")
        void missingTarget() {
            assertCode(() -> moderation.complain(alice, ContentType.ADVERT, UUID.randomUUID(),
                    ComplaintCategory.SPAM, null), ErrorCode.ADVERT_NOT_FOUND);
            assertCode(() -> moderation.complain(alice, ContentType.REPLY, UUID.randomUUID(),
                    ComplaintCategory.SPAM, null), ErrorCode.REPLY_NOT_FOUND);

            Advert advert = advert(author);
            board.delete(author, advert.id());
            assertCode(() -> complain(alice, advert), ErrorCode.ADVERT_NOT_FOUND);
        }
    }

    // ---- FR-057 ----

    @Nested
    @DisplayName("FR-057: черга скарг Global Admin")
    class Queue {

        @Test
        @DisplayName("TC-037: підтверджена скарга прибирає оголошення і закриває інші скарги на нього")
        void resolveRemovesAdvert() {
            Advert advert = advert(author);
            Complaint fromAlice = complain(alice, advert);
            Complaint fromBob = complain(bob, advert);

            Complaint resolved = moderation.resolve(admin, fromAlice.id(), "Реклама");

            assertThat(resolved.status()).isEqualTo(ComplaintStatus.RESOLVED);
            assertThat(resolved.resolvedBy()).isEqualTo(admin.id());
            assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.DELETED);
            assertThat(repository.findComplaint(fromBob.id()).orElseThrow().status())
                    .isEqualTo(ComplaintStatus.RESOLVED);
            assertThat(moderation.listComplaints(admin, ComplaintStatus.OPEN, 20, 0).data()).isEmpty();
        }

        @Test
        @DisplayName("підтверджена скарга на відгук переводить його в REMOVED, автор оголошення його не бачить")
        void resolveRemovesReply() {
            Advert advert = advert(author);
            Reply reply = board.reply(bob, advert.id(), "Купуйте курси");
            Complaint complaint = moderation.complain(author, ContentType.REPLY, reply.id(),
                    ComplaintCategory.SPAM, null);

            moderation.resolve(admin, complaint.id(), null);

            assertThat(replies.findById(reply.id()).orElseThrow().status()).isEqualTo(ReplyStatus.REMOVED);
            assertThat(board.listReplies(author, advert.id())).isEmpty();
            assertThat(board.listReplied(bob, 20, 0).data()).isEmpty();
        }

        @Test
        @DisplayName("відхилена скарга лишає контент на місці")
        void dismissKeepsContent() {
            Advert advert = advert(author);
            Complaint complaint = complain(alice, advert);

            Complaint dismissed = moderation.dismiss(admin, complaint.id(), "Порушення немає");

            assertThat(dismissed.status()).isEqualTo(ComplaintStatus.DISMISSED);
            assertThat(dismissed.resolution()).isEqualTo("Порушення немає");
            assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.ACTIVE);
        }

        @Test
        @DisplayName("рішення остаточне: другий адміністратор не прибере контент удруге")
        void decisionIsFinal() {
            Advert advert = advert(author);
            Complaint complaint = complain(alice, advert);
            moderation.dismiss(admin, complaint.id(), null);

            assertCode(() -> moderation.resolve(admin, complaint.id(), null), ErrorCode.INVALID_STATE_TRANSITION);
            assertCode(() -> moderation.resolve(admin, UUID.randomUUID(), null), ErrorCode.COMPLAINT_NOT_FOUND);
        }

        @Test
        @DisplayName("черга лише для Global Admin, фільтр за станом і пагінація")
        void queue() {
            Advert first = advert(author);
            Advert second = advert(carol);
            complain(alice, first);
            clock.advance(Duration.ofMinutes(1));
            Complaint newest = complain(alice, second);
            Complaint dismissed = complain(bob, first);
            moderation.dismiss(admin, dismissed.id(), null);

            assertCode(() -> moderation.listComplaints(alice, null, 20, 0), ErrorCode.FORBIDDEN);
            OffsetPage<Complaint> open = moderation.listComplaints(admin, ComplaintStatus.OPEN, 1, 0);
            assertThat(open.data()).containsExactly(newest);
            assertThat(open.pagination().total()).isEqualTo(2);
            assertThat(open.pagination().hasMore()).isTrue();
            assertThat(moderation.listComplaints(admin, null, 20, 0).pagination().total()).isEqualTo(3);
        }
    }

    // ---- FR-058 ----

    @Nested
    @DisplayName("FR-058: автофільтр заборонених слів")
    class Filter {

        @Test
        @DisplayName("оголошення із забороненим словом відхиляється незалежно від регістру")
        void rejectsAdvert() {
            moderation.addBannedWord(admin, "казино");

            assertCode(() -> advert(author, "Онлайн КАЗИНО!", "Бонус"), ErrorCode.CONTENT_REJECTED);
            assertThat(adverts.findByAuthor(author.id())).isEmpty();
            assertThat(repository.countRejectionsSince(author.id(), now().minusMinutes(1))).isEqualTo(1);
        }

        @Test
        @DisplayName("слово, записане в базу не в нижньому регістрі, однаково спрацьовує")
        void storedWordIsNormalized() {
            repository.saveBannedWord(new BannedWord(UUID.randomUUID(), "Казино", null, now()));

            assertCode(() -> advert(author, "Вечір у казино", "Настолки"), ErrorCode.CONTENT_REJECTED);
        }

        @Test
        @DisplayName("порівнюються слова цілком: «кіт» у списку не блокує «Нікіту»")
        void wholeWordsOnly() {
            moderation.addBannedWord(admin, "кіт");

            Advert advert = advert(author, "Шукаю Нікіту", "Для команди на хакатон");

            assertThat(advert.title()).isEqualTo("Шукаю Нікіту");
        }

        @Test
        @DisplayName("відгук теж фільтрується")
        void rejectsReply() {
            Advert advert = advert(author);
            moderation.addBannedWord(admin, "спам");

            assertCode(() -> board.reply(alice, advert.id(), "Це спам, перевірте"), ErrorCode.CONTENT_REJECTED);
            assertThat(replies.findByAdvert(advert.id())).isEmpty();
        }

        @Test
        @DisplayName("список веде лише Global Admin; дублікати й фрази не приймаються")
        void wordListRules() {
            moderation.addBannedWord(admin, "спам");

            assertCode(() -> moderation.addBannedWord(alice, "інше"), ErrorCode.FORBIDDEN);
            assertCode(() -> moderation.addBannedWord(admin, " Спам "), ErrorCode.BANNED_WORD_EXISTS);
            assertCode(() -> moderation.addBannedWord(admin, "два слова"), ErrorCode.VALIDATION_ERROR);
            assertCode(() -> moderation.addBannedWord(admin, "я"), ErrorCode.VALIDATION_ERROR);
            assertThat(moderation.listBannedWords(admin)).extracting(BannedWord::word).containsExactly("спам");
        }

        @Test
        @DisplayName("прибране зі списку слово більше не блокує")
        void removeWord() {
            BannedWord word = moderation.addBannedWord(admin, "казино");
            moderation.removeBannedWord(admin, word.id());

            assertThat(advert(author, "Вечір у казино", "Настолки").title()).isEqualTo("Вечір у казино");
            assertCode(() -> moderation.removeBannedWord(admin, word.id()), ErrorCode.BANNED_WORD_NOT_FOUND);
        }
    }

    // ---- FR-059 ----

    @Nested
    @DisplayName("FR-059: ліміти частоти дій")
    class Limits {

        @Test
        @DisplayName("одне оголошення на добу; через добу — знову можна")
        void oneAdvertPerDay() {
            advert(author);

            assertCode(() -> advert(author), ErrorCode.TOO_MANY_REQUESTS);

            clock.advance(Duration.ofDays(1).plusSeconds(1));
            assertThat(advert(author)).isNotNull();
        }

        @Test
        @DisplayName("видалене оголошення теж рахується: ліміт не обходиться створенням і видаленням")
        void deletedStillCounts() {
            Advert advert = advert(author);
            board.delete(author, advert.id());

            assertCode(() -> advert(author), ErrorCode.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("три відгуки на годину")
        void threeRepliesPerHour() {
            List<Advert> board4 = List.of(advert(author), advert(bob), advert(carol), advert(admin));
            for (int i = 0; i < REPLIES_PER_HOUR; i++) {
                board.reply(alice, board4.get(i).id(), null);
            }

            assertCode(() -> board.reply(alice, board4.get(REPLIES_PER_HOUR).id(), null),
                    ErrorCode.TOO_MANY_REQUESTS);

            clock.advance(Duration.ofHours(1).plusSeconds(1));
            assertThat(board.reply(alice, board4.get(REPLIES_PER_HOUR).id(), null).status())
                    .isEqualTo(ReplyStatus.PENDING);
        }
    }

    // ---- FR-060 ----

    @Nested
    @DisplayName("FR-060: автоматичне тимчасове блокування")
    class AutoBan {

        @Test
        @DisplayName("три скарги від різних користувачів блокують автора на налаштований строк")
        void threeComplainants() {
            Advert advert = advert(author);
            complain(alice, advert);
            complain(bob, advert);
            assertThat(reload(author).status()).isEqualTo(UserStatus.ACTIVE);

            complain(carol, advert);

            assertThat(reload(author).status()).isEqualTo(UserStatus.BLOCKED);
            Ban ban = moderation.listBans(admin, author.id()).get(0);
            assertThat(ban.isAutomatic()).isTrue();
            assertThat(ban.expiresAt()).isEqualTo(now().plus(AUTO_BAN_DURATION));
            assertThat(board.listOpen(alice, null, 20, 0).data()).isEmpty();
        }

        @Test
        @DisplayName("один скаржник на різний контент автора рахується один раз")
        void sameComplainantCountsOnce() {
            Advert advert = advert(author);
            Reply first = board.reply(author, advert(bob).id(), "Перший");
            Reply second = board.reply(author, advert(carol).id(), "Другий");

            complain(alice, advert);
            moderation.complain(alice, ContentType.REPLY, first.id(), ComplaintCategory.SPAM, null);
            moderation.complain(alice, ContentType.REPLY, second.id(), ComplaintCategory.SPAM, null);

            assertThat(reload(author).status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("відхилені скарги в ліміт не йдуть")
        void dismissedDoNotCount() {
            Advert advert = advert(author);
            Complaint unfounded = complain(alice, advert);
            moderation.dismiss(admin, unfounded.id(), null);
            complain(bob, advert);

            complain(carol, advert);

            assertThat(reload(author).status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("три відмови автофільтра за добу блокують, а сам запит усе одно відхиляється")
        void threeRejections() {
            moderation.addBannedWord(admin, "казино");
            Advert advert = advert(author);
            for (int i = 0; i < AUTO_BAN_REJECTIONS - 1; i++) {
                assertCode(() -> board.reply(alice, advert.id(), "казино"), ErrorCode.CONTENT_REJECTED);
            }
            assertThat(reload(alice).status()).isEqualTo(UserStatus.ACTIVE);

            assertCode(() -> board.reply(alice, advert.id(), "казино"), ErrorCode.CONTENT_REJECTED);

            assertThat(reload(alice).status()).isEqualTo(UserStatus.BLOCKED);
            assertThat(moderation.listBans(admin, alice.id()).get(0).reason()).contains("автофільтр");
        }

        @Test
        @DisplayName("відмови поза вікном не рахуються")
        void rejectionsOutsideWindow() {
            moderation.addBannedWord(admin, "казино");
            Advert advert = advert(author);
            assertCode(() -> board.reply(alice, advert.id(), "казино"), ErrorCode.CONTENT_REJECTED);
            assertCode(() -> board.reply(alice, advert.id(), "казино"), ErrorCode.CONTENT_REJECTED);
            clock.advance(Duration.ofHours(25));

            assertCode(() -> board.reply(alice, advert.id(), "казино"), ErrorCode.CONTENT_REJECTED);

            assertThat(reload(alice).status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Global Admin автоматично не блокується: змова трьох не лишить платформу без модератора")
        void adminIsNeverAutoBanned() {
            Advert advert = advert(admin);
            complain(alice, advert);
            complain(bob, advert);
            complain(carol, advert);

            assertThat(reload(admin).status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(moderation.listBans(admin, admin.id())).isEmpty();
        }

        @Test
        @DisplayName("автобан спадає сам: через строк планувальник повертає користувача")
        void autoBanExpires() {
            Advert advert = advert(author);
            complain(alice, advert);
            complain(bob, advert);
            complain(carol, advert);
            clock.advance(AUTO_BAN_DURATION.plusMinutes(1));

            moderation.liftExpiredBans();

            assertThat(reload(author).status()).isEqualTo(UserStatus.ACTIVE);
        }
    }
}
