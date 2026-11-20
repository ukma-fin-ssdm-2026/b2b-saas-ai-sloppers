package ua.edu.ukma.community.board;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.moderation.ModerationGate;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.support.MutableClock;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Правила дошки на справжніх in-memory репозиторіях: так перевіряється поведінка, а не виклики моків.
 * SQL JDBC-репозиторіїв тут не покритий (див. «червону зону» в testing-strategy.md).
 */
@DisplayName("BoardService")
class BoardServiceTest {

    private static final String CONTACT = "@team_seeker";

    private MutableClock clock;
    private InMemoryAdvertRepository adverts;
    private InMemoryReplyRepository replies;
    private BoardService board;

    private User author;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        clock = MutableClock.startingAtFixtureNow();
        adverts = new InMemoryAdvertRepository();
        replies = new InMemoryReplyRepository();
        board = new BoardService(adverts, replies, ModerationGate.permissive(),
                TransactionOperations.withoutTransaction(), clock);
        author = user("author");
        alice = user("alice");
        bob = user("bob");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private User user(String name) {
        OffsetDateTime at = now();
        return new User(UUID.randomUUID(), name + "@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null, at, at);
    }

    private static AdvertCreateRequest request(AdvertType type, int slots, OffsetDateTime deadline) {
        return new AdvertCreateRequest(type, "  Шукаю бекендера  ", "Хакатон на вихідних", slots, deadline,
                " " + CONTACT + " ");
    }

    private Advert createAdvert(int slots) {
        return board.create(author, request(AdvertType.TEAM, slots, now().plusDays(2))).advert();
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Nested
    @DisplayName("створення (FR-043, FR-044)")
    class Create {

        @Test
        @DisplayName("TC-028: оголошення ACTIVE, поля обрізані, автор бачить свій контакт")
        void createsActiveAdvert() {
            AdvertView view = board.create(author, request(AdvertType.TEAM, 3, now().plusDays(2)));

            assertThat(view.advert().status()).isEqualTo(AdvertStatus.ACTIVE);
            assertThat(view.advert().title()).isEqualTo("Шукаю бекендера");
            assertThat(view.advert().contact()).isEqualTo(CONTACT);
            assertThat(view.own()).isTrue();
            assertThat(view.visibleContact()).isEqualTo(CONTACT);
            assertThat(adverts.findById(view.advert().id())).isPresent();
        }

        @Test
        @DisplayName("TC-029: дедлайн, що вже минув за годинником сервісу, → VALIDATION_ERROR")
        void rejectsPastDeadline() {
            assertFails(() -> board.create(author, request(AdvertType.TEAM, 1, now().minusMinutes(1))),
                    ErrorCode.VALIDATION_ERROR);
            assertFails(() -> board.create(author, request(AdvertType.TEAM, 1, now())), ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("список і контакт (FR-045, FR-049, FR-052)")
    class Listing {

        @Test
        @DisplayName("TC-030: у списку контакт бачить лише автор")
        void contactHiddenInListForOthers() {
            createAdvert(2);

            assertThat(board.listOpen(alice, null, 20, 0).data())
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.own()).isFalse();
                        assertThat(v.visibleContact()).isNull();
                    });
            assertThat(board.listOpen(author, null, 20, 0).data())
                    .singleElement()
                    .satisfies(v -> assertThat(v.visibleContact()).isEqualTo(CONTACT));
        }

        @Test
        @DisplayName("лише відкриті: без закритих, видалених і прострочених; фільтр за типом; найновіші спершу")
        void listsOnlyOpen() {
            Advert older = createAdvert(1);
            clock.advance(Duration.ofMinutes(1));
            Advert newer = createAdvert(1);
            clock.advance(Duration.ofMinutes(1));
            Advert leisure = board.create(author, request(AdvertType.LEISURE, 1, now().plusDays(1))).advert();
            Advert closed = createAdvert(1);
            board.close(author, closed.id());
            Advert deleted = createAdvert(1);
            board.delete(author, deleted.id());
            Advert expiring = board.create(author, request(AdvertType.TEAM, 1, now().plusMinutes(30))).advert();
            clock.advance(Duration.ofHours(1));

            OffsetPage<AdvertView> all = board.listOpen(alice, null, 20, 0);
            assertThat(all.data()).extracting(v -> v.advert().id())
                    .containsExactly(leisure.id(), newer.id(), older.id())
                    .doesNotContain(closed.id(), deleted.id(), expiring.id());
            assertThat(board.listOpen(alice, AdvertType.LEISURE, 20, 0).data())
                    .extracting(v -> v.advert().id()).containsExactly(leisure.id());
            assertThat(board.listOpen(alice, null, 2, 1).pagination().hasMore()).isFalse();
        }

        @Test
        @DisplayName("прострочене, але ще ACTIVE у сховищі, показується як CLOSED")
        void expiredShownAsClosed() {
            Advert advert = board.create(author, request(AdvertType.TEAM, 1, now().plusMinutes(10))).advert();
            clock.advance(Duration.ofMinutes(11));

            assertThat(board.get(author, advert.id()).status()).isEqualTo(AdvertStatus.CLOSED);
        }

        @Test
        @DisplayName("«мої оголошення» — без видалених, закриті лишаються")
        void listMine() {
            Advert active = createAdvert(1);
            Advert closed = createAdvert(1);
            board.close(author, closed.id());
            board.delete(author, createAdvert(1).id());

            assertThat(board.listMine(author, 20, 0).data()).extracting(v -> v.advert().id())
                    .containsExactlyInAnyOrder(active.id(), closed.id());
            assertThat(board.listMine(alice, 20, 0).data()).isEmpty();
        }
    }

    @Nested
    @DisplayName("відгуки (FR-046 – FR-049, FR-053)")
    class Replies {

        @Test
        @DisplayName("TC-031: на власне оголошення — OWN_ADVERT, повторно — ALREADY_REPLIED")
        void oneReplyOnSomeoneElsesAdvert() {
            Advert advert = createAdvert(2);

            assertFails(() -> board.reply(author, advert.id(), "сам собі"), ErrorCode.OWN_ADVERT);
            Reply reply = board.reply(alice, advert.id(), "  Привіт  ");
            assertThat(reply.status()).isEqualTo(ReplyStatus.PENDING);
            assertThat(reply.message()).isEqualTo("Привіт");
            assertFails(() -> board.reply(alice, advert.id(), "ще раз"), ErrorCode.ALREADY_REPLIED);
        }

        @Test
        @DisplayName("на неіснуюче чи видалене — ADVERT_NOT_FOUND, на закрите чи прострочене — ADVERT_CLOSED")
        void replyToMissingOrClosed() {
            assertFails(() -> board.reply(alice, UUID.randomUUID(), null), ErrorCode.ADVERT_NOT_FOUND);

            Advert deleted = createAdvert(1);
            board.delete(author, deleted.id());
            assertFails(() -> board.reply(alice, deleted.id(), null), ErrorCode.ADVERT_NOT_FOUND);

            Advert closed = createAdvert(1);
            board.close(author, closed.id());
            assertFails(() -> board.reply(alice, closed.id(), null), ErrorCode.ADVERT_CLOSED);

            Advert expiring = board.create(author, request(AdvertType.TEAM, 1, now().plusMinutes(5))).advert();
            clock.advance(Duration.ofMinutes(5));
            assertFails(() -> board.reply(alice, expiring.id(), null), ErrorCode.ADVERT_CLOSED);
        }

        @Test
        @DisplayName("TC-032: відгуки бачить лише автор; відкликані не показуються")
        void onlyAuthorSeesReplies() {
            Advert advert = createAdvert(3);
            Reply fromAlice = board.reply(alice, advert.id(), "a");
            Reply fromBob = board.reply(bob, advert.id(), "b");
            board.withdraw(bob, advert.id(), fromBob.id());

            assertThat(board.listReplies(author, advert.id())).extracting(Reply::id).containsExactly(fromAlice.id());
            assertFails(() -> board.listReplies(alice, advert.id()), ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("TC-033: контакт відкривається лише після підтвердження і лише цьому відгукувачу")
        void contactOnlyAfterConfirmation() {
            Advert advert = createAdvert(3);
            Reply fromAlice = board.reply(alice, advert.id(), null);
            Reply fromBob = board.reply(bob, advert.id(), null);
            User stranger = user("stranger");

            assertThat(board.get(alice, advert.id()).visibleContact()).isNull();

            board.confirm(author, advert.id(), fromAlice.id());
            board.reject(author, advert.id(), fromBob.id());

            AdvertView forAlice = board.get(alice, advert.id());
            assertThat(forAlice.myReplyStatus()).isEqualTo(ReplyStatus.CONFIRMED);
            assertThat(forAlice.myReplyId()).isEqualTo(fromAlice.id());
            assertThat(forAlice.visibleContact()).isEqualTo(CONTACT);
            assertThat(board.get(bob, advert.id()).myReplyId()).isEqualTo(fromBob.id());
            assertThat(board.get(stranger, advert.id()).myReplyId()).isNull();
            assertThat(board.get(bob, advert.id()).visibleContact()).isNull();
            assertThat(board.get(stranger, advert.id()).visibleContact()).isNull();
            assertThat(board.listOpen(alice, null, 20, 0).data())
                    .singleElement().satisfies(v -> assertThat(v.visibleContact()).isEqualTo(CONTACT));
            assertThat(board.listOpen(bob, null, 20, 0).data())
                    .singleElement().satisfies(v -> assertThat(v.visibleContact()).isNull());
        }

        @Test
        @DisplayName("підтверджує й відхиляє лише автор; лише PENDING; чужий відгук — REPLY_NOT_FOUND")
        void decisionsByAuthorOnly() {
            Advert advert = createAdvert(2);
            Advert other = createAdvert(2);
            Reply reply = board.reply(alice, advert.id(), null);

            assertFails(() -> board.confirm(bob, advert.id(), reply.id()), ErrorCode.FORBIDDEN);
            assertFails(() -> board.confirm(author, other.id(), reply.id()), ErrorCode.REPLY_NOT_FOUND);
            assertFails(() -> board.reject(author, advert.id(), UUID.randomUUID()), ErrorCode.REPLY_NOT_FOUND);

            board.reject(author, advert.id(), reply.id());
            assertFails(() -> board.confirm(author, advert.id(), reply.id()), ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("FR-053: відкликати можна лише свій відгук і лише до рішення; після — подати знову")
        void withdrawAndResubmit() {
            Advert advert = createAdvert(2);
            Reply reply = board.reply(alice, advert.id(), "перше");

            assertFails(() -> board.withdraw(bob, advert.id(), reply.id()), ErrorCode.FORBIDDEN);
            assertThat(board.withdraw(alice, advert.id(), reply.id()).status()).isEqualTo(ReplyStatus.WITHDRAWN);
            assertFails(() -> board.withdraw(alice, advert.id(), reply.id()), ErrorCode.INVALID_STATE_TRANSITION);

            Reply again = board.reply(alice, advert.id(), "друге");
            assertThat(again.id()).isEqualTo(reply.id());
            assertThat(again.status()).isEqualTo(ReplyStatus.PENDING);
            assertThat(again.message()).isEqualTo("друге");

            board.confirm(author, advert.id(), again.id());
            assertFails(() -> board.withdraw(alice, advert.id(), again.id()), ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("відхилений відгук повторно не подається")
        void rejectedCannotReplyAgain() {
            Advert advert = createAdvert(2);
            Reply reply = board.reply(alice, advert.id(), null);
            board.reject(author, advert.id(), reply.id());

            assertFails(() -> board.reply(alice, advert.id(), null), ErrorCode.ALREADY_REPLIED);
        }
    }

    @Nested
    @DisplayName("закриття (FR-050 – FR-052)")
    class Closing {

        @Test
        @DisplayName("TC-034: щойно підтверджених стільки, скільки місць, оголошення закривається")
        void closesWhenSlotsFilled() {
            Advert advert = createAdvert(2);
            Reply a = board.reply(alice, advert.id(), null);
            Reply b = board.reply(bob, advert.id(), null);
            Reply c = board.reply(user("carol"), advert.id(), null);

            board.confirm(author, advert.id(), a.id());
            assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.ACTIVE);
            board.confirm(author, advert.id(), b.id());

            assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.CLOSED);
            assertThat(board.listOpen(alice, null, 20, 0).data()).isEmpty();
            assertFails(() -> board.confirm(author, advert.id(), c.id()), ErrorCode.ADVERT_CLOSED);
            assertFails(() -> board.reply(user("dave"), advert.id(), null), ErrorCode.ADVERT_CLOSED);
        }

        @Test
        @DisplayName("TC-034 / NFR-006: 100 паралельних підтверджень на 5 місць — рівно 5 підтверджених")
        void concurrentConfirmationsNeverExceedSlots() throws InterruptedException {
            // запис із затримкою, як мережевий round-trip до бази: без нього вікно гонки — мікросекунди,
            // і тест не помітив би, якби підтвердження перестали виконуватися по черзі
            replies = new InMemoryReplyRepository() {
                @Override
                public Reply save(Reply reply) {
                    pause();
                    return super.save(reply);
                }
            };
            board = new BoardService(adverts, replies, ModerationGate.permissive(),
                TransactionOperations.withoutTransaction(), clock);
            Advert advert = createAdvert(5);
            List<Reply> pending = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                pending.add(board.reply(user("u" + i), advert.id(), null));
            }
            AtomicInteger confirmed = new AtomicInteger();
            AtomicInteger closedErrors = new AtomicInteger();
            // усі потоки чекають на спільному старті, щоб підтвердження справді зіткнулися
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(pending.size());
            for (Reply reply : pending) {
                pool.submit(() -> {
                    start.await();
                    try {
                        board.confirm(author, advert.id(), reply.id());
                        confirmed.incrementAndGet();
                    } catch (ApiException e) {
                        if (e.code() == ErrorCode.ADVERT_CLOSED) {
                            closedErrors.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            assertThat(confirmed.get()).isEqualTo(5);
            assertThat(closedErrors.get()).isEqualTo(95);
            assertThat(replies.countConfirmed(advert.id())).isEqualTo(5);
            assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.CLOSED);
        }

        private static void pause() {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Test
        @DisplayName("TC-035: після дедлайну планувальник переводить оголошення в CLOSED")
        void closeExpiredAfterDeadline() {
            Advert soon = board.create(author, request(AdvertType.TEAM, 1, now().plusMinutes(30))).advert();
            Advert later = createAdvert(1);

            assertThat(board.closeExpired()).isZero();
            clock.advance(Duration.ofMinutes(30));
            assertThat(board.closeExpired()).isEqualTo(1);
            assertThat(board.closeExpired()).isZero();

            assertThat(adverts.findById(soon.id()).orElseThrow().status()).isEqualTo(AdvertStatus.CLOSED);
            assertThat(adverts.findById(later.id()).orElseThrow().status()).isEqualTo(AdvertStatus.ACTIVE);
        }

        @Test
        @DisplayName("TC-036: після закриття підтверджені зберігають контакт, у тому числі в «моїх відгуках»")
        void confirmedKeepContactAfterClose() {
            Advert advert = createAdvert(3);
            Reply reply = board.reply(alice, advert.id(), null);
            board.confirm(author, advert.id(), reply.id());
            board.reply(bob, advert.id(), null);
            board.close(author, advert.id());

            assertThat(board.get(alice, advert.id()).visibleContact()).isEqualTo(CONTACT);
            assertThat(board.listReplied(alice, 20, 0).data()).singleElement()
                    .satisfies(v -> {
                        assertThat(v.status()).isEqualTo(AdvertStatus.CLOSED);
                        assertThat(v.visibleContact()).isEqualTo(CONTACT);
                    });
            assertThat(board.listReplied(bob, 20, 0).data()).singleElement()
                    .satisfies(v -> assertThat(v.visibleContact()).isNull());
        }

        @Test
        @DisplayName("FR-051: закрити й видалити може лише автор; видалене зникає для всіх")
        void closeAndDeleteByAuthorOnly() {
            Advert advert = createAdvert(2);
            Reply reply = board.reply(alice, advert.id(), null);
            board.confirm(author, advert.id(), reply.id());

            assertFails(() -> board.close(alice, advert.id()), ErrorCode.FORBIDDEN);
            assertFails(() -> board.delete(alice, advert.id()), ErrorCode.FORBIDDEN);

            assertThat(board.close(author, advert.id()).status()).isEqualTo(AdvertStatus.CLOSED);
            assertFails(() -> board.close(author, advert.id()), ErrorCode.INVALID_STATE_TRANSITION);

            board.delete(author, advert.id());
            assertFails(() -> board.get(alice, advert.id()), ErrorCode.ADVERT_NOT_FOUND);
            assertFails(() -> board.get(author, advert.id()), ErrorCode.ADVERT_NOT_FOUND);
            assertFails(() -> board.delete(author, advert.id()), ErrorCode.ADVERT_NOT_FOUND);
            assertThat(board.listReplied(alice, 20, 0).data()).isEmpty();
            assertThat(board.listMine(author, 20, 0).data()).isEmpty();
        }
    }
}
