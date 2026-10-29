package ua.edu.ukma.community.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.edu.ukma.community.common.error.ErrorResponse;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;


@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String UNAUTHORIZED = "#/components/responses/Unauthorized";
    public static final String FORBIDDEN = "#/components/responses/Forbidden";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";
    public static final String PAYLOAD_TOO_LARGE = "#/components/responses/PayloadTooLarge";
    public static final String UNSUPPORTED_MEDIA_TYPE = "#/components/responses/UnsupportedMediaType";
    public static final String STORAGE_UNAVAILABLE = "#/components/responses/StorageUnavailable";
    public static final String MAIL_UNAVAILABLE = "#/components/responses/MailUnavailable";
    public static final String TOO_MANY_REQUESTS = "#/components/responses/TooManyRequests";

    @Bean
    OpenAPI communityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Student Community & Events API")
                        .version("1.0.0")
                        .description("""
                                API мікросервісу «Студентське ком'юніті та Івенти» (екосистема SmartUKMA): \
                                каталог студентських організацій. Специфікація згенерована з коду (Code-First). \
                                Відповіді — заглушки з пам'яті процесу.""")
                        .contact(new Contact().name("Команда Student Community")))
                .tags(List.of(
                        new Tag().name("Auth")
                                .description("Реєстрація з поштою @ukma.edu.ua, вхід, поточний користувач"),
                        new Tag().name("Organizations").description("Каталог студентських організацій"),
                        new Tag().name("Heads").description("Голови організацій і журнал змін ролей")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT з POST /api/v1/auth/login, діє 60 хв")));
    }

    @Bean
    OpenApiCustomizer sharedErrorResponses() {
        return openApi -> {
            Components components = openApi.getComponents() != null ? openApi.getComponents() : new Components();
            openApi.setComponents(components);
            ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);

            components.addResponses("BadRequest", errorResponse(
                    "Невалідні параметри або тіло запиту (VALIDATION_ERROR, INVALID_PARAMETER, MALFORMED_REQUEST)",
                    example("VALIDATION_ERROR", "Запит містить невалідні поля",
                            List.of(Map.of("field", "slug",
                                    "message", "Slug: 3–50 символів, лише a-z, 0-9 і дефіс",
                                    "providedValue", "Debate Club!")))));
            components.addResponses("Unauthorized", errorResponse(
                    "Відсутній або недійсний JWT (UNAUTHORIZED)",
                    example("UNAUTHORIZED", "Потрібна автентифікація", List.of())));
            components.addResponses("Forbidden", errorResponse(
                    "Роль користувача не дозволяє цю дію (FORBIDDEN)",
                    example("FORBIDDEN", "Дія доступна лише голові цієї організації", List.of())));
            components.addResponses("NotFound", errorResponse(
                    "Ресурс не знайдено (ORGANIZATION_NOT_FOUND)",
                    example("ORGANIZATION_NOT_FOUND", "Організацію не знайдено", List.of())));
            components.addResponses("Conflict", errorResponse(
                    "Конфлікт зі станом ресурсу (SLUG_ALREADY_EXISTS)",
                    example("SLUG_ALREADY_EXISTS", "Організація з таким slug уже існує",
                            List.of(Map.of("field", "slug", "message", "Slug зайнятий", "providedValue", "debate-club")))));
            components.addResponses("PayloadTooLarge", errorResponse(
                    "Файл більший за 5 МБ (PAYLOAD_TOO_LARGE)",
                    example("PAYLOAD_TOO_LARGE", "Файл завеликий: не більше 5 МБ", List.of())));
            components.addResponses("UnsupportedMediaType", errorResponse(
                    "Файл не є картинкою JPEG, PNG чи WebP або запит не multipart/form-data (UNSUPPORTED_MEDIA_TYPE)",
                    example("UNSUPPORTED_MEDIA_TYPE", "Підтримуються лише картинки JPEG, PNG і WebP",
                            List.of(Map.of("field", "file", "message", "Невідомий формат файлу",
                                    "providedValue", "logo.svg")))));
            components.addResponses("MailUnavailable", errorResponse(
                    "Не вдалося надіслати лист з кодом (MAIL_UNAVAILABLE)",
                    example("MAIL_UNAVAILABLE", "Не вдалося надіслати лист з кодом", List.of())));
            components.addResponses("TooManyRequests", errorResponse(
                    "Забагато запитів, спробуйте пізніше (TOO_MANY_REQUESTS)",
                    example("TOO_MANY_REQUESTS", "Новий код можна запросити через 42 с", List.of())));
            components.addResponses("StorageUnavailable", errorResponse(
                    "Файлове сховище недоступне або не налаштоване (STORAGE_UNAVAILABLE)",
                    example("STORAGE_UNAVAILABLE", "Не вдалося зберегти файл у сховищі", List.of())));
        };
    }

    private static ApiResponse errorResponse(String description, Object example) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                        .example(example)));
    }

    private static Map<String, Object> example(String code, String message, List<Map<String, Object>> details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", details);
        error.put("requestId", "req_7f3a9c21d04b4e18");
        error.put("documentation", "https://ukma-fin-ssdm-2026.github.io/YOUR-REPO/api/errors#"
                + code.toLowerCase(Locale.ROOT).replace('_', '-'));
        return Map.of("error", error);
    }
}
