package ua.edu.ukma.community.moderation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Модерація в пам'яті процесу: профіль {@code inmemory} і модульні тести. */
@Repository
@Profile("inmemory")
public class InMemoryModerationRepository implements ModerationRepository {

    private static final Comparator<Complaint> NEWEST_COMPLAINT =
            Comparator.comparing(Complaint::createdAt).reversed().thenComparing(Complaint::id);
    private static final Comparator<Ban> NEWEST_BAN =
            Comparator.comparing(Ban::createdAt).reversed().thenComparing(Ban::id);

    private final Map<UUID, Complaint> complaints = new ConcurrentHashMap<>();
    private final Map<UUID, Ban> bans = new ConcurrentHashMap<>();
    private final Map<UUID, BannedWord> words = new ConcurrentHashMap<>();
    private final List<FilterRejection> rejections = new ArrayList<>();

    @Override
    public Complaint saveComplaint(Complaint complaint) {
        complaints.put(complaint.id(), complaint);
        return complaint;
    }

    @Override
    public Optional<Complaint> findComplaint(UUID id) {
        return Optional.ofNullable(complaints.get(id));
    }

    @Override
    public List<Complaint> findComplaints(ComplaintStatus status, int limit, int offset) {
        List<Complaint> matching = matchingComplaints(status).sorted(NEWEST_COMPLAINT).toList();
        int from = Math.min(offset, matching.size());
        int to = (int) Math.min((long) from + limit, matching.size());
        return List.copyOf(matching.subList(from, to));
    }

    @Override
    public long countComplaints(ComplaintStatus status) {
        return matchingComplaints(status).count();
    }

    @Override
    public boolean complaintExists(UUID reporterId, ContentType targetType, UUID targetId) {
        return complaints.values().stream().anyMatch(c -> c.reporterId().equals(reporterId)
                && c.targetType() == targetType && c.targetId().equals(targetId));
    }

    @Override
    public long countComplainantsAgainst(UUID authorId) {
        return complaints.values().stream()
                .filter(c -> c.authorId().equals(authorId) && c.status() != ComplaintStatus.DISMISSED)
                .map(Complaint::reporterId)
                .distinct()
                .count();
    }

    @Override
    public synchronized int resolveComplaintsOn(ContentType targetType, UUID targetId, ComplaintStatus decision,
                                                UUID adminId, String resolution, OffsetDateTime at) {
        List<Complaint> open = complaints.values().stream()
                .filter(c -> c.isOpen() && c.targetType() == targetType && c.targetId().equals(targetId))
                .toList();
        open.forEach(c -> saveComplaint(c.resolve(decision, adminId, resolution, at)));
        return open.size();
    }

    @Override
    public Ban saveBan(Ban ban) {
        bans.put(ban.id(), ban);
        return ban;
    }

    @Override
    public Optional<Ban> findActiveBan(UUID userId) {
        return bans.values().stream()
                .filter(b -> b.userId().equals(userId) && !b.isLifted())
                .max(Comparator.comparing(Ban::createdAt));
    }

    @Override
    public List<Ban> findBans(UUID userId) {
        return bans.values().stream().filter(b -> b.userId().equals(userId)).sorted(NEWEST_BAN).toList();
    }

    @Override
    public List<Ban> findExpiredBans(OffsetDateTime now) {
        return bans.values().stream().filter(b -> !b.isLifted() && b.hasExpired(now)).toList();
    }

    @Override
    public Set<UUID> findBannedUsers(OffsetDateTime now) {
        return bans.values().stream()
                .filter(b -> b.isActive(now))
                .map(Ban::userId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<BannedWord> findBannedWords() {
        return words.values().stream().sorted(Comparator.comparing(BannedWord::word)).toList();
    }

    @Override
    public BannedWord saveBannedWord(BannedWord word) {
        words.put(word.id(), word);
        return word;
    }

    @Override
    public boolean deleteBannedWord(UUID id) {
        return words.remove(id) != null;
    }

    @Override
    public synchronized FilterRejection saveRejection(FilterRejection rejection) {
        rejections.add(rejection);
        return rejection;
    }

    @Override
    public synchronized long countRejectionsSince(UUID userId, OffsetDateTime since) {
        return rejections.stream()
                .filter(r -> r.userId().equals(userId) && r.createdAt().isAfter(since))
                .count();
    }

    private Stream<Complaint> matchingComplaints(ComplaintStatus status) {
        return complaints.values().stream().filter(c -> status == null || c.status() == status);
    }
}
