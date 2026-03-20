package com.project.authmigration;

import com.project.auth.config.persistence.MigrationProperties;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(MigrationProperties.class)
@Import(MigrationApplication.MigrationConfiguration.class)
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplicationBuilder builder =
                new SpringApplicationBuilder(MigrationApplication.class);
        builder
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
                )
                .run(args);
    }

    @Configuration
    static class MigrationConfiguration {

        @Bean
        Flyway flyway(DataSource dataSource, MigrationProperties migrationProperties) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations(migrationProperties.location())
                    .load();
        }

        @Bean
        MigrationRunner migrationRunner(
                Flyway flyway,
                MigrationProperties migrationProperties,
                ConfigurableApplicationContext applicationContext
        ) {
            return new MigrationRunner(flyway, migrationProperties, applicationContext);
        }
    }

    static class MigrationRunner {

        private final Flyway flyway;
        private final MigrationProperties migrationProperties;
        private final ConfigurableApplicationContext applicationContext;

        MigrationRunner(
                Flyway flyway,
                MigrationProperties migrationProperties,
                ConfigurableApplicationContext applicationContext
        ) {
            this.flyway = flyway;
            this.migrationProperties = migrationProperties;
            this.applicationContext = applicationContext;
        }

        @PostConstruct
        void runMigration() {
            if (migrationProperties.runOnStartup()) {
                flyway.migrate();
            }

            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }
    }
}
