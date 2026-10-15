package ua.edu.ukma.community.event;


final class EventRules {
    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 5000;
    static final int LOCATION_MAX = 200;
    static final int URL_MAX = 2048;
    static final int EXTERNAL_POST_ID_MAX = 100;

    static final String URL_REGEX = "^https?://\\S+$";
    static final String URL_MESSAGE = "Посилання має починатися з http:// або https:// і не містити пробілів";
    static final String NOT_BLANK_REGEX = "(?s).*\\S.*";
    static final String NOT_BLANK_MESSAGE = "Поле не може складатися лише з пробілів";

    private EventRules() {
    }

    static boolean isValidUrl(String url) {
        return url.length() <= URL_MAX && url.matches(URL_REGEX);
    }
}
