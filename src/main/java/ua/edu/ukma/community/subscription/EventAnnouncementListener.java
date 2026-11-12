package ua.edu.ukma.community.subscription;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ua.edu.ukma.community.config.AsyncConfig;
import ua.edu.ukma.community.event.EventPublished;

/**
 * Розсилає листи про нову подію у фоні (Lab 09, R1, fail safe).
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT}: лист іде лише про подію, яка справді збережена. Якщо транзакції немає
 *       (сервіси подій зараз працюють без неї), {@code fallbackExecution} запускає слухача одразу.</li>
 *   <li>{@code @Async} на обмеженому пулі {@link AsyncConfig#ANNOUNCEMENTS}: запит на створення
 *       чи схвалення події не чекає SMTP і не тримає монітор {@code EventService}.</li>
 * </ul>
 * Подія вже створена й видима в дайджесті; якщо лист не дійшов, це записано в лог, а користувач
 * нічого не помічає.
 */
@Component
public class EventAnnouncementListener {

    private final SubscriptionService subscriptions;

    public EventAnnouncementListener(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Async(AsyncConfig.ANNOUNCEMENTS)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEventPublished(EventPublished published) {
        subscriptions.notifyNewEvent(published.event());
    }
}
