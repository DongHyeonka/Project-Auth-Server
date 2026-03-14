package com.project.auth.config.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.persistence.migration")
public record MigrationProperties(boolean runOnStartup, String location) {
}
