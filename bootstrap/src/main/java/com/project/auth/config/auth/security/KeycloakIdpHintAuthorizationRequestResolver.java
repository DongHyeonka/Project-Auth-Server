package com.project.auth.config.auth.security;

import com.project.auth.config.auth.OAuth2LoginProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

public class KeycloakIdpHintAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";
    private static final String KEYCLOAK_IDP_HINT_PARAMETER = "kc_idp_hint";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final OAuth2LoginProperties oAuth2LoginProperties;

    public KeycloakIdpHintAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2LoginProperties oAuth2LoginProperties
    ) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                AUTHORIZATION_REQUEST_BASE_URI
        );
        this.oAuth2LoginProperties = oAuth2LoginProperties;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = extractRegistrationId(request.getRequestURI());
        return customize(delegate.resolve(request), registrationId);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId), clientRegistrationId);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authorizationRequest, String registrationId) {
        if (authorizationRequest == null) {
            return null;
        }

        String idpHint = resolveIdpHint(registrationId);
        if (idpHint == null) {
            return authorizationRequest;
        }

        Map<String, Object> additionalParameters = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
        additionalParameters.put(KEYCLOAK_IDP_HINT_PARAMETER, idpHint);

        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(additionalParameters)
                .build();
    }

    private String resolveIdpHint(String registrationId) {
        if (oAuth2LoginProperties.googleRegistrationId().equals(registrationId)) {
            return oAuth2LoginProperties.googleIdpHint();
        }

        if (oAuth2LoginProperties.githubRegistrationId().equals(registrationId)) {
            return oAuth2LoginProperties.githubIdpHint();
        }

        return null;
    }

    private String extractRegistrationId(String requestUri) {
        int lastSlashIndex = requestUri.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex == requestUri.length() - 1) {
            return "";
        }

        return requestUri.substring(lastSlashIndex + 1);
    }
}
