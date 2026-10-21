package ua.edu.ukma.community.support;

import ua.edu.ukma.community.config.ClockConfig;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.event.EventStatus;
import ua.edu.ukma.community.organization.Organization;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;


public final class TestFixtures {
    public static final Instant NOW_INSTANT = Instant.parse("2026-10-21T10:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ClockConfig.KYIV);
    public static final OffsetDateTime NOW = OffsetDateTime.now(FIXED_CLOCK);

    public static final UUID ORG_ID = UUID.fromString("3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60");
    public static final UUID OTHER_ORG_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    public static final UUID SOURCE_ID = UUID.fromString("50000000-0000-4000-8000-000000000001");

    private TestFixtures() {
    }

    public static Organization organization(UUID id, String name, String slug) {
        return organization(id, name, slug, false);
    }

    public static Organization organization(UUID id, String name, String slug, boolean autoPublish) {
        OffsetDateTime created = NOW.minusDays(30);
        return new Organization(id, name, slug, "Опис " + name, slug.replace('-', '.'),
                slug + "@ukma.edu.ua", autoPublish, created, created);
    }

    public static Event event(UUID organizationId, EventStatus status, boolean verified, OffsetDateTime startsAt) {
        OffsetDateTime created = NOW.minusDays(1);
        return new Event(UUID.randomUUID(), organizationId, "Подія о " + startsAt.toLocalTime(), "Опис",
                startsAt, "НаУКМА", "https://www.instagram.com/p/demo/", null, null,
                status, verified, created, created);
    }

    public static Event publishedEvent(OffsetDateTime startsAt) {
        return event(ORG_ID, EventStatus.PUBLISHED, true, startsAt);
    }
}