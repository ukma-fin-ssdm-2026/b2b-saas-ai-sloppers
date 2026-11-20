package ua.edu.ukma.community.moderation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Знімає блокування, строк яких вийшов (FR-054, TC-039). Користувач не чекає на планувальник, щоб
 * знову бачити сайт: {@code Ban.isActive} уже не вважає прострочений бан дійсним, тож завдання лише
 * зводить {@code app_user.status} до журналу банів.
 */
@Component
public class ModerationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ModerationScheduler.class);

    private final ModerationService moderation;

    public ModerationScheduler(ModerationService moderation) {
        this.moderation = moderation;
    }

    @Scheduled(initialDelayString = "${moderation.lift-expired.initial-delay:PT1M}",
            fixedDelayString = "${moderation.lift-expired.interval:PT5M}")
    public void liftExpiredBans() {
        int lifted = moderation.liftExpiredBans();
        if (lifted > 0) {
            LOG.info("Lifted {} expired ban(s)", lifted);
        }
    }
}
