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
                        new Tag().name("Organizations").description("Каталог студентських організацій")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT власного AuthZ-сервісу (заплановано; у заглушках не перевіряється)")));
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
                + code.toLowerCase().replace('_', '-'));
        return Map.of("error", error);
    }
}
