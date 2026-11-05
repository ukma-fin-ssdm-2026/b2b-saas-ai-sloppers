package ua.edu.ukma.community.calendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.event.EventStatus;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.NOW;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;

/**
 * Характеризаційні тести (Лаб 8): фіксують поточну поведінку експорту в календар перед рефакторингом.
 * Очікувані значення зняті з наявної реалізації, а не з RFC 5545, тож тест падає на будь-яку зміну виходу.
 */
@DisplayName("CalendarExportService: поточна поведінка ICS і Google Calendar")
class CalendarExportServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime STARTS = OffsetDateTime.of(2026, 10, 28, 18, 30, 0, 0, ZoneOffset.ofHours(2));
    private static final String ORGANIZER = "FIDo";
    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;

    private final CalendarExportService service = new CalendarExportService(FIXED_CLOCK);

    private static Event event(String title, String description, String location, String originalUrl) {
        return new Event(EVENT_ID, ORG_ID, title, description, STARTS, location, originalUrl, null, null, null,
                EventStatus.PUBLISHED, true, NOW, NOW);
    }

    private static Event fullEvent() {
        return event("Відкрита лекція: AI, етика; право", "  Говоримо про відповідальність.\nВхід вільний.  ",
                "НаУКМА, 1-225", "https://www.instagram.com/p/demo/");
    }

    @Test
    @DisplayName("ICS з усіма полями: порядок рядків, UTC-час, екранування, перенос довгого DESCRIPTION")
    void icsWithAllFields() {
        assertThat(service.toIcs(fullEvent(), ORGANIZER)).isEqualTo(
                "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//NaUKMA//Student Community//UK\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "METHOD:PUBLISH\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:e1000000-0000-4000-8000-000000000001@community.ukma.edu.ua\r\n"
                + "DTSTAMP:20261021T100000Z\r\n"
                + "DTSTART:20261028T163000Z\r\n"
                + "DTEND:20261028T183000Z\r\n"
                + "SUMMARY:Відкрита лекція: AI\\, етика\\; право\r\n"
                + "DESCRIPTION:Говоримо про відповідальність.\\nВх\r\n"
                + " ід вільний.\\n\\nОрганізатор: FIDo\\nАнонс: https://\r\n"
                + " www.instagram.com/p/demo/\r\n"
                + "LOCATION:НаУКМА\\, 1-225\r\n"
                + "URL:https://www.instagram.com/p/demo/\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n");
    }

    @Test
    @DisplayName("ICS без необов'язкових полів: немає LOCATION і URL, порожній опис пропускається")
    void icsWithoutOptionalFields() {
        String ics = service.toIcs(event("Зустріч", "   ", null, null), ORGANIZER);

        assertThat(ics).contains("SUMMARY:Зустріч\r\n" + "DESCRIPTION:Організатор: FIDo\r\n" + "END:VEVENT\r\n");
        assertThat(ics).doesNotContain("LOCATION:", "URL:");
    }

    static Stream<Arguments> foldingByUtf8Width() {
        String emoji = "😀";
        return Stream.of(
                Arguments.of("1 байт (ASCII)", "a".repeat(80),
                        "SUMMARY:" + "a".repeat(67) + CRLF + " " + "a".repeat(13) + CRLF),
                Arguments.of("2 байти (кирилиця)", "Я".repeat(40),
                        "SUMMARY:" + "Я".repeat(33) + CRLF + " " + "Я".repeat(7) + CRLF),
                Arguments.of("3 байти (€)", "€".repeat(30),
                        "SUMMARY:" + "€".repeat(22) + CRLF + " " + "€".repeat(8) + CRLF),
                Arguments.of("4 байти (емодзі не розрізається)", "a".repeat(66) + emoji + "b",
                        "SUMMARY:" + "a".repeat(66) + CRLF + " " + emoji + "b" + CRLF),
                // кодові точки рівно на межах ширини UTF-8: зсув будь-якої межі змінює місце переносу
                foldCase(0x7F, 80, 67, 13),
                foldCase(0x80, 40, 33, 7),
                foldCase(0x7FF, 40, 33, 7),
                foldCase(0x800, 30, 22, 8),
                foldCase(0xFFFF, 30, 22, 8),
                foldCase(0x10000, 20, 16, 4));
    }

    private static Arguments foldCase(int codePoint, int total, int onFirstLine, int onSecondLine) {
        String symbol = Character.toString(codePoint);
        return Arguments.of(String.format("U+%04X", codePoint), symbol.repeat(total),
                "SUMMARY:" + symbol.repeat(onFirstLine) + CRLF + " " + symbol.repeat(onSecondLine) + CRLF);
    }

    @ParameterizedTest(name = "перенос рядка ICS: символи по {0}")
    @MethodSource("foldingByUtf8Width")
    void foldsLongLinesByUtf8Octets(String label, String title, String expectedSummary) {
        String ics = service.toIcs(event(title, null, null, null), ORGANIZER);

        assertThat(ics).contains(CRLF + expectedSummary + "DESCRIPTION:");
        for (String line : ics.split(CRLF)) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).as(line).isLessThanOrEqualTo(MAX_LINE_OCTETS);
        }
    }

    @Test
    @DisplayName("escapeText: зворотна похила, ; і , екрануються, усі види переносу стають \\n")
    void escapesTextValues() {
        assertThat(CalendarExportService.escapeText("a\\b;c,d\r\ne\rf\ng")).isEqualTo("a\\\\b\\;c\\,d\\ne\\nf\\ng");
    }

    @Test
    @DisplayName("Google Calendar: усі параметри закодовані, пробіл як %20, тривалість 2 год")
    void googleLinkWithAllFields() {
        assertThat(service.googleCalendarLink(fullEvent(), ORGANIZER)).hasToString(
                "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=%D0%92%D1%96%D0%B4%D0%BA%D1%80%D0%B8%D1%82%D0%B0%20%D0%BB%D0%B5%D0%BA%D1%86%D1%96%D1%8F"
                + "%3A%20AI%2C%20%D0%B5%D1%82%D0%B8%D0%BA%D0%B0%3B%20%D0%BF%D1%80%D0%B0%D0%B2%D0%BE"
                + "&dates=20261028T163000Z%2F20261028T183000Z"
                + "&details=%D0%93%D0%BE%D0%B2%D0%BE%D1%80%D0%B8%D0%BC%D0%BE%20%D0%BF%D1%80%D0%BE%20"
                + "%D0%B2%D1%96%D0%B4%D0%BF%D0%BE%D0%B2%D1%96%D0%B4%D0%B0%D0%BB%D1%8C%D0%BD%D1%96%D1%81%D1%82%D1%8C."
                + "%0A%D0%92%D1%85%D1%96%D0%B4%20%D0%B2%D1%96%D0%BB%D1%8C%D0%BD%D0%B8%D0%B9.%0A%0A"
                + "%D0%9E%D1%80%D0%B3%D0%B0%D0%BD%D1%96%D0%B7%D0%B0%D1%82%D0%BE%D1%80%3A%20FIDo%0A"
                + "%D0%90%D0%BD%D0%BE%D0%BD%D1%81%3A%20https%3A%2F%2Fwww.instagram.com%2Fp%2Fdemo%2F"
                + "&location=%D0%9D%D0%B0%D0%A3%D0%9A%D0%9C%D0%90%2C%201-225"
                + "&ctz=Europe%2FKyiv");
    }

    @Test
    @DisplayName("Google Calendar без місця: параметра location немає")
    void googleLinkWithoutLocation() {
        assertThat(service.googleCalendarLink(event("Зустріч", null, null, null), ORGANIZER)).hasToString(
                "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=%D0%97%D1%83%D1%81%D1%82%D1%80%D1%96%D1%87"
                + "&dates=20261028T163000Z%2F20261028T183000Z"
                + "&details=%D0%9E%D1%80%D0%B3%D0%B0%D0%BD%D1%96%D0%B7%D0%B0%D1%82%D0%BE%D1%80%3A%20FIDo"
                + "&ctz=Europe%2FKyiv");
    }
}
