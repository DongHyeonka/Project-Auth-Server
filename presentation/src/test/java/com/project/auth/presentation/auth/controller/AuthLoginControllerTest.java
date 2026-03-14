package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.support.code.SuccessCode;
import com.project.auth.presentation.auth.dto.LoginRequest;
import com.project.auth.presentation.auth.mapper.AuthPresentationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLoginControllerTest {

    @Test
    void loginReturnsOkResponse() {
        LoginUseCase loginUseCase = command -> new LoginResult(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                command.email(),
                "테스터",
                "LOCAL",
                "issued-access-token",
                "Bearer",
                1800L,
                Instant.parse("2026-03-14T00:00:00Z"),
                Instant.parse("2026-03-14T00:30:00Z")
        );
        AuthPresentationMapper authPresentationMapper = new AuthPresentationMapper();
        AuthLoginController controller = new AuthLoginController(loginUseCase, authPresentationMapper);

        var response = controller.login(new LoginRequest("tester@example.com", "password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo(SuccessCode.AUTH_LOGIN_SUCCEEDED.code());
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().accessToken()).isEqualTo("issued-access-token");
    }
}
