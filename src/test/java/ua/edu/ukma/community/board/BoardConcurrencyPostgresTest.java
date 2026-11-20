package ua.edu.ukma.community.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ua.edu.ukma.community.auth.JdbcUserRepository;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.moderation.ModerationGate;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-034 / NFR-006 на рівні бази: кілька екземплярів {@link BoardService} — як кілька подів у Minikube —
 * одночасно підтверджують відгуки одного оголошення. {@code synchronized} тут нічого не дає (у кожного
 * екземпляра свій монітор), тож місця рятує лише {@code SELECT ... FOR UPDATE} у PostgreSQL.
 */
@DisplayName("BoardService на PostgreSQL: паралельні підтвердження з кількох подів")
@Tag("integration")
class BoardConcurrencyPostgresTest {

    private static final int PODS = 4;
    private static final int SLOTS = 3;
    private static final int REPLIES = 24;

    @Test
    @DisplayName("TC-034: 24 одночасні підтвердження з 4 подів на 3 місця — рівно 3 підтверджені, оголошення закрите")
    void confirmationsNeverExceedSlotsAcrossPods() throws InterruptedException {
        DataSource dataSource = TestDatabase.dataSource();
        TestDatabase.truncateAll();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        MutableClock clock = MutableClock.startingAtFixtureNow();
        JdbcUserRepository users = new JdbcUserRepository(jdbc, clock);
        JdbcAdvertRepository adverts = new JdbcAdvertRepository(jdbc, clock);
        JdbcReplyRepository replies = new JdbcReplyRepository(jdbc, clock);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        List<BoardService> pods = IntStream.range(0, PODS)
                .mapToObj(i -> new BoardService(adverts, replies, ModerationGate.permissive(), tx, clock))
                .toList();

        OffsetDateTime now = OffsetDateTime.now(clock);
        User author = users.save(new User(UUID.randomUUID(), "author@ukma.edu.ua", "hash", UserStatus.ACTIVE, false,
                null, now, now));
        Advert advert = pods.get(0).create(author, new AdvertCreateRequest(AdvertType.TEAM, "Хакатон", "Шукаю команду",
                SLOTS, now.plusDays(1), "@team_seeker")).advert();
        List<Reply> pending = new ArrayList<>();
        for (int i = 0; i < REPLIES; i++) {
            User user = users.save(new User(UUID.randomUUID(), "u" + i + "@ukma.edu.ua", "hash", UserStatus.ACTIVE,
                    false, null, now, now));
            pending.add(pods.get(i % PODS).reply(user, advert.id(), null));
        }

        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(REPLIES);
        for (int i = 0; i < REPLIES; i++) {
            BoardService pod = pods.get(i % PODS);
            Reply reply = pending.get(i);
            pool.submit(() -> {
                start.await();
                try {
                    pod.confirm(author, advert.id(), reply.id());
                    confirmed.incrementAndGet();
                } catch (ApiException e) {
                    if (e.code() == ErrorCode.ADVERT_CLOSED) {
                        closed.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(confirmed.get()).isEqualTo(SLOTS);
        assertThat(closed.get()).isEqualTo(REPLIES - SLOTS);
        assertThat(replies.countConfirmed(advert.id())).isEqualTo(SLOTS);
        assertThat(adverts.findById(advert.id()).orElseThrow().status()).isEqualTo(AdvertStatus.CLOSED);
    }
}
