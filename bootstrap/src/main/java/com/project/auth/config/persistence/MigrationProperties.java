package com.project.auth.config.persistence;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.persistence.migration")
@Validated
public record MigrationProperties(
        @NotBlank String location,
        @NotBlank String schema
) {
}
