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
}
