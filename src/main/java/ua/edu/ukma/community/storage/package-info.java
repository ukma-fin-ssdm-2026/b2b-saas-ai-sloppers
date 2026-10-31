/**
 * Сховище картинок: логотипи організацій і постери подій.
 *
 * <p>Файли лежать у публічному бакеті Supabase Storage ({@code images}), у базі — лише їхні URL
 * ({@code organization.logo_url}, {@code event.image_url}, міграція V3). Структура ключів:
 * <pre>
 * organizations/{organizationId}/logo-{suffix}.png
 * organizations/{organizationId}/events/{eventId}/image-{suffix}.jpg
 * </pre>
 * Усе, що належить організації, лежить під її префіксом, тож за потреби його можна прибрати разом.
 */
package ua.edu.ukma.community.storage;
