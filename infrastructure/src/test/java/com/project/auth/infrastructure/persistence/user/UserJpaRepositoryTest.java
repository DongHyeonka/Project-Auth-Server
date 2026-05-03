package com.project.auth.infrastructure.persistence.user;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;
import com.project.auth.infrastructure.persistence.user.mapper.UserPersistenceMapper;
import com.project.auth.infrastructure.persistence.user.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = {
        UserJpaRepositoryTest.TestApplication.class,
        UserJpaRepositoryTest.TestFlywayConfiguration.class
})
@Testcontainers
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=auth"
})
class UserJpaRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private JpaUserRepositoryAdapter adapter;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void setUp() {
        adapter = new JpaUserRepositoryAdapter(userJpaRepository, new UserPersistenceMapper());
    }

    @Test
    void savePersistsKeycloakUserAndReloadsFromDatabase() {
        User user = keycloakUser(
                "11111111-1111-1111-1111-111111111111",
                "tester@example.com",
                "keycloak-subject-1"
        );

        adapter.save(user);
        entityManager.flush();
        entityManager.clear();

        assertThat(userJpaRepository.findByProviderAndProviderSubject(AuthProvider.KEYCLOAK, "keycloak-subject-1"))
                .isPresent()
                .get()
                .satisfies(entity -> {
                    assertThat(entity.getEmail()).isEqualTo("tester@example.com");
                    assertThat(entity.getProvider()).isEqualTo(AuthProvider.KEYCLOAK);
                    assertThat(entity.getCreatedAt()).isEqualTo(Instant.parse("2026-04-17T00:00:00Z"));
                });
    }

    @Test
    void saveRejectsDuplicateEmailOnFlush() {
        userJpaRepository.save(keycloakEntity(
                "11111111-1111-1111-1111-111111111111",
                "duplicate@example.com",
                "keycloak-subject-1"
        ));
        userJpaRepository.flush();
        entityManager.clear();

        userJpaRepository.save(keycloakEntity(
                "22222222-2222-2222-2222-222222222222",
                "duplicate@example.com",
                "keycloak-subject-2"
        ));

        assertThatThrownBy(() -> userJpaRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveRejectsDuplicateProviderSubjectOnFlush() {
        userJpaRepository.save(keycloakEntity(
                "11111111-1111-1111-1111-111111111111",
                "first@example.com",
                "keycloak-subject-1"
        ));
        userJpaRepository.flush();
        entityManager.clear();

        userJpaRepository.save(keycloakEntity(
                "22222222-2222-2222-2222-222222222222",
                "second@example.com",
                "keycloak-subject-1"
        ));

        assertThatThrownBy(() -> userJpaRepository.flush())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static User keycloakUser(String id, String email, String providerSubject) {
        return User.registerKeycloak(
                UUID.fromString(id),
                UserEmail.from(email),
                UserName.from("테스터"),
                providerSubject,
                Instant.parse("2026-04-17T00:00:00Z")
        );
    }

    private static UserJpaEntity keycloakEntity(String id, String email, String providerSubject) {
        return UserJpaEntity.of(
                UUID.fromString(id),
                email,
                "테스터",
                AuthProvider.KEYCLOAK,
                providerSubject,
                Instant.parse("2026-04-17T00:00:00Z")
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = UserJpaRepository.class)
    static class TestApplication {
    }

    @Configuration(proxyBeanMethods = false)
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
