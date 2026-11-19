package ua.edu.ukma.community.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.ukma.community.support.TestFixtures.NOW;

@DisplayName("Advert і Reply: переходи станів")
class AdvertTest {

    private static Advert advert(AdvertStatus status) {
        return new Advert(UUID.randomUUID(), UUID.randomUUID(), AdvertType.TEAM, "Шукаю команду", "Опис", 2,
                NOW.plusDays(1), "+380501112233", status, NOW, NOW);
    }

    private static Reply reply(ReplyStatus status) {
        return new Reply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "привіт", status, NOW, null);
    }

    @Test
    @DisplayName("відкрите лише ACTIVE до дедлайну; після дедлайну статус для користувача — CLOSED")
    void openness() {
        Advert active = advert(AdvertStatus.ACTIVE);

        assertThat(active.isOpen(NOW)).isTrue();
        assertThat(active.isOpen(NOW.plusDays(1))).isFalse();
        assertThat(active.effectiveStatus(NOW)).isEqualTo(AdvertStatus.ACTIVE);
        assertThat(active.effectiveStatus(NOW.plusDays(1))).isEqualTo(AdvertStatus.CLOSED);
        assertThat(advert(AdvertStatus.CLOSED).isOpen(NOW)).isFalse();
        assertThat(advert(AdvertStatus.DELETED).effectiveStatus(NOW)).isEqualTo(AdvertStatus.DELETED);
    }

    @Test
    @DisplayName("закрити можна лише ACTIVE, видалити — будь-яке, крім уже видаленого")
    void advertTransitions() {
        assertThat(advert(AdvertStatus.ACTIVE).close(NOW).status()).isEqualTo(AdvertStatus.CLOSED);
        assertThat(advert(AdvertStatus.CLOSED).delete(NOW).status()).isEqualTo(AdvertStatus.DELETED);
        assertThatThrownBy(() -> advert(AdvertStatus.CLOSED).close(NOW))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> advert(AdvertStatus.DELETED).delete(NOW)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("кількість місць менше 1 не створюється навіть в обхід валідації")
    void slotsMustBePositive() {
        assertThatThrownBy(() -> new Advert(UUID.randomUUID(), UUID.randomUUID(), AdvertType.LEISURE, "t", "d", 0,
                NOW, "@someone", AdvertStatus.ACTIVE, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NFR-009: toString не містить контакту й тексту відгуку")
    void toStringHidesPersonalData() {
        assertThat(advert(AdvertStatus.ACTIVE).toString()).doesNotContain("380501112233");
        assertThat(reply(ReplyStatus.PENDING).toString()).doesNotContain("привіт");
    }

    @Test
    @DisplayName("рішення щодо відгуку — лише з PENDING; повторно подати — лише WITHDRAWN")
    void replyTransitions() {
        Reply confirmed = reply(ReplyStatus.PENDING).confirm(NOW);
        assertThat(confirmed.status()).isEqualTo(ReplyStatus.CONFIRMED);
        assertThat(confirmed.decidedAt()).isEqualTo(NOW);
        assertThat(reply(ReplyStatus.PENDING).reject(NOW).status()).isEqualTo(ReplyStatus.REJECTED);

        assertThatThrownBy(() -> confirmed.withdraw(NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reply(ReplyStatus.REJECTED).confirm(NOW)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reply(ReplyStatus.PENDING).resubmit("ще", NOW)).isInstanceOf(ApiException.class);

        Reply again = reply(ReplyStatus.WITHDRAWN).resubmit("ще", NOW.plusHours(1));
        assertThat(again.status()).isEqualTo(ReplyStatus.PENDING);
        assertThat(again.decidedAt()).isNull();
        assertThat(again.createdAt()).isEqualTo(NOW.plusHours(1));
    }
}
