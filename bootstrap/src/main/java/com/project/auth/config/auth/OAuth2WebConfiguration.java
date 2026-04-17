package com.project.auth.config.auth;

import com.project.auth.config.auth.security.ConfiguredOAuth2AuthorizationRedirects;
import com.project.auth.config.auth.security.OAuth2LoginPrincipalArgumentResolver;
import com.project.auth.config.auth.security.OAuth2RegistrationProviderMapper;
import com.project.auth.presentation.auth.current.OAuth2AuthorizationRedirects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties(OAuth2LoginProperties.class)
public class OAuth2WebConfiguration implements WebMvcConfigurer {

    private final OAuth2LoginProperties properties;

    public OAuth2WebConfiguration(OAuth2LoginProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OAuth2AuthorizationRedirects oAuth2AuthorizationRedirects(OAuth2LoginProperties properties) {
        return new ConfiguredOAuth2AuthorizationRedirects(properties);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new OAuth2LoginPrincipalArgumentResolver(new OAuth2RegistrationProviderMapper(properties)));
    }
}
