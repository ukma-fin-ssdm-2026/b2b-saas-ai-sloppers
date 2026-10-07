package ua.edu.ukma.community.organization;

final class OrganizationRules {
    static final String SLUG_REGEX = "^[a-z0-9]+(-[a-z0-9]+)*$";
    static final String SLUG_MESSAGE = "Slug: лише a-z, 0-9 і дефіс між словами";
    static final String INSTAGRAM_REGEX = "^[A-Za-z0-9._]{1,30}$";
    static final String INSTAGRAM_MESSAGE = "Instagram-акаунт: 1–30 символів (літери, цифри, крапка, підкреслення), без @";

    private OrganizationRules() {
    }
}
