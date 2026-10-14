package ua.edu.ukma.community.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

@Schema(description = "Посилання для відкриття події в Google Calendar (FR-041)")
public record GoogleCalendarLink(
        @Schema(description = "Посилання на шаблон події",
                example = "https://calendar.google.com/calendar/render?action=TEMPLATE&text=...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        URI url
) {
}
