package ua.edu.ukma.community.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Годинник, який тест може перемотати вперед: для строків дії коду й пауз між запитами.
 */
public final class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    public static MutableClock startingAtFixtureNow() {
        return new MutableClock(TestFixtures.NOW_INSTANT, TestFixtures.FIXED_CLOCK.getZone());
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now, newZone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
