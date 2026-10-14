package ua.edu.ukma.community.calendar;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;
import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.event.EventService;
import ua.edu.ukma.community.organization.OrganizationService;

import java.util.UUID;


@RestController
@RequestMapping(ApiPaths.V1 + "/events/{eventId}")
@Tag(name = "Calendar")
public class CalendarController {

    static final String TEXT_CALENDAR = "text/calendar";
    private static final String EVENT_ID_EXAMPLE = "e1000000-0000-4000-8000-000000000001";

    private final EventService events;
    private final OrganizationService organizations;
    private final CalendarExportService calendar;

    public CalendarController(EventService events, OrganizationService organizations,
                              CalendarExportService calendar) {
        this.events = events;
        this.organizations = organizations;
        this.calendar = calendar;
    }

    @GetMapping("/calendar.ics")
    @Operation(operationId = "downloadEventIcs", summary = "Файл .ics для події",
            description = "Публічна (FR-042). Час у файлі в UTC, календар користувача покаже його в місцевому поясі.")
    @ApiResponse(responseCode = "200", description = "Файл календаря",
            content = @Content(mediaType = TEXT_CALENDAR, schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public ResponseEntity<String> downloadEventIcs(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        Event event = events.getPublished(eventId);
        String ics = calendar.toIcs(event, organizations.get(event.organizationId()).name());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(TEXT_CALENDAR + ";charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("event-" + event.id() + ".ics").build().toString())
                .body(ics);
    }

    @GetMapping("/calendar/google")
    @Operation(operationId = "getGoogleCalendarLink", summary = "Посилання на Google Calendar",
            description = "Публічна (FR-041). Повертає посилання на шаблон події з назвою, часом, місцем і описом.")
    @ApiResponse(responseCode = "200", description = "Посилання сформовано")
    @ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public GoogleCalendarLink getGoogleCalendarLink(
            @Parameter(description = "Ідентифікатор події (UUID)", example = EVENT_ID_EXAMPLE)
            @PathVariable("eventId") UUID eventId) {
        Event event = events.getPublished(eventId);
        return new GoogleCalendarLink(
                calendar.googleCalendarLink(event, organizations.get(event.organizationId()).name()));
    }
}
