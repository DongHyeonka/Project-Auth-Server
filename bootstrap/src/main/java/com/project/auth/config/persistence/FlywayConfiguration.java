package com.project.auth.config.persistence;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(MigrationProperties.class)
public class FlywayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfiguration.class);

    @Bean
    public Flyway flyway(DataSource dataSource, MigrationProperties migrationProperties) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationProperties.location())
                .load();
    }

    @Bean("flywayMigrationInitializer")
    public InitializingBean flywayMigrationInitializer(Flyway flyway, MigrationProperties migrationProperties) {
        return () -> {
            if (migrationProperties.runOnStartup()) {
                log.info("Flyway migration enabled on startup: location={}", migrationProperties.location());
                flyway.migrate();
                log.info("Flyway migration completed successfully");
            } else {
                log.info("Flyway migration skipped: runOnStartup=false");
            }
        };
    }

    @Configuration
    static class FlywayDependsOnPostProcessor extends AbstractDependsOnBeanFactoryPostProcessor {

        FlywayDependsOnPostProcessor() {
            super(EntityManagerFactory.class, "flywayMigrationInitializer");
        }
    }
}
