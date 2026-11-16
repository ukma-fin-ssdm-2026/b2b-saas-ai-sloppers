package ua.edu.ukma.community.board;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Закриває оголошення з дедлайном, що минув (FR-050). Вимога — не пізніше ніж за годину після дедлайну;
 * типовий інтервал 5 хвилин дає запас. Навіть до запуску прострочене оголошення вже не приймає відгуків
 * і не показується серед активних ({@link Advert#isOpen}), тож планувальник лише фіксує статус у базі.
 *
 * <p>З кількома подами кожен виконує той самий {@code UPDATE ... WHERE status = 'ACTIVE'}: повторний запуск
 * нічого не змінює, тому розподілене блокування не потрібне.
 */
@Component
public class BoardScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(BoardScheduler.class);

    private final BoardService board;

    public BoardScheduler(BoardService board) {
        this.board = board;
    }

    @Scheduled(initialDelayString = "${board.close-expired.initial-delay:PT1M}",
            fixedDelayString = "${board.close-expired.interval:PT5M}")
    public void closeExpired() {
        // виняток (напр., база недоступна) Spring запише в лог, а наступний запуск спробує ще раз
        int closed = board.closeExpired();
        if (closed > 0) {
            LOG.info("Closed {} expired advert(s)", closed);
        }
    }
}
