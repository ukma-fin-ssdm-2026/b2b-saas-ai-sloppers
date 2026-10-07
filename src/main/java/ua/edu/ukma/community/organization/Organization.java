package ua.edu.ukma.community.organization;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Student organization")
public record Organization(
        @Schema(description = "ID", example = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Name", example = "FIdo", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Unique readable ID for URL", example = "fido",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,

        @Schema(description = "Activity description", example = "Жоско прогаєм")
        String description,

        @Schema(description = "Instagram-account without @", example = "fido_naukma")
        String instagramHandle,

        @Schema(description = "Post", example = "fido@ukma.edu.ua")
        String contactEmail,

        @Schema(description = "Час створення (ISO 8601)", example = "2026-09-01T10:00:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime createdAt,

        @Schema(description = "Час останньої зміни (ISO 8601)", example = "2026-09-15T12:30:00+03:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime updatedAt
) {
}

