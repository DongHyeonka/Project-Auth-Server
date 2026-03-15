package com.project.auth;

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
class LoginPageIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void loginPageIsExposedByAuthServer() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/login"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("로그인은 이 서버에서 시작됩니다.");
        assertThat(response.body()).contains("/api/v1/auth/login");
        assertThat(response.body()).contains("/api/v1/auth/oauth2/keycloak/google");
        assertThat(response.body()).contains("/api/v1/auth/oauth2/keycloak/github");
    }
}
