package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.LoadedKeycloakUser;
import com.project.auth.application.auth.identity.LoadKeycloakUserUseCase;
import com.project.auth.presentation.auth.current.AuthenticatedUser;
import com.project.auth.presentation.auth.current.CurrentUser;
import com.project.auth.presentation.auth.docs.AuthenticatedUserApiDocs;
import com.project.auth.presentation.auth.dto.AuthenticatedUserResponse;
import com.project.auth.presentation.auth.mapper.AuthenticatedUserPresentationMapper;
import com.project.auth.presentation.support.response.ApiResult;
import com.project.auth.presentation.support.response.ApiResultFactory;
import com.project.auth.presentation.support.response.ApiSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Keycloak 토큰 기반 인증 API")
public class AuthenticatedUserController implements AuthenticatedUserApiDocs {

    private static final ApiSuccessCode USER_LOADED = ApiSuccessCode.AUTHENTICATED_USER_LOADED;

    private final LoadKeycloakUserUseCase loadKeycloakUserUseCase;
    private final AuthenticatedUserPresentationMapper authenticatedUserPresentationMapper;
    private final ApiResultFactory apiResultFactory;

    public AuthenticatedUserController(
            LoadKeycloakUserUseCase loadKeycloakUserUseCase,
            AuthenticatedUserPresentationMapper authenticatedUserPresentationMapper,
            ApiResultFactory apiResultFactory
    ) {
        this.loadKeycloakUserUseCase = loadKeycloakUserUseCase;
        this.authenticatedUserPresentationMapper = authenticatedUserPresentationMapper;
        this.apiResultFactory = apiResultFactory;
    }

    @Override
    @GetMapping("/me")
    public ApiResult<AuthenticatedUserResponse> me(@CurrentUser AuthenticatedUser currentUser) {
        LoadedKeycloakUser user = loadKeycloakUserUseCase.load(new KeycloakUserClaims(
                currentUser.subject(),
                currentUser.email(),
                currentUser.name()
        ));

        return apiResultFactory.success(
                USER_LOADED.code(),
                USER_LOADED.message(),
                authenticatedUserPresentationMapper.toResponse(user, currentUser.authorities())
        );
    }
}
