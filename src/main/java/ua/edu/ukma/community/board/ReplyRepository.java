package ua.edu.ukma.community.board;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Сховище відгуків (таблиця {@code advert_response}). */
public interface ReplyRepository {

    Optional<Reply> findById(UUID id);

    Optional<Reply> findByAdvertAndUser(UUID advertId, UUID userId);

    List<Reply> findByAdvert(UUID advertId);

    List<Reply> findByUser(UUID userId);

    long countConfirmed(UUID advertId);

    Reply save(Reply reply);
}
