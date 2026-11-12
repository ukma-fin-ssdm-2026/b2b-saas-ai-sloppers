package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.event.EventCreateRequest;
import ua.edu.ukma.community.event.EventService;
import ua.edu.ukma.community.event.EventStatus;
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.subscription.EventAnnouncementMailer;
import ua.edu.ukma.community.subscription.SubscriptionRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.edu.ukma.community.organization.InMemoryOrganizationRepository.DEBATE_CLUB_ID;

/**
 * Lab 09, R1 — <b>fault injection</b>: SMTP-сервер «висить» або відмовляє.
 *
 * <p>Обрана реакція — <b>fail safe</b>: подія створюється й видима одразу, листи надсилаються у фоні,
 * а збій листа записується в лог і не заважає решті підписників. До виправлення створення події
 * чекало на SMTP під {@code synchronized} і блокувало всі зміни подій.
 */
@SpringBootTest
@ActiveProfiles("inmemory")
@DisplayName("Надійність R1: розсилка не блокує створення подій")
class AnnouncementFaultInjectionTest {

    private static final List<String> SUBSCRIBERS = List.of("first@ukma.edu.ua", "second@ukma.edu.ua");
    /** Скільки максимум може тривати створення події; SMTP у тесті «висить» значно довше. */
    private static final Duration FAST = Duration.ofSeconds(1);
    private static final long BACKGROUND_WAIT_MS = 5_000;
    private static final int DAYS_AHEAD = 3;

    @Autowired
    EventService events;

    @MockitoBean
    EventAnnouncementMailer mailer;

    @MockitoBean
    SubscriptionRepository subscriptions;

    private final CountDownLatch smtpReleased = new CountDownLatch(1);

    @BeforeEach
    void subscribers() {
        when(subscriptions.findActiveSubscriberEmails(DEBATE_CLUB_ID)).thenReturn(SUBSCRIBERS);
    }

    @AfterEach
    void releaseSmtp() {
        smtpReleased.countDown();
    }

    @Test
    @DisplayName("SMTP «висить»: дві події створюються швидко, листи доходять, коли SMTP оживає")
    void hangingSmtpDoesNotBlockEventCreation() {
        doAnswer(invocation -> smtpReleased.await(BACKGROUND_WAIT_MS, TimeUnit.MILLISECONDS))
                .when(mailer).sendNewEvent(any(), any(), any());

        Event first = assertTimeoutPreemptively(FAST, () -> events.create(DEBATE_CLUB_ID, request("Турнір")));
        // другий запит не стоїть у черзі на монітор EventService, поки перший «розсилає» листи
        Event second = assertTimeoutPreemptively(FAST, () -> events.create(DEBATE_CLUB_ID, request("Воркшоп")));

        assertThat(events.getPublished(first.id()).status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(events.getPublished(second.id()).status()).isEqualTo(EventStatus.PUBLISHED);

        smtpReleased.countDown();
        for (String email : SUBSCRIBERS) {
            verify(mailer, timeout(BACKGROUND_WAIT_MS)).sendNewEvent(eq(email), any(Organization.class),
                    eq(first));
            verify(mailer, timeout(BACKGROUND_WAIT_MS)).sendNewEvent(eq(email), any(Organization.class),
                    eq(second));
        }
    }

    @Test
    @DisplayName("SMTP відмовляє одному адресату: подія створена, решта підписників лист отримує")
    void failedEmailDoesNotStopOthers() {
        doThrow(new MailSendException("550 mailbox unavailable"))
                .when(mailer).sendNewEvent(eq(SUBSCRIBERS.get(0)), any(), any());
        doNothing().when(mailer).sendNewEvent(eq(SUBSCRIBERS.get(1)), any(), any());

        Event created = assertTimeoutPreemptively(FAST, () -> events.create(DEBATE_CLUB_ID, request("Лекція")));

        assertThat(events.getPublished(created.id()).title()).isEqualTo("Лекція");
        verify(mailer, timeout(BACKGROUND_WAIT_MS))
                .sendNewEvent(eq(SUBSCRIBERS.get(1)), any(Organization.class), eq(created));
    }

    private static EventCreateRequest request(String title) {
        return new EventCreateRequest(title, null, OffsetDateTime.now().plusDays(DAYS_AHEAD), "Корпус 1", null);
    }
}
