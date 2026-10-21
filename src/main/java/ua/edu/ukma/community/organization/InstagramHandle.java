package ua.edu.ukma.community.organization;

import java.util.Locale;
import java.util.regex.Pattern;

final class InstagramHandle {

    private static final Pattern PROFILE_URL_PREFIX =
            Pattern.compile("^(https?://)?(www\\.)?instagram\\.com/");
    private static final String URL_TAIL = "[/?#]";
    private static final Pattern VALID_HANDLE = Pattern.compile(OrganizationRules.INSTAGRAM_REGEX);

    private InstagramHandle() {
    }

    static String normalize(String raw) {
        String lowered = raw.strip().toLowerCase(Locale.ROOT);
        String handle = PROFILE_URL_PREFIX.matcher(lowered).replaceFirst("").split(URL_TAIL, 2)[0];
        String result = handle.startsWith("@") ? handle.substring(1) : handle;
        if (!VALID_HANDLE.matcher(result).matches()) {
            throw new IllegalArgumentException(OrganizationRules.INSTAGRAM_MESSAGE);
        }
        return result;
    }
}