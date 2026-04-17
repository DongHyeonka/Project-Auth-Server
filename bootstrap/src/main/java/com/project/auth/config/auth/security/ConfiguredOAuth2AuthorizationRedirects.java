package com.project.auth.config.auth.security;

import com.project.auth.config.auth.OAuth2LoginProperties;
import com.project.auth.presentation.auth.current.OAuth2AuthorizationRedirects;

import java.util.Objects;

public class ConfiguredOAuth2AuthorizationRedirects implements OAuth2AuthorizationRedirects {

    private static final String AUTHORIZATION_PATH_PREFIX = "/oauth2/authorization/";

    private final OAuth2LoginProperties properties;

    public ConfiguredOAuth2AuthorizationRedirects(OAuth2LoginProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String googleAuthorizationPath() {
        return authorizationPath(properties.googleRegistrationId());
    }

    @Override
    public String githubAuthorizationPath() {
        return authorizationPath(properties.githubRegistrationId());
    }

    private static String authorizationPath(String registrationId) {
        return AUTHORIZATION_PATH_PREFIX + registrationId;
    }
}
