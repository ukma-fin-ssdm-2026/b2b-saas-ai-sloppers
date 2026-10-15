package ua.edu.ukma.community.calendar;

import org.springframework.stereotype.Service;
import ua.edu.ukma.community.config.ClockConfig;
import ua.edu.ukma.community.event.Event;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;


@Service
public class CalendarExportService {

    static final Duration DEFAULT_DURATION = Duration.ofHours(2);

    private static final DateTimeFormatter UTC_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String GOOGLE_TEMPLATE_URL = "https://calendar.google.com/calendar/render";
    private static final String UID_DOMAIN = "community.ukma.edu.ua";
    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;

    private final Clock clock;

    public CalendarExportService(Clock clock) {
        this.clock = clock;
    }

    public String toIcs(Event event, String organizationName) {
        StringBuilder ics = new StringBuilder();
        appendLine(ics, "BEGIN:VCALENDAR");
        appendLine(ics, "VERSION:2.0");
        appendLine(ics, "PRODID:-//NaUKMA//Student Community//UK");
        appendLine(ics, "CALSCALE:GREGORIAN");
        appendLine(ics, "METHOD:PUBLISH");
        appendLine(ics, "BEGIN:VEVENT");
        appendLine(ics, "UID:" + event.id() + "@" + UID_DOMAIN);
        appendLine(ics, "DTSTAMP:" + utc(clock.instant()));
        appendLine(ics, "DTSTART:" + utc(event.startsAt()));
        appendLine(ics, "DTEND:" + utc(event.startsAt().plus(DEFAULT_DURATION)));
        appendLine(ics, "SUMMARY:" + escapeText(event.title()));
        appendLine(ics, "DESCRIPTION:" + escapeText(details(event, organizationName)));
        if (event.location() != null) {
            appendLine(ics, "LOCATION:" + escapeText(event.location()));
        }
        if (event.originalUrl() != null) {
            appendLine(ics, "URL:" + stripLineBreaks(event.originalUrl()));
        }
        appendLine(ics, "END:VEVENT");
        appendLine(ics, "END:VCALENDAR");
        return ics.toString();
    }

    public URI googleCalendarLink(Event event, String organizationName) {
        StringBuilder url = new StringBuilder(GOOGLE_TEMPLATE_URL).append("?action=TEMPLATE");
        appendParam(url, "text", event.title());
        appendParam(url, "dates", utc(event.startsAt()) + "/" + utc(event.startsAt().plus(DEFAULT_DURATION)));
        appendParam(url, "details", details(event, organizationName));
        if (event.location() != null) {
            appendParam(url, "location", event.location());
        }
        appendParam(url, "ctz", ClockConfig.KYIV.getId());
        return URI.create(url.toString());
    }

    static String escapeText(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\r", "\\n")
                .replace("\n", "\\n");
    }

    private static String details(Event event, String organizationName) {
        StringBuilder text = new StringBuilder();
        if (event.description() != null && !event.description().isBlank()) {
            text.append(event.description().strip()).append("\n\n");
        }
        text.append("Організатор: ").append(organizationName);
        if (event.originalUrl() != null) {
            text.append("\nАнонс: ").append(event.originalUrl());
        }
        return text.toString();
    }

    private static String utc(TemporalAccessor moment) {
        return UTC_STAMP.format(moment);
    }

    private static String stripLineBreaks(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    private static void appendParam(StringBuilder url, String name, String value) {
        url.append('&').append(name).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static void appendLine(StringBuilder out, String content) {
        int lineOctets = 0;
        int index = 0;
        while (index < content.length()) {
            int codePoint = content.codePointAt(index);
            int octets = utf8Length(codePoint);
            if (lineOctets + octets > MAX_LINE_OCTETS) {
                out.append(CRLF).append(' ');
                lineOctets = 1;
            }
            out.appendCodePoint(codePoint);
            lineOctets += octets;
            index += Character.charCount(codePoint);
        }
        out.append(CRLF);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
