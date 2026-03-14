package com.project.auth.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppDocsProperties.class)
public class OpenApiConfig {

    private final AppDocsProperties appDocsProperties;

    public OpenApiConfig(AppDocsProperties appDocsProperties) {
        this.appDocsProperties = appDocsProperties;
    }

    @Bean
    public OpenAPI authServerOpenApi() {
        return new OpenAPI().info(new Info()
                .title(appDocsProperties.title())
                .version(appDocsProperties.version())
                .description(appDocsProperties.description()));
    }
}
