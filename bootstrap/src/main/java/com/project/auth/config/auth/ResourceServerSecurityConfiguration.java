package com.project.auth.config.auth;

import com.project.auth.config.auth.security.KeycloakAuthenticatedUserFactory;
import com.project.auth.config.auth.security.KeycloakGrantedAuthoritiesConverter;
import com.project.auth.config.auth.security.KeycloakJwtAuthenticationConverter;
import com.project.auth.config.auth.security.SecurityActorIdResolver;
import com.project.auth.config.auth.security.SecurityAuditTrailWriter;
import com.project.auth.config.auth.security.SecurityExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Collection;

@Configuration
public class ResourceServerSecurityConfiguration {

    @Bean
    public SecurityActorIdResolver securityActorIdResolver() {
        return new SecurityActorIdResolver();
    }

    @Bean
    public SecurityAuditTrailWriter securityAuditTrailWriter(SecurityActorIdResolver securityActorIdResolver) {
        return new SecurityAuditTrailWriter(securityActorIdResolver);
    }

    @Bean
    public SecurityExceptionHandler securityExceptionHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            SecurityAuditTrailWriter securityAuditTrailWriter
    ) {
        return new SecurityExceptionHandler(handlerExceptionResolver, securityAuditTrailWriter);
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> keycloakGrantedAuthoritiesConverter() {
        return new KeycloakGrantedAuthoritiesConverter();
    }

    @Bean
    public KeycloakAuthenticatedUserFactory keycloakAuthenticatedUserFactory() {
        return new KeycloakAuthenticatedUserFactory();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter(
            Converter<Jwt, Collection<GrantedAuthority>> keycloakGrantedAuthoritiesConverter,
            KeycloakAuthenticatedUserFactory keycloakAuthenticatedUserFactory
    ) {
        return new KeycloakJwtAuthenticationConverter(
                keycloakGrantedAuthoritiesConverter,
                keycloakAuthenticatedUserFactory
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            SecurityExceptionHandler securityExceptionHandler
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/livez",
                                "/readyz",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .build();
    }
}
