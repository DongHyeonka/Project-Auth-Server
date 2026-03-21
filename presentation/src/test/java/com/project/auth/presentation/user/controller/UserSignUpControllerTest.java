package com.project.auth.presentation.user.controller;

import com.project.auth.application.user.signup.SignUpResult;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.presentation.support.response.ApiSuccessCode;
import com.project.auth.presentation.user.dto.SignUpRequest;
import com.project.auth.presentation.user.mapper.UserPresentationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserSignUpControllerTest {

    @Test
    void signUpReturnsCreatedResponse() {
        SignUpUseCase signUpUseCase = command -> new SignUpResult(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                command.email(),
                command.name(),
                "LOCAL",
                Instant.parse("2026-03-13T00:00:00Z")
        );
        UserPresentationMapper userPresentationMapper = new UserPresentationMapper();
        UserSignUpController controller = new UserSignUpController(signUpUseCase, userPresentationMapper);

        var response = controller.signUp(new SignUpRequest("tester@example.com", "password123", "테스터"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo(ApiSuccessCode.USER_SIGNED_UP.code());
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().email()).isEqualTo("tester@example.com");
    }
}
