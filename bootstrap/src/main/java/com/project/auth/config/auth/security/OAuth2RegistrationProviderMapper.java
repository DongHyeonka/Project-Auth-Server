package com.project.auth.config.auth.security;

import com.project.auth.application.auth.exception.InvalidOAuthUserInfoException;
import com.project.auth.application.auth.exception.UnsupportedOAuthProviderException;
import com.project.auth.config.auth.OAuth2LoginProperties;
import com.project.auth.domain.user.model.AuthProvider;

import java.util.Map;
import java.util.Objects;

public class OAuth2RegistrationProviderMapper {

    private final Map<String, AuthProvider> providersByRegistrationId;

    public OAuth2RegistrationProviderMapper(OAuth2LoginProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.providersByRegistrationId = Map.of(
                normalize(properties.googleRegistrationId()), AuthProvider.GOOGLE,
                normalize(properties.githubRegistrationId()), AuthProvider.GITHUB
        );
    }

    public String providerName(String registrationId) {
        AuthProvider provider = providersByRegistrationId.get(normalize(registrationId));
        if (provider == null) {
            throw new UnsupportedOAuthProviderException();
        }
        return provider.name();
    }

    private static String normalize(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new InvalidOAuthUserInfoException();
        }
        return registrationId.trim();
    }
}
