package com.project.auth.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.config.auth.security.KeycloakIdpHintAuthorizationRequestResolver;
import com.project.auth.config.auth.security.OAuth2LoginFailureHandler;
import com.project.auth.config.auth.security.OAuth2LoginSuccessHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(OAuth2LoginProperties.class)
public class OAuth2SecurityConfiguration {

    @Bean
    public OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler(OAuth2LoginProperties oAuth2LoginProperties) {
        return new OAuth2LoginSuccessHandler(oAuth2LoginProperties);
    }

    @Bean
    public OAuth2LoginFailureHandler oAuth2LoginFailureHandler(ObjectMapper objectMapper) {
        return new OAuth2LoginFailureHandler(objectMapper);
    }

    @Bean
    public KeycloakIdpHintAuthorizationRequestResolver keycloakIdpHintAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2LoginProperties oAuth2LoginProperties
    ) {
        return new KeycloakIdpHintAuthorizationRequestResolver(clientRegistrationRepository, oAuth2LoginProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            KeycloakIdpHintAuthorizationRequestResolver keycloakIdpHintAuthorizationRequestResolver,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/login",
                                "/auth-login.html",
                                "/.well-known/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/v1/users/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/oauth2/keycloak/**",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(keycloakIdpHintAuthorizationRequestResolver)
                        )
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                .build();
    }
}
