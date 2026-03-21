package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.presentation.auth.docs.AuthOAuth2ApiDocs;
import com.project.auth.presentation.auth.dto.LoginResponse;
import com.project.auth.presentation.auth.mapper.AuthPresentationMapper;
import com.project.auth.presentation.auth.mapper.OAuth2AuthenticationCommandMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@Tag(name = "Auth", description = "OAuth2 소셜 로그인 API")
public class AuthOAuth2Controller implements AuthOAuth2ApiDocs {

    private static final ApiSuccessCode LOGIN_SUCCEEDED = ApiSuccessCode.AUTH_LOGIN_SUCCEEDED;

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final AuthPresentationMapper authPresentationMapper;
    private final OAuth2AuthenticationCommandMapper oAuth2AuthenticationCommandMapper;

    public AuthOAuth2Controller(
            OAuthLoginUseCase oAuthLoginUseCase,
            AuthPresentationMapper authPresentationMapper,
            OAuth2AuthenticationCommandMapper oAuth2AuthenticationCommandMapper
    ) {
        this.oAuthLoginUseCase = oAuthLoginUseCase;
        this.authPresentationMapper = authPresentationMapper;
        this.oAuth2AuthenticationCommandMapper = oAuth2AuthenticationCommandMapper;
    }

    @Override
    @GetMapping("/keycloak/google")
    public ResponseEntity<Void> loginWithGoogle() {
        return redirect(AuthOAuth2RoutePaths.GOOGLE_AUTHORIZATION_PATH);
    }

    @Override
    @GetMapping("/keycloak/github")
    public ResponseEntity<Void> loginWithGithub() {
        return redirect(AuthOAuth2RoutePaths.GITHUB_AUTHORIZATION_PATH);
    }

    @Operation(hidden = true)
    @GetMapping("/complete")
    public ResponseEntity<ApiResult<LoginResponse>> completeOAuthLogin(
            @RequestParam("provider") String provider,
            Authentication authentication
    ) {
        LoginResult loginResult = oAuthLoginUseCase.login(
                oAuth2AuthenticationCommandMapper.toCommand(provider, authentication)
        );
        LoginResponse response = authPresentationMapper.toResponse(loginResult);

        return ResponseEntity.ok(ApiResult.success(LOGIN_SUCCEEDED.code(), LOGIN_SUCCEEDED.message(), response));
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(path).toString())
                .build();
    }
}
