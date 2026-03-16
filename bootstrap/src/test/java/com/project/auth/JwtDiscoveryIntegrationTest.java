package com.project.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtDiscoveryIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void openIdConfigurationExposesIssuerAndJwkSetUri() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGet("/.well-known/openid-configuration");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.path("issuer").asText()).isEqualTo("http://localhost:8080");
        assertThat(body.path("jwks_uri").asText()).isEqualTo("http://localhost:8080/.well-known/jwks.json");
        assertThat(body.path("id_token_signing_alg_values_supported").toString()).contains("RS256");
    }

    @Test
    void jwkSetExposesPublicRsaKey() throws IOException, InterruptedException {
        HttpResponse<String> response = sendGet("/.well-known/jwks.json");
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.path("keys").size()).isEqualTo(1);
        assertThat(body.path("keys").get(0).path("kty").asText()).isEqualTo("RSA");
        assertThat(body.path("keys").get(0).path("kid").asText()).isNotBlank();
        assertThat(body.path("keys").get(0).has("d")).isFalse();
    }

    private HttpResponse<String> sendGet(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
