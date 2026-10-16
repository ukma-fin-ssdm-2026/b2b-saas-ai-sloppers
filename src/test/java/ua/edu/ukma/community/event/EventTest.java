package ua.edu.ukma.community.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-16T12:00:00Z");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-20T18:00:00Z");

    @Test
    void approve_publishesPendingEventAndMarksItVerified() {
        Event pending = event(EventStatus.PENDING, false);

        Event approved = pending.approve(NOW);

        assertEquals(EventStatus.PUBLISHED, approved.status());
        assertTrue(approved.verified());
        assertEquals(NOW, approved.updatedAt());
    }

    @Test
    void reject_throwsWhenEventIsAlreadyPublished() {
        Event published = event(EventStatus.PUBLISHED, true);

        ApiException error = assertThrows(ApiException.class, () -> published.reject(NOW));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, error.code());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "REJECTED", "UNPUBLISHED"})
    void reject_throwsWhenStatusIsNotPending(EventStatus status) {
        Event event = event(status, true);

        ApiException error = assertThrows(ApiException.class, () -> event.reject(NOW));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, error.code());
    }

    @Test
    void approve_republishesUnpublishedEventAndMarksItVerified() {
        Event unpublished = event(EventStatus.UNPUBLISHED, false);

        Event approved = unpublished.approve(NOW);

        assertEquals(EventStatus.PUBLISHED, approved.status());
        assertTrue(approved.verified());
        assertEquals(NOW, approved.updatedAt());
    }

    @Test
    void approve_allowsUnverifiedPublishedEventAndMarksItVerified() {
        Event publishedUnverified = event(EventStatus.PUBLISHED, false);

        Event approved = publishedUnverified.approve(NOW);

        assertEquals(EventStatus.PUBLISHED, approved.status());
        assertTrue(approved.verified());
        assertEquals(NOW, approved.updatedAt());
    }

    @Test
    void approve_throwsWhenPublishedEventIsAlreadyVerified() {
        Event publishedVerified = event(EventStatus.PUBLISHED, true);

        ApiException error = assertThrows(ApiException.class, () -> publishedVerified.approve(NOW));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, error.code());
    }

    @Test
    void approve_throwsWhenEventIsRejected() {
        Event rejected = event(EventStatus.REJECTED, true);

        ApiException error = assertThrows(ApiException.class, () -> rejected.approve(NOW));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, error.code());
    }

    private static Event event(EventStatus status, boolean verified) {
        return new Event(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Meetup title",
                "Description",
                STARTS_AT,
                "Kyiv",
                "https://example.com/event",
                null,
                null,
                status,
                verified,
                NOW.minusDays(1),
                NOW.minusDays(1)
        );
    }
}
