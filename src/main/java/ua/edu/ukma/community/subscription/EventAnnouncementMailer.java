package ua.edu.ukma.community.subscription;

import ua.edu.ukma.community.event.Event;
import ua.edu.ukma.community.organization.Organization;

public interface EventAnnouncementMailer {

    void sendNewEvent(String email, Organization organization, Event event);
}
