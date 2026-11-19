package ua.edu.ukma.community.moderation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserRepository;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.pagination.OffsetPage;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Модерація користувачів і контенту (FR-054–FR-060).
 *
 * <p>Блокування записується у два місця в одній транзакції: рядок у {@code ban} — це причина, строк
 * і журнал, а {@code app_user.status = BLOCKED} — прапорець, який уже читає {@code CurrentUser} на
 * кожному захищеному маршруті. Денормалізація свідома: інакше кожен запит до API додавав би запит до
 * {@code ban}. Єдиним джерелом правди лишається {@code ban}: планувальник
 * ({@link ModerationScheduler}) зводить статус до нього, коли строк бану виходить.
 *
 * <p>Методи {@code before*} з {@link ModerationGate} навмисно нетранзакційні: відмову автофільтра
 * треба зберегти (FR-060 рахує такі відмови), а сам запит при цьому — провалити. Викликати їх
 * усередині чужої транзакції не можна, бо відкат забрав би з собою й цей запис.
 */
@Service
public class ModerationService implements ModerationGate {

    private static final Logger LOG = LoggerFactory.getLogger(ModerationService.class);

    private final ModerationRepository moderation;
    private final ModeratedContent content;
    private final UserRepository users;
    private final OrgRolePolicy policy;
    private final WordFilter filter;
    private final ModerationProperties properties;
    private final TransactionOperations transactions;
    private final Clock clock;

    public ModerationService(ModerationRepository moderation, ModeratedContent content, UserRepository users,
                             OrgRolePolicy policy, WordFilter filter, ModerationProperties properties,
                             TransactionOperations transactions, Clock clock) {
        this.moderation = moderation;
        this.content = content;
        this.users = users;
        this.policy = policy;
        this.filter = filter;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    // ---- скарги (FR-056) ----

    /**
     * Скарга користувача на оголошення або відгук. Повторна скарга того самого користувача на той самий
     * об'єкт не приймається, інакше трьох скаржників (FR-060) можна було б зімітувати одному.
     */
    public Complaint complain(User reporter, ContentType targetType, UUID targetId, ComplaintCategory category,
                              String comment) {
        UUID authorId = content.authorOf(targetType, targetId)
                .orElseThrow(() -> contentNotFound(targetType, targetId));
        if (authorId.equals(reporter.id())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Не можна скаржитися на власний контент");
        }
        if (moderation.complaintExists(reporter.id(), targetType, targetId)) {
            throw new ApiException(ErrorCode.ALREADY_COMPLAINED, "Ви вже скаржилися на цей контент");
        }
        OffsetDateTime now = now();
        String text = comment == null || comment.isBlank() ? null : comment.trim();
        Complaint saved = transactions.execute(tx -> moderation.saveComplaint(
                Complaint.open(reporter.id(), authorId, targetType, targetId, category, text, now)));
        autoBanOnComplaints(authorId, now);
        return saved;
    }

    // ---- черга модератора (FR-057) ----

    public OffsetPage<Complaint> listComplaints(User admin, ComplaintStatus status, int limit, int offset) {
        policy.requireGlobalAdmin(admin);
        List<Complaint> page = moderation.findComplaints(status, limit, offset);
        long total = moderation.countComplaints(status);
        return new OffsetPage<>(page,
                new OffsetPage.OffsetPagination(limit, offset, total, offset + page.size() < total));
    }

    /**
     * Скаргу підтверджено: контент прибирається (FR-057). Інші відкриті скарги на цей самий об'єкт
     * закриваються тим самим рішенням — розглядати їх уже нема чого.
     */
    public Complaint resolve(User admin, UUID complaintId, String resolution) {
        policy.requireGlobalAdmin(admin);
        return transactions.execute(tx -> {
            Complaint complaint = requireComplaint(complaintId);
            OffsetDateTime now = now();
            Complaint resolved = moderation.saveComplaint(
                    complaint.resolve(ComplaintStatus.RESOLVED, admin.id(), resolution, now));
            content.remove(complaint.targetType(), complaint.targetId());
            moderation.resolveComplaintsOn(complaint.targetType(), complaint.targetId(),
                    ComplaintStatus.RESOLVED, admin.id(), resolution, now);
            return resolved;
        });
    }

    /** Скаргу відхилено: контент лишається, скарга закривається (FR-057). */
    public Complaint dismiss(User admin, UUID complaintId, String resolution) {
        policy.requireGlobalAdmin(admin);
        return transactions.execute(tx -> {
            Complaint complaint = requireComplaint(complaintId);
            return moderation.saveComplaint(
                    complaint.resolve(ComplaintStatus.DISMISSED, admin.id(), resolution, now()));
        });
    }

    // ---- блокування (FR-054) ----

    /**
     * Блокування користувача Global Admin.
     *
     * @param expiresAt коли бан спадає сам, або {@code null} — безстроково
     */
    public Ban ban(User admin, UUID userId, String reason, OffsetDateTime expiresAt) {
        policy.requireGlobalAdmin(admin);
        if (admin.id().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Не можна заблокувати власний акаунт");
        }
        OffsetDateTime now = now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw ApiException.validation("expiresAt", "Строк блокування має бути в майбутньому", expiresAt);
        }
        return transactions.execute(tx -> block(userId, admin.id(), reason.trim(), expiresAt, now));
    }

    /** Знімає блокування достроково (FR-054). */
    public Ban unban(User admin, UUID userId) {
        policy.requireGlobalAdmin(admin);
        return transactions.execute(tx -> moderation.findActiveBan(userId)
                .map(ban -> lift(ban, admin.id(), now()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_BANNED, "Користувач не заблокований")));
    }

    /** Історія блокувань користувача (FR-054): журнал, за яким видно, хто й за що блокував. */
    public List<Ban> listBans(User admin, UUID userId) {
        policy.requireGlobalAdmin(admin);
        return moderation.findBans(userId);
    }

    /**
     * Знімає бани, строк яких вийшов (FR-054). Викликає {@link ModerationScheduler}.
     *
     * <p>Кілька подів можуть зняти той самий бан: другий запис лише перезапише ті самі значення, тому
     * розподілене блокування не потрібне.
     */
    public int liftExpiredBans() {
        OffsetDateTime now = now();
        List<Ban> expired = moderation.findExpiredBans(now);
        expired.forEach(ban -> transactions.execute(tx -> lift(ban, null, now)));
        return expired.size();
    }

    // ---- заборонені слова (FR-058) ----

    public List<BannedWord> listBannedWords(User admin) {
        policy.requireGlobalAdmin(admin);
        return moderation.findBannedWords();
    }

    public BannedWord addBannedWord(User admin, String word) {
        policy.requireGlobalAdmin(admin);
        String normalized = BannedWord.normalize(word);
        if (normalized.length() < 2) {
            throw ApiException.validation("word", "Слово має містити щонайменше 2 символи", word);
        }
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw ApiException.validation("word", "Фільтр порівнює окремі слова, тому пробіли не допускаються",
                    word);
        }
        if (moderation.findBannedWords().stream().anyMatch(w -> w.word().equals(normalized))) {
            throw new ApiException(ErrorCode.BANNED_WORD_EXISTS, "Слово «" + normalized + "» вже у списку");
        }
        return moderation.saveBannedWord(BannedWord.of(normalized, admin.id(), now()));
    }

    public void removeBannedWord(User admin, UUID wordId) {
        policy.requireGlobalAdmin(admin);
        if (!moderation.deleteBannedWord(wordId)) {
            throw new ApiException(ErrorCode.BANNED_WORD_NOT_FOUND, "Слова " + wordId + " у списку немає");
        }
    }

    // ---- те, що викликає дошка (FR-055, FR-058, FR-059) ----

    @Override
    public void beforeAdvert(User author, String title, String description) {
        OffsetDateTime now = now();
        ModerationProperties.Limits limits = properties.limits();
        requireUnderLimit(content.countAdvertsSince(author.id(), now.minus(limits.advertWindow())),
                limits.adverts(), limits.advertWindow(), "оголошення");
        requireClean(author, ContentType.ADVERT, now, title, description);
    }

    @Override
    public void beforeReply(User user, String message) {
        OffsetDateTime now = now();
        ModerationProperties.Limits limits = properties.limits();
        requireUnderLimit(content.countRepliesSince(user.id(), now.minus(limits.replyWindow())),
                limits.replies(), limits.replyWindow(), "відгуки");
        requireClean(user, ContentType.REPLY, now, message);
    }

    @Override
    public Set<UUID> hiddenAuthors() {
        return moderation.findBannedUsers(now());
    }

    @Override
    public boolean isHidden(UUID authorId) {
        return moderation.findActiveBan(authorId).filter(ban -> ban.isActive(now())).isPresent();
    }

    // ---- допоміжне ----

    private static void requireUnderLimit(long used, int allowed, Duration window, String what) {
        if (allowed > 0 && used >= allowed) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Ліміт: " + allowed + " " + what + " за " + humanize(window) + ". Спробуйте пізніше");
        }
    }

    /**
     * Автофільтр (FR-058). Відмова зберігається навіть при помилці запиту: за цими рядками спрацьовує
     * автоматичне блокування (FR-060).
     */
    private void requireClean(User user, ContentType type, OffsetDateTime now, String... texts) {
        Optional<String> found = filter.firstBanned(texts);
        if (found.isEmpty()) {
            return;
        }
        String word = found.get();
        moderation.saveRejection(FilterRejection.of(user.id(), type, word, now));
        autoBanOnRejections(user.id(), now);
        throw new ApiException(ErrorCode.CONTENT_REJECTED,
                "Текст містить заборонене слово «" + word + "»: відредагуйте й спробуйте ще раз");
    }

    /** FR-060: скарги від кількох різних користувачів на контент однієї людини. */
    private void autoBanOnComplaints(UUID authorId, OffsetDateTime now) {
        int threshold = properties.autoBan().complaints();
        if (threshold <= 0) {
            return;
        }
        long complainants = moderation.countComplainantsAgainst(authorId);
        if (complainants >= threshold) {
            autoBan(authorId, "скарги від " + complainants + " різних користувачів", now);
        }
    }

    /** FR-060: кілька відмов автофільтра за вікно. */
    private void autoBanOnRejections(UUID userId, OffsetDateTime now) {
        ModerationProperties.AutoBan autoBan = properties.autoBan();
        if (autoBan.filterRejections() <= 0) {
            return;
        }
        long rejections = moderation.countRejectionsSince(userId, now.minus(autoBan.rejectionWindow()));
        if (rejections >= autoBan.filterRejections()) {
            autoBan(userId, "автофільтр відхилив " + rejections + " повідомлень за "
                    + humanize(autoBan.rejectionWindow()), now);
        }
    }

    /**
     * Тимчасове блокування системою (FR-060). Global Admin автоматично не блокується: інакше три
     * змовлені скарги могли б лишити платформу без модератора.
     */
    private void autoBan(UUID userId, String why, OffsetDateTime now) {
        Optional<User> target = users.findById(userId);
        if (target.isEmpty() || target.get().globalAdmin()) {
            return;
        }
        if (moderation.findActiveBan(userId).filter(ban -> ban.isActive(now)).isPresent()) {
            return;
        }
        OffsetDateTime until = now.plus(properties.autoBan().duration());
        String reason = "Автоматичне блокування: " + why;
        transactions.execute(tx -> block(userId, null, reason, until, now));
        LOG.info("Auto-banned user {} until {}: {}", userId, until, why);
    }

    /**
     * Створює бан і виставляє {@code app_user.status = BLOCKED}. Викликається вже в транзакції:
     * журнал і прапорець мають з'явитися разом.
     */
    private Ban block(UUID userId, UUID adminId, String reason, OffsetDateTime expiresAt, OffsetDateTime now) {
        User target = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Користувача " + userId
                        + " не знайдено"));
        if (adminId != null && target.globalAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Global Admin не блокується через цей маршрут");
        }
        Optional<Ban> existing = moderation.findActiveBan(userId);
        if (existing.isPresent()) {
            if (existing.get().isActive(now)) {
                throw new ApiException(ErrorCode.ALREADY_BANNED, "Користувач уже заблокований");
            }
            // строк вийшов, а планувальник ще не дійшов: знімаємо тут, бо незнятий бан на людину лише один
            lift(existing.get(), null, now);
        }
        // якщо статус і бани розійшлися, повертати людину після розблокування будемо в ACTIVE
        UserStatus previous = target.status() == UserStatus.BLOCKED ? UserStatus.ACTIVE : target.status();
        Ban ban = moderation.saveBan(Ban.create(userId, adminId, reason, expiresAt, previous, now));
        users.save(target.withStatus(UserStatus.BLOCKED, now));
        return ban;
    }

    /** Знімає бан і повертає користувачу статус, який був до блокування. */
    private Ban lift(Ban ban, UUID adminId, OffsetDateTime now) {
        Ban lifted = moderation.saveBan(ban.lift(adminId, now));
        users.findById(ban.userId())
                .filter(user -> user.status() == UserStatus.BLOCKED)
                .ifPresent(user -> users.save(user.withStatus(ban.previousStatus(), now)));
        return lifted;
    }

    private Complaint requireComplaint(UUID complaintId) {
        return moderation.findComplaint(complaintId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPLAINT_NOT_FOUND,
                        "Скаргу " + complaintId + " не знайдено"));
    }

    private static ApiException contentNotFound(ContentType targetType, UUID targetId) {
        ErrorCode code = targetType == ContentType.ADVERT ? ErrorCode.ADVERT_NOT_FOUND : ErrorCode.REPLY_NOT_FOUND;
        return new ApiException(code, targetType.name().toLowerCase(Locale.ROOT) + " " + targetId + " не знайдено");
    }

    private static String humanize(Duration window) {
        if (window.toHours() >= 24 && window.toHours() % 24 == 0) {
            long days = window.toHours() / 24;
            return days == 1 ? "добу" : days + " дн.";
        }
        if (window.toMinutes() >= 60 && window.toMinutes() % 60 == 0) {
            long hours = window.toHours();
            return hours == 1 ? "годину" : hours + " год.";
        }
        return window.toMinutes() + " хв.";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
