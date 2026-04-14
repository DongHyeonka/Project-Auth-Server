package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.presentation.auth.current.CurrentOAuth2User;
import com.project.auth.presentation.auth.current.OAuth2AuthorizationRedirects;
import com.project.auth.presentation.auth.current.OAuth2LoginPrincipal;
import com.project.auth.presentation.auth.docs.AuthOAuth2ApiDocs;
import com.project.auth.presentation.auth.dto.LoginResponse;
import com.project.auth.presentation.auth.mapper.AuthPresentationMapper;
import com.project.auth.presentation.auth.mapper.OAuth2AuthenticationCommandMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import com.project.auth.presentation.support.response.ApiSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final OAuth2AuthorizationRedirects oAuth2AuthorizationRedirects;
    private final ApiResultFactory apiResultFactory;

    public AuthOAuth2Controller(
            OAuthLoginUseCase oAuthLoginUseCase,
            AuthPresentationMapper authPresentationMapper,
            OAuth2AuthenticationCommandMapper oAuth2AuthenticationCommandMapper,
            OAuth2AuthorizationRedirects oAuth2AuthorizationRedirects,
            ApiResultFactory apiResultFactory
    ) {
        this.oAuthLoginUseCase = oAuthLoginUseCase;
        this.authPresentationMapper = authPresentationMapper;
        this.oAuth2AuthenticationCommandMapper = oAuth2AuthenticationCommandMapper;
        this.oAuth2AuthorizationRedirects = oAuth2AuthorizationRedirects;
        this.apiResultFactory = apiResultFactory;
    }

    @Override
    @GetMapping("/keycloak/google")
    public ResponseEntity<Void> loginWithGoogle() {
        return redirect(oAuth2AuthorizationRedirects.googleAuthorizationPath());
    }

    @Override
    @GetMapping("/keycloak/github")
    public ResponseEntity<Void> loginWithGithub() {
        return redirect(oAuth2AuthorizationRedirects.githubAuthorizationPath());
    }

    @Operation(hidden = true)
    @GetMapping("/complete")
    public ResponseEntity<ApiResult<LoginResponse>> completeOAuthLogin(
            @CurrentOAuth2User OAuth2LoginPrincipal currentUser
    ) {
        LoginResult loginResult = oAuthLoginUseCase.login(
                oAuth2AuthenticationCommandMapper.toCommand(currentUser)
        );
        LoginResponse response = authPresentationMapper.toResponse(loginResult);

        return ResponseEntity.ok(apiResultFactory.success(LOGIN_SUCCEEDED.code(), LOGIN_SUCCEEDED.message(), response));
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(path).toString())
                .build();
    }
}
