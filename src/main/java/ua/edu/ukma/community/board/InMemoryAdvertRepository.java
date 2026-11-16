package ua.edu.ukma.community.board;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Оголошення в пам'яті процесу: профіль {@code inmemory} і тести. */
@Repository
@Profile("inmemory")
public class InMemoryAdvertRepository implements AdvertRepository {

    private final Map<UUID, Advert> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Advert> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    /** Бази немає: послідовність забезпечує {@code synchronized} у {@link BoardService}. */
    @Override
    public Optional<Advert> lock(UUID id) {
        return findById(id);
    }

    @Override
    public List<Advert> findAllById(Collection<UUID> ids) {
        return ids.stream().map(store::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<Advert> findOpen(AdvertType type, OffsetDateTime now) {
        return store.values().stream()
                .filter(a -> a.isOpen(now))
                .filter(a -> type == null || a.type() == type)
                .toList();
    }

    @Override
    public List<Advert> findByAuthor(UUID authorId) {
        return store.values().stream()
                .filter(a -> a.isAuthor(authorId) && !a.isDeleted())
                .toList();
    }

    @Override
    public Advert save(Advert advert) {
        store.put(advert.id(), advert);
        return advert;
    }

    @Override
    public synchronized int closeExpired(OffsetDateTime now) {
        int closed = 0;
        for (Advert advert : List.copyOf(store.values())) {
            if (advert.status() == AdvertStatus.ACTIVE && !advert.deadline().isAfter(now)) {
                store.put(advert.id(), advert.close(now));
                closed++;
            }
        }
        return closed;
    }
}
