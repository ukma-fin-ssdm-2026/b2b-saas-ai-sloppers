package ua.edu.ukma.community.board;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Відгуки в пам'яті процесу: профіль {@code inmemory} і тести. */
@Repository
@Profile("inmemory")
public class InMemoryReplyRepository implements ReplyRepository {

    private final Map<UUID, Reply> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Reply> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Reply> findByAdvertAndUser(UUID advertId, UUID userId) {
        return store.values().stream()
                .filter(r -> r.advertId().equals(advertId) && r.userId().equals(userId))
                .findFirst();
    }

    @Override
    public List<Reply> findByAdvert(UUID advertId) {
        return store.values().stream().filter(r -> r.advertId().equals(advertId)).toList();
    }

    @Override
    public List<Reply> findByUser(UUID userId) {
        return store.values().stream().filter(r -> r.userId().equals(userId)).toList();
    }

    @Override
    public long countConfirmed(UUID advertId) {
        return store.values().stream().filter(r -> r.advertId().equals(advertId) && r.isConfirmed()).count();
    }

    /** Той самий інваріант, що й {@code UNIQUE (advert_id, user_id)} у базі. */
    @Override
    public synchronized Reply save(Reply reply) {
        findByAdvertAndUser(reply.advertId(), reply.userId())
                .filter(existing -> !existing.id().equals(reply.id()))
                .ifPresent(existing -> {
                    throw new IllegalStateException("duplicate reply for advert " + reply.advertId());
                });
        store.put(reply.id(), reply);
        return reply;
    }
}
