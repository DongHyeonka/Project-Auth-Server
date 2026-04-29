package com.project.authmigration;

import com.project.auth.config.persistence.MigrationProperties;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(MigrationProperties.class)
@Import(MigrationApplication.MigrationConfiguration.class)
public class MigrationApplication {

    private static final String AUTOCONFIGURATION_EXCLUSIONS = String.join(
            ",",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
    );

    public static void main(String[] args) {
        SpringApplicationBuilder builder =
                new SpringApplicationBuilder(MigrationApplication.class);
        builder
                .web(WebApplicationType.NONE)
                .properties("spring.autoconfigure.exclude=" + AUTOCONFIGURATION_EXCLUSIONS)
                .run(args);
    }

    @Configuration(proxyBeanMethods = false)
    static class MigrationConfiguration {

        private static final Logger log = LoggerFactory.getLogger(MigrationConfiguration.class);

        @Bean
        Flyway flyway(DataSource dataSource, MigrationProperties migrationProperties) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations(migrationProperties.location())
                    .defaultSchema(migrationProperties.schema())
                    .schemas(migrationProperties.schema())
                    .load();
        }

        @Bean
        ApplicationRunner migrationRunner(
                Flyway flyway,
                MigrationProperties migrationProperties,
                ConfigurableApplicationContext applicationContext
        ) {
            return args -> {
                log.info(
                        "Flyway migration app started: location={} schema={}",
                        migrationProperties.location(),
                        migrationProperties.schema()
                );
                flyway.migrate();
                log.info("Flyway migration app completed successfully");

                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
            };
        }
    }
}
