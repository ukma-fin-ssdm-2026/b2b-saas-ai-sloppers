package ua.edu.ukma.community.board;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;
import ua.edu.ukma.community.moderation.ModerationGate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Правила дошки оголошень (FR-043–FR-053).
 *
 * <p>Усе, що змінює оголошення або його відгуки, виконується в {@link #locked}: транзакція плюс
 * {@code SELECT ... FOR UPDATE} на рядок оголошення. Так «підтвердити відгук» і «закрити, бо місця скінчились»
 * — одна атомарна дія, і паралельні підтвердження не перевищать кількість місць (FR-050, NFR-006).
 * {@code synchronized} дає ту саму гарантію в профілі {@code inmemory}, де бази й транзакцій немає.
 *
 * <p>Правила модерації (FR-055, FR-058, FR-059) живуть за {@link ModerationGate}: дошка лише питає
 * дозволу перед записом і список прихованих авторів перед показом. Питати треба до відкриття
 * транзакції: відмову автофільтра шлюз зберігає, і відкат забрав би її з собою.
 */
@Service
public class BoardService {

    private static final Comparator<Advert> NEWEST_FIRST =
            Comparator.comparing(Advert::createdAt).reversed().thenComparing(Advert::id);
    private static final Comparator<Reply> OLDEST_FIRST =
            Comparator.comparing(Reply::createdAt).thenComparing(Reply::id);

    private final AdvertRepository adverts;
    private final ReplyRepository replies;
    private final ModerationGate moderation;
    private final TransactionOperations transactions;
    private final Clock clock;

    public BoardService(AdvertRepository adverts, ReplyRepository replies, ModerationGate moderation,
                        TransactionOperations transactions, Clock clock) {
        this.adverts = adverts;
        this.replies = replies;
        this.moderation = moderation;
        this.transactions = transactions;
        this.clock = clock;
    }

    // ---- оголошення ----

    /** FR-043, FR-044. Поля вже перевірені Bean Validation; дедлайн звіряємо ще й з годинником сервісу. */
    public AdvertView create(User author, AdvertCreateRequest request) {
        OffsetDateTime now = now();
        if (!request.deadline().isAfter(now)) {
            throw ApiException.validation("deadline", "Дедлайн має бути в майбутньому", request.deadline());
        }
        moderation.beforeAdvert(author, request.title(), request.description());
        Advert advert = adverts.save(new Advert(UUID.randomUUID(), author.id(), request.type(),
                request.title().trim(), request.description().trim(), request.slots(), request.deadline(),
                request.contact().trim(), AdvertStatus.ACTIVE, now, now));
        return AdvertView.of(advert, author.id(), null, null, now);
    }

    /** FR-045, FR-052: лише відкриті оголошення, найновіші спершу; без оголошень заблокованих (FR-055). */
    public OffsetPage<AdvertView> listOpen(User viewer, AdvertType type, int limit, int offset) {
        OffsetDateTime now = now();
        Set<UUID> hidden = moderation.hiddenAuthors();
        List<Advert> open = adverts.findOpen(type, now).stream()
                .filter(a -> !hidden.contains(a.authorId()))
                .sorted(NEWEST_FIRST)
                .toList();
        return toViews(viewer, OffsetPage.of(open, limit, offset), now);
    }

    /** Оголошення автора в будь-якому статусі, крім видалених. */
    public OffsetPage<AdvertView> listMine(User author, int limit, int offset) {
        OffsetDateTime now = now();
        List<Advert> mine = adverts.findByAuthor(author.id()).stream().sorted(NEWEST_FIRST).toList();
        return toViews(author, OffsetPage.of(mine, limit, offset), now);
    }

    /**
     * Оголошення, на які користувач відгукнувся (крім відкликаних відгуків). Тут підтверджений учасник
     * знаходить контакт автора, навіть коли оголошення вже закрите (US-013).
     */
    public OffsetPage<AdvertView> listReplied(User user, int limit, int offset) {
        OffsetDateTime now = now();
        List<UUID> advertIds = replies.findByUser(user.id()).stream()
                .filter(r -> r.status() != ReplyStatus.WITHDRAWN && r.status() != ReplyStatus.REMOVED)
                .map(Reply::advertId)
                .toList();
        List<Advert> replied = adverts.findAllById(advertIds).stream()
                .filter(a -> !a.isDeleted())
                .sorted(NEWEST_FIRST)
                .toList();
        return toViews(user, OffsetPage.of(replied, limit, offset), now);
    }

    /**
     * FR-055: оголошення заблокованого автора не показуємо й за прямим посиланням. Виняток — сам автор
     * і ті, хто вже відгукнувся: підтверджений учасник не має втратити контакт через бан автора (US-013).
     */
    public AdvertView get(User viewer, UUID advertId) {
        Advert advert = requireVisible(advertId);
        Reply myReply = replies.findByAdvertAndUser(advertId, viewer.id())
                .filter(r -> r.status() != ReplyStatus.REMOVED)
                .orElse(null);
        if (!advert.isAuthor(viewer.id()) && myReply == null && moderation.isHidden(advert.authorId())) {
            throw advertNotFound(advertId);
        }
        return AdvertView.of(
                advert,
                viewer.id(),
                myReply != null ? myReply.status() : null,
                myReply != null ? myReply.id() : null,
                now());
    }

    /** FR-051. Підтверджені відгукувачі зберігають доступ до контакту. */
    public synchronized AdvertView close(User actor, UUID advertId) {
        return locked(() -> {
            Advert advert = requireAuthor(actor, lockVisible(advertId));
            OffsetDateTime now = now();
            return AdvertView.of(adverts.save(advert.close(now)), actor.id(), null, null, now);
        });
    }

    /** FR-051: оголошення зникає для всіх, зокрема для підтверджених відгукувачів. */
    public synchronized void delete(User actor, UUID advertId) {
        locked(() -> {
            Advert advert = requireAuthor(actor, lockVisible(advertId));
            return adverts.save(advert.delete(now()));
        });
    }

    // ---- відгуки ----

    /**
     * FR-046, FR-052: один відгук на чуже відкрите оголошення. Відкликаний раніше відгук подається
     * знову тим самим рядком; відхилений — ні, щоб не засипати автора повторами.
     */
    public synchronized Reply reply(User user, UUID advertId, String message) {
        String text = message == null || message.isBlank() ? null : message.trim();
        // до транзакції: шлюз пише відмову автофільтра, яку відкат знищив би (FR-058, FR-059)
        moderation.beforeReply(user, text);
        return locked(() -> {
            Advert advert = lockVisible(advertId);
            if (advert.isAuthor(user.id())) {
                throw new ApiException(ErrorCode.OWN_ADVERT, "Не можна відгукнутися на власне оголошення");
            }
            OffsetDateTime now = now();
            requireOpen(advert, now);
            return replies.findByAdvertAndUser(advertId, user.id())
                    .map(existing -> {
                        if (existing.status() != ReplyStatus.WITHDRAWN) {
                            throw new ApiException(ErrorCode.ALREADY_REPLIED,
                                    "Ви вже відгукнулися на це оголошення");
                        }
                        return replies.save(existing.resubmit(text, now));
                    })
                    .orElseGet(() -> replies.save(Reply.create(advertId, user.id(), text, now)));
        });
    }

    /** FR-047: бачить лише автор; відкликані відгуки не показуються. */
    public List<Reply> listReplies(User actor, UUID advertId) {
        requireAuthor(actor, requireVisible(advertId));
        return replies.findByAdvert(advertId).stream()
                .filter(r -> r.status() != ReplyStatus.WITHDRAWN && r.status() != ReplyStatus.REMOVED)
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /**
     * FR-048, FR-050. Підтвердження й автозакриття — в одній транзакції під блокуванням оголошення:
     * лічильник підтверджених читається вже після того, як інші підтвердження цього оголошення завершились.
     */
    public synchronized Reply confirm(User actor, UUID advertId, UUID replyId) {
        return locked(() -> {
            Advert advert = requireAuthor(actor, lockVisible(advertId));
            OffsetDateTime now = now();
            requireOpen(advert, now);
            Reply confirmed = replies.save(requireReply(advertId, replyId).confirm(now));
            if (replies.countConfirmed(advertId) >= advert.slots()) {
                adverts.save(advert.close(now));
            }
            return confirmed;
        });
    }

    /** FR-048. */
    public synchronized Reply reject(User actor, UUID advertId, UUID replyId) {
        return locked(() -> {
            requireAuthor(actor, lockVisible(advertId));
            return replies.save(requireReply(advertId, replyId).reject(now()));
        });
    }

    /** FR-053: лише власний відгук і лише до рішення автора. */
    public synchronized Reply withdraw(User user, UUID advertId, UUID replyId) {
        return locked(() -> {
            lockVisible(advertId);
            Reply reply = requireReply(advertId, replyId);
            if (!reply.userId().equals(user.id())) {
                throw new ApiException(ErrorCode.FORBIDDEN, "Відкликати можна лише власний відгук");
            }
            return replies.save(reply.withdraw(now()));
        });
    }

    // ---- планувальник ----

    /** FR-050: закриває оголошення з дедлайном, що минув. Викликає {@link BoardScheduler}. */
    public int closeExpired() {
        return adverts.closeExpired(now());
    }

    // ---- допоміжне ----

    private OffsetPage<AdvertView> toViews(User viewer, OffsetPage<Advert> page, OffsetDateTime now) {
        Map<UUID, Reply> myReplies = replies.findByUser(viewer.id()).stream()
                .filter(r -> r.status() != ReplyStatus.REMOVED)
                .collect(Collectors.toMap(Reply::advertId, r -> r, (a, b) -> a));
        return page.map(a -> {
            Reply mine = myReplies.get(a.id());
            return AdvertView.of(
                    a,
                    viewer.id(),
                    mine != null ? mine.status() : null,
                    mine != null ? mine.id() : null,
                    now);
        });
    }

    private <T> T locked(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private Advert requireVisible(UUID advertId) {
        return adverts.findById(advertId).filter(a -> !a.isDeleted()).orElseThrow(() -> advertNotFound(advertId));
    }

    private Advert lockVisible(UUID advertId) {
        return adverts.lock(advertId).filter(a -> !a.isDeleted()).orElseThrow(() -> advertNotFound(advertId));
    }

    private static Advert requireAuthor(User actor, Advert advert) {
        if (!advert.isAuthor(actor.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Дія доступна лише автору оголошення");
        }
        return advert;
    }

    private static void requireOpen(Advert advert, OffsetDateTime now) {
        if (!advert.isOpen(now)) {
            throw new ApiException(ErrorCode.ADVERT_CLOSED, "Оголошення закрите: відгуки більше не приймаються");
        }
    }

    private Reply requireReply(UUID advertId, UUID replyId) {
        return replies.findById(replyId)
                .filter(r -> r.advertId().equals(advertId))
                .orElseThrow(() -> new ApiException(ErrorCode.REPLY_NOT_FOUND,
                        "Відгук " + replyId + " на це оголошення не знайдено"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static ApiException advertNotFound(UUID advertId) {
        return new ApiException(ErrorCode.ADVERT_NOT_FOUND, "Оголошення " + advertId + " не знайдено");
    }
}
