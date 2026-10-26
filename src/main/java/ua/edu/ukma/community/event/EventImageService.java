package ua.edu.ukma.community.event;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.storage.ImageService;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;


@Service
public class EventImageService {

    private final EventService events;
    private final ImageService images;
    private final Clock clock;

    public EventImageService(EventService events, ImageService images, Clock clock) {
        this.events = events;
        this.images = images;
        this.clock = clock;
    }

    public Event upload(UUID eventId, MultipartFile file) {
        Event current = events.get(eventId);
        current.withImage(null, OffsetDateTime.now(clock));
        String url = images.store(folder(current), "image", file);
        Event updated;
        try {
            updated = events.changeImage(eventId, url);
        } catch (ApiException e) {
            images.deleteQuietly(url);
            throw e;
        }
        images.deleteQuietly(current.imageUrl());
        return updated;
    }

    public void delete(UUID eventId) {
        Event current = events.get(eventId);
        if (current.imageUrl() == null) {
            return;
        }
        events.changeImage(eventId, null);
        images.deleteQuietly(current.imageUrl());
    }

    static String folder(Event event) {
        return "organizations/" + event.organizationId() + "/events/" + event.id();
    }
}
