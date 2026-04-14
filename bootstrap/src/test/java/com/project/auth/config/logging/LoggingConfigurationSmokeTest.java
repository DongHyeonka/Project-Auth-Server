package com.project.auth.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationSmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void prodStructuredLogstashEncoderWritesMdcAndEventFieldsAsTopLevelJsonMembers() throws Exception {
        LoggerContext loggerContext = new LoggerContext();
        loggerContext.putObject(Environment.class.getName(), new MockEnvironment());
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(loggerContext);
        encoder.setFormat("logstash");
        encoder.start();

        try {
            LoggingEvent event = new LoggingEvent();
            event.setLoggerContext(loggerContext);
            event.setInstant(Instant.parse("2026-04-12T00:00:00Z"));
            event.setLoggerName("http.access");
            event.setThreadName("test-thread");
            event.setLevel(Level.INFO);
            event.setMessage("ACCESS");
            event.setMDCPropertyMap(Map.of(
                    "traceId", "0123456789abcdef0123456789abcdef",
                    "clientIp", "10.0.0.0",
                    "userAgent", "JUnit_5"
            ));
            event.setKeyValuePairs(List.of(
                    new KeyValuePair("eventType", "HTTP_ACCESS"),
                    new KeyValuePair("status", 503),
                    new KeyValuePair("durationMs", 42L),
                    new KeyValuePair("actorId", "al***@example.com"),
                    new KeyValuePair("result", "failure")
            ));

            String json = new String(encoder.encode(event), StandardCharsets.UTF_8);
            JsonNode root = OBJECT_MAPPER.readTree(json);

            assertThat(root.path("message").asText()).isEqualTo("ACCESS");
            assertThat(root.path("traceId").asText()).isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(root.path("clientIp").asText()).isEqualTo("10.0.0.0");
            assertThat(root.path("userAgent").asText()).isEqualTo("JUnit_5");
            assertThat(root.path("eventType").asText()).isEqualTo("HTTP_ACCESS");
            assertThat(root.path("status").asInt()).isEqualTo(503);
            assertThat(root.path("durationMs").asLong()).isEqualTo(42L);
            assertThat(root.path("actorId").asText()).isEqualTo("al***@example.com");
            assertThat(root.path("result").asText()).isEqualTo("failure");
            assertThat(root.path("message").asText())
                    .doesNotContain("status=")
                    .doesNotContain("actorId=")
                    .doesNotContain("traceId=");
        } finally {
            encoder.stop();
            loggerContext.stop();
        }
    }

    @Test
    void logbackRoutesStructuredConsoleForProdAndMakesAuditFileOptIn() throws Exception {
        String logback = mainResource("logback-spring.xml").getContentAsString(StandardCharsets.UTF_8);

        assertThat(logback)
                .contains("<springProfile name=\"prod\">")
                .contains("org/springframework/boot/logging/logback/structured-console-appender.xml")
                .contains("%kvp")
                .contains("<springProfile name=\"!audit-file\">")
                .contains("<springProfile name=\"audit-file\">")
                .contains("<appender name=\"AUDIT_FILE\"")
                .contains("<appender-ref ref=\"AUDIT_FILE\"/>");
    }

    @Test
    void profileYamlLocksStructuredLoggingProxyTrustAndDevActuatorContract() throws Exception {
        PropertySource<?> base = yaml("application.yml");
        PropertySource<?> prod = yaml("application-prod.yml");
        PropertySource<?> dev = yaml("application-dev.yml");

        assertThat(base.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(base.getProperty("server.tomcat.remoteip.internal-proxies").toString())
                .contains("SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES")
                .contains("10\\.")
                .contains("192\\.168")
                .contains("172\\.");

        assertThat(prod.getProperty("logging.structured.format.console")).isEqualTo("logstash");

        assertThat(dev.getProperty("management.server.address")).isEqualTo("127.0.0.1");
        assertThat(dev.getProperty("management.endpoints.web.exposure.include").toString())
                .contains("health")
                .contains("loggers")
                .contains("httpexchanges")
                .doesNotContain("env");
    }

    private static PropertySource<?> yaml(String location) throws Exception {
        return new YamlPropertySourceLoader()
                .load(location, mainResource(location))
                .getFirst();
    }

    private static Resource mainResource(String location) {
        Path modulePath = Path.of("src/main/resources", location);
        if (Files.exists(modulePath)) {
            return new FileSystemResource(modulePath);
        }
        return new FileSystemResource(Path.of("bootstrap/src/main/resources", location));
    }
}
