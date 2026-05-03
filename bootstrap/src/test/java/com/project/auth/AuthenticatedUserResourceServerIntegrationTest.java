package com.project.auth;

import com.project.auth.config.auth.security.KeycloakAuthenticatedUserFactory;
import com.project.auth.config.auth.security.KeycloakGrantedAuthoritiesConverter;
import com.project.auth.config.auth.security.KeycloakJwtAuthenticationConverter;
import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;
import com.project.auth.infrastructure.persistence.user.repository.UserJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Import(AuthenticatedUserResourceServerIntegrationTest.TestFlywayConfiguration.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=auth"
})
class AuthenticatedUserResourceServerIntegrationTest {

    private static final String SUBJECT = "keycloak-subject-1";
    private static final String EMAIL = "tester@example.com";
    private static final String NAME = "테스터";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void me_returns_404_without_creating_internal_user_when_subject_is_not_synchronized() throws Exception {
        AbstractAuthenticationToken authentication = authenticationFor(SUBJECT, EMAIL, NAME, List.of("user"));

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH-004"));

        assertThat(userJpaRepository.count()).isZero();
    }

    @Test
    void me_reuses_existing_internal_user_when_subject_already_synchronized() throws Exception {
        UUID existingUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        userJpaRepository.save(UserJpaEntity.of(
                existingUserId,
                EMAIL,
                NAME,
                AuthProvider.KEYCLOAK,
                SUBJECT,
                Instant.parse("2026-04-17T00:00:00Z")
        ));

        AbstractAuthenticationToken authentication = authenticationFor(SUBJECT, "changed@example.com", "변경", List.of("user"));

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(existingUserId.toString()))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value(NAME));

        assertThat(userJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void me_returns_404_when_subject_is_not_found_even_if_same_email_exists() throws Exception {
        userJpaRepository.save(UserJpaEntity.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                EMAIL,
                NAME,
                AuthProvider.KEYCLOAK,
                "other-subject",
                Instant.parse("2026-04-17T00:00:00Z")
        ));

        AbstractAuthenticationToken authentication = authenticationFor(SUBJECT, EMAIL, NAME, List.of("user"));

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH-004"));

        assertThat(userJpaRepository.count()).isEqualTo(1);
    }

    @Test
    void me_returns_401_when_no_authentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    void me_returns_403_when_token_has_no_user_realm_role() throws Exception {
        AbstractAuthenticationToken authentication = authenticationFor(SUBJECT, EMAIL, NAME, List.of("viewer"));

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-002"));
    }

    private static AbstractAuthenticationToken authenticationFor(
            String subject,
            String email,
            String name,
            List<String> realmRoles
    ) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8180/realms/test")
                .subject(subject)
                .issuedAt(Instant.parse("2026-04-17T00:00:00Z"))
                .expiresAt(Instant.parse("2026-04-17T00:30:00Z"))
                .claim("email", email)
                .claim("name", name)
                .claim("realm_access", Map.of("roles", realmRoles))
                .build();

        return new KeycloakJwtAuthenticationConverter(
                new KeycloakGrantedAuthoritiesConverter(),
                new KeycloakAuthenticatedUserFactory()
        ).convert(jwt);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestFlywayConfiguration {

        private static final String MIGRATION_LOCATION = "classpath:db/migration";
        private static final String SCHEMA = "auth";

        @Bean
        Flyway flyway(DataSource dataSource) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations(MIGRATION_LOCATION)
                    .defaultSchema(SCHEMA)
                    .schemas(SCHEMA)
                    .load();
        }

        @Bean("testFlywayMigrationInitializer")
        InitializingBean testFlywayMigrationInitializer(Flyway flyway) {
            return flyway::migrate;
        }

        @Configuration(proxyBeanMethods = false)
        static class FlywayDependsOnPostProcessor extends AbstractDependsOnBeanFactoryPostProcessor {

            FlywayDependsOnPostProcessor() {
                super(EntityManagerFactory.class, "testFlywayMigrationInitializer");
            }
        }
    }
}
