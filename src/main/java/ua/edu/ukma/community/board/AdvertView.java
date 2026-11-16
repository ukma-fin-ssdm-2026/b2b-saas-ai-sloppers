package ua.edu.ukma.community.board;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Оголошення очима конкретного користувача. Єдине місце, де вирішується, чи можна показати контакт
 * (FR-049, NFR-009): автору — завжди, відгукувачу — лише після підтвердження, решті — ніколи.
 * Закриття оголошення доступ підтвердженого відгукувача не забирає (US-013).
 *
 * @param own           чи переглядач — автор
 * @param myReplyStatus статус відгуку переглядача на це оголошення, або {@code null}
 * @param status        статус з урахуванням дедлайну ({@link Advert#effectiveStatus})
 */
public record AdvertView(Advert advert, boolean own, ReplyStatus myReplyStatus, AdvertStatus status) {

    public static AdvertView of(Advert advert, UUID viewerId, ReplyStatus myReplyStatus, OffsetDateTime now) {
        return new AdvertView(advert, advert.isAuthor(viewerId), myReplyStatus, advert.effectiveStatus(now));
    }

    public boolean contactVisible() {
        return own || myReplyStatus == ReplyStatus.CONFIRMED;
    }

    /** Контакт автора або {@code null}, якщо переглядачу його бачити не можна. */
    public String visibleContact() {
        return contactVisible() ? advert.contact() : null;
    }
}
