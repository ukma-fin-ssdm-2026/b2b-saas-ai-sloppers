package ua.edu.ukma.community.event;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.storage.ImageService;

import java.util.UUID;


@Service
public class EventImageService {

    private final EventService events;
    private final ImageService images;

    public EventImageService(EventService events, ImageService images) {
        this.events = events;
        this.images = images;
    }

    public Event upload(UUID eventId, MultipartFile file) {
        Event current = events.get(eventId);
        current.requireNotRejected("змінити картинку");
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
