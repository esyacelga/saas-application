package com.gymadmin.platform.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(
        @NotBlank(message = "CLOUDINARY_CLOUD_NAME no está configurado") String cloudName,
        @NotBlank(message = "CLOUDINARY_API_KEY no está configurado") String apiKey,
        @NotBlank(message = "CLOUDINARY_API_SECRET no está configurado") String apiSecret
) {
}
