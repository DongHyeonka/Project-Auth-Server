package com.project.auth.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.oauth2")
public record OAuth2LoginProperties(
        String googleRegistrationId,
        String googleIdpHint,
        String githubRegistrationId,
        String githubIdpHint
) {
}
