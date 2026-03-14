package com.project.auth.config.persistence;

import org.flywaydb.core.Flyway;
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
                flyway.migrate();
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
