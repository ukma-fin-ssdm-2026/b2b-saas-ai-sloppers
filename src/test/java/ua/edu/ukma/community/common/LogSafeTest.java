package ua.edu.ukma.community.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogSafe: значення ззовні не можуть підробити рядок у лозі")
class LogSafeTest {

    @Test
    @DisplayName("переноси рядка замінюються на _, решта тексту без змін")
    void replacesLineBreaks() {
        assertThat(LogSafe.of("user@ukma.edu.ua\r\nINFO fake admin login"))
                .isEqualTo("user@ukma.edu.ua__INFO fake admin login")
                .doesNotContain("\n", "\r");
        assertThat(LogSafe.of("a b\u0085c")).isEqualTo("a_b_c");
        assertThat(LogSafe.of("organizations/42/logo-abc.png")).isEqualTo("organizations/42/logo-abc.png");
    }

    @Test
    @DisplayName("null і не-рядки")
    void handlesNullAndObjects() {
        assertThat(LogSafe.of(null)).isEqualTo("null");
        assertThat(LogSafe.of(42)).isEqualTo("42");
    }
}
