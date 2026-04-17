package com.project.auth.config.auth.security;

import com.project.auth.application.auth.exception.InvalidOAuthUserInfoException;
import com.project.auth.presentation.auth.current.CurrentOAuth2User;
import com.project.auth.presentation.auth.current.OAuth2LoginPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;
import java.util.Objects;

public class OAuth2LoginPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    private final OAuth2RegistrationProviderMapper providerMapper;

    public OAuth2LoginPrincipalArgumentResolver(OAuth2RegistrationProviderMapper providerMapper) {
        this.providerMapper = Objects.requireNonNull(providerMapper, "providerMapper must not be null");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOAuth2User.class)
                && OAuth2LoginPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Principal principal = webRequest.getUserPrincipal();
        if (!(principal instanceof Authentication authentication)) {
            throw new InvalidOAuthUserInfoException();
        }

        OAuth2AuthenticationToken authenticationToken = requireAuthenticationToken(authentication);
        OidcUser oidcUser = requireOidcUser(authenticationToken.getPrincipal());

        return new OAuth2LoginPrincipal(
                providerMapper.providerName(authenticationToken.getAuthorizedClientRegistrationId()),
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                resolveUserName(oidcUser)
        );
    }

    private static OAuth2AuthenticationToken requireAuthenticationToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken authenticationToken) {
            return authenticationToken;
        }
        throw new InvalidOAuthUserInfoException();
    }

    private static OidcUser requireOidcUser(Object principal) {
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser;
        }
        throw new InvalidOAuthUserInfoException();
    }

    private static String resolveUserName(OidcUser oidcUser) {
        String name = oidcUser.getFullName();
        return (name != null && !name.isBlank()) ? name : oidcUser.getPreferredUsername();
    }
}
