package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.presentation.auth.docs.AuthLoginApiDocs;
import com.project.auth.presentation.auth.dto.LoginRequest;
import com.project.auth.presentation.auth.dto.LoginResponse;
import com.project.auth.presentation.auth.mapper.AuthPresentationMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "로그인과 토큰 발급 API")
public class AuthLoginController implements AuthLoginApiDocs {

    private static final ApiSuccessCode LOGIN_SUCCEEDED = ApiSuccessCode.AUTH_LOGIN_SUCCEEDED;

    private final LoginUseCase loginUseCase;
    private final AuthPresentationMapper authPresentationMapper;

    public AuthLoginController(LoginUseCase loginUseCase, AuthPresentationMapper authPresentationMapper) {
        this.loginUseCase = loginUseCase;
        this.authPresentationMapper = authPresentationMapper;
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult loginResult = loginUseCase.login(authPresentationMapper.toCommand(request));
        LoginResponse response = authPresentationMapper.toResponse(loginResult);

        return ResponseEntity.ok(ApiResult.success(LOGIN_SUCCEEDED.code(), LOGIN_SUCCEEDED.message(), response));
    }
}
