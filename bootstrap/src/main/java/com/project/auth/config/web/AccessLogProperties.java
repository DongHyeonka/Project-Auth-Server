package com.project.auth.config.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.logging.access")
public record AccessLogProperties(List<String> excludedPathPrefixes) {
}
