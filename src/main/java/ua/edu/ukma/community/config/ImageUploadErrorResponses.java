package ua.edu.ukma.community.config;

import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Спільні відповіді-помилки для завантаження картинки (multipart, поле {@code file}) у Supabase Storage:
 * логотип організації і картинка події. Успішну відповідь і помилки, специфічні для ресурсу
 * (наприклад, 409 для відхиленої події), метод оголошує сам.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(responseCode = "400", ref = OpenApiConfig.BAD_REQUEST)
@ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
@ApiResponse(responseCode = "403", ref = OpenApiConfig.FORBIDDEN)
@ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
@ApiResponse(responseCode = "413", ref = OpenApiConfig.PAYLOAD_TOO_LARGE)
@ApiResponse(responseCode = "415", ref = OpenApiConfig.UNSUPPORTED_MEDIA_TYPE)
@ApiResponse(responseCode = "503", ref = OpenApiConfig.STORAGE_UNAVAILABLE)
public @interface ImageUploadErrorResponses {
}
