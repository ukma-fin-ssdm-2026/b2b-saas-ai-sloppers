package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.ukma.community.support.TestFixtures.NOW;

@DisplayName("Ім'я та прізвище (FR-069)")
class NamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Теодора", "Анна-Марія", "Мар'яна", "Марʼяна", "O'Brien", "Ван Гог", "Łukasz", " Олена "})
    @DisplayName("приймаються літери будь-якої абетки, апостроф, дефіс і пробіл")
    void acceptsRealNames(String name) {
        assertThat(name.matches(Names.REGEX)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123", "R2-D2", "-Анна", "'Олена", "<script>", "a@b"})
    @DisplayName("відхиляються цифри, символи й рядки, що не починаються з літери")
    void rejectsNonNames(String name) {
        assertThat(name.matches(Names.REGEX)).isFalse();
    }

    @Test
    @DisplayName("normalize обрізає й стискає пробіли; порожнє → null")
    void normalize() {
        assertThat(Names.normalize("  Анна   Марія  ")).isEqualTo("Анна Марія");
        assertThat(Names.normalize("   ")).isNull();
        assertThat(Names.normalize(null)).isNull();
    }

    @Test
    @DisplayName("fullName склеює задане; без імені — null; withName не губить решту полів")
    void fullName() {
        User user = new User(UUID.randomUUID(), "s@ukma.edu.ua", "hash", UserStatus.ACTIVE, true, null, NOW, NOW);
        assertThat(user.fullName()).isNull();

        OffsetDateTime later = NOW.plusHours(1);
        User named = user.withName("Теодора", "Гречко", later);
        assertThat(named.fullName()).isEqualTo("Теодора Гречко");
        assertThat(named.globalAdmin()).isTrue();
        assertThat(named.updatedAt()).isEqualTo(later);
        assertThat(user.withName("Теодора", null, later).fullName()).isEqualTo("Теодора");
        assertThat(named.activated(later).fullName()).isEqualTo("Теодора Гречко");
    }
}
