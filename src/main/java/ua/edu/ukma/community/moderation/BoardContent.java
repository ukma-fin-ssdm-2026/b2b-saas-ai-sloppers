package ua.edu.ukma.community.moderation;

import org.springframework.stereotype.Component;
import ua.edu.ukma.community.board.Advert;
import ua.edu.ukma.community.board.AdvertRepository;
import ua.edu.ukma.community.board.Reply;
import ua.edu.ukma.community.board.ReplyRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ModeratedContent} над дошкою оголошень: єдине місце, де модерація торкається оголошень і
 * відгуків. Залежність одностороння: дошка про скарги й бани не знає, вона бачить лише
 * {@link ModerationGate}.
 */
@Component
public class BoardContent implements ModeratedContent {

    private final AdvertRepository adverts;
    private final ReplyRepository replies;
    private final Clock clock;

    public BoardContent(AdvertRepository adverts, ReplyRepository replies, Clock clock) {
        this.adverts = adverts;
        this.replies = replies;
        this.clock = clock;
    }

    @Override
    public Optional<UUID> authorOf(ContentType type, UUID id) {
        return type == ContentType.ADVERT
                ? visibleAdvert(id).map(Advert::authorId)
                : visibleReply(id).map(Reply::userId);
    }

    @Override
    public void remove(ContentType type, UUID id) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (type == ContentType.ADVERT) {
            visibleAdvert(id).ifPresent(advert -> adverts.save(advert.delete(now)));
        } else {
            visibleReply(id).ifPresent(reply -> replies.save(reply.remove(now)));
        }
    }

    @Override
    public long countAdvertsSince(UUID authorId, OffsetDateTime since) {
        return adverts.countByAuthorSince(authorId, since);
    }

    @Override
    public long countRepliesSince(UUID userId, OffsetDateTime since) {
        return replies.countByUserSince(userId, since);
    }

    private Optional<Advert> visibleAdvert(UUID id) {
        return adverts.findById(id).filter(advert -> !advert.isDeleted());
    }

    private Optional<Reply> visibleReply(UUID id) {
        return replies.findById(id).filter(reply -> !reply.isRemoved());
    }
}
