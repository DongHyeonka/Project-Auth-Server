package com.project.auth.config.openapi;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.docs")
public record AppDocsProperties(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String version
) {
}
