package ua.edu.ukma.community.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.ukma.community.support.TestFixtures.NOW;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.event;

/**
 * Машина станів модерації події (FR-032, FR-035, FR-036) — чиста доменна логіка без моків.
 */
@DisplayName("Event: переходи станів")
class EventTest {

    private static final OffsetDateTime STARTS = NOW.plusDays(7);
    private static final OffsetDateTime LATER = NOW.plusMinutes(5);

    @ParameterizedTest(name = "{0} (verified={1}) → PUBLISHED")
    @CsvSource({"PENDING, false", "UNPUBLISHED, true", "PUBLISHED, false"})
    void approveAllowedTransitions(EventStatus from, boolean verified) {
        Event approved = event(ORG_ID, from, verified, STARTS).approve(LATER);

        assertThat(approved.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(approved.verified()).isTrue();
        assertThat(approved.updatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest(name = "approve із {0} (verified={1}) заборонено")
    @CsvSource({"PUBLISHED, true", "REJECTED, true"})
    void approveForbiddenTransitions(EventStatus from, boolean verified) {
        Event current = event(ORG_ID, from, verified, STARTS);

        assertThatThrownBy(() -> current.approve(LATER))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @ParameterizedTest(name = "reject із {0} заборонено")
    @EnumSource(value = EventStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void rejectOnlyFromPending(EventStatus from) {
        Event current = event(ORG_ID, from, true, STARTS);

        assertThatThrownBy(() -> current.reject(LATER)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("unpublish зберігає ознаку verified (неперевірена лишається неперевіреною)")
    void unpublishKeepsVerifiedFlag() {
        Event autoPublished = event(ORG_ID, EventStatus.PUBLISHED, false, STARTS);

        Event unpublished = autoPublished.unpublish(LATER);

        assertThat(unpublished.status()).isEqualTo(EventStatus.UNPUBLISHED);
        assertThat(unpublished.verified()).isFalse();
    }

    @Test
    @DisplayName("withDetails: null-поля не змінюються, назва обрізається, службові поля незмінні")
    void withDetailsIsPartial() {
        Event original = event(ORG_ID, EventStatus.PENDING, false, STARTS);

        Event edited = original.withDetails("  Нова назва ", null, null, "Корпус 4", null, LATER);

        assertThat(edited.title()).isEqualTo("Нова назва");
        assertThat(edited.location()).isEqualTo("Корпус 4");
        assertThat(edited.description()).isEqualTo(original.description());
        assertThat(edited.startsAt()).isEqualTo(original.startsAt());
        assertThat(edited.originalUrl()).isEqualTo(original.originalUrl());
        assertThat(edited.status()).isEqualTo(EventStatus.PENDING);
        assertThat(edited.createdAt()).isEqualTo(original.createdAt());
        assertThat(edited.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("відхилену подію редагувати не можна")
    void rejectedEventCannotBeEdited() {
        Event rejected = event(ORG_ID, EventStatus.REJECTED, true, STARTS);

        assertThatThrownBy(() -> rejected.withDetails("Нова назва", null, null, null, null, LATER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    @DisplayName("відхилену подію не можна змінювати, решту — можна (requireNotRejected, withImage)")
    void requireNotRejected() {
        Event rejected = event(ORG_ID, EventStatus.REJECTED, true, STARTS);
        Event pending = event(ORG_ID, EventStatus.PENDING, false, STARTS);

        assertThatThrownBy(() -> rejected.requireNotRejected("змінити картинку"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("змінити картинку");
        assertThatThrownBy(() -> rejected.withImage("https://cdn/x.png", LATER))
                .isInstanceOf(ApiException.class);
        pending.requireNotRejected("змінити картинку");
        assertThat(pending.withImage("https://cdn/x.png", LATER).imageUrl()).isEqualTo("https://cdn/x.png");
    }

    @Test
    @DisplayName("обов'язкові поля конструктора перевіряються на null")
    void constructorRejectsNullTitle() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Event(UUID.randomUUID(), ORG_ID, null, null, STARTS, null, null,
                        null, null, null, EventStatus.PENDING, false, NOW, NOW))
                .withMessage("title");
    }
}