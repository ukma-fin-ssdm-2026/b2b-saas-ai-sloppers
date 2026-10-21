package ua.edu.ukma.community.event;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DedupKeyTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-03-12T18:00:00+02:00");

    @Test
    void sameTitleAndStartGiveSameKey() {
        String first = DedupKey.of("Дебатний турнір", START);
        String second = DedupKey.of("Дебатний турнір", START);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void titleCaseDoesNotAffectKey() {
        String lower = DedupKey.of("дебатний турнір", START);
        String upper = DedupKey.of("ДЕБАТНИЙ ТУРНІР", START);

        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void emojiAndPunctuationDoNotAffectKey() {
        String plain = DedupKey.of("Дебатний турнір", START);
        String decorated = DedupKey.of("🔥 Дебатний турнір!!! 🔥", START);

        assertThat(plain).isEqualTo(decorated);
    }
}
