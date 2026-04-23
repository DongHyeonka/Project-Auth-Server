package com.project.auth.presentation.auth.controller;

import com.project.auth.application.auth.identity.KeycloakUserClaims;
import com.project.auth.application.auth.identity.SyncedKeycloakUser;
import com.project.auth.application.auth.identity.SyncKeycloakUserUseCase;
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

    private final SyncKeycloakUserUseCase syncKeycloakUserUseCase;
    private final AuthenticatedUserPresentationMapper authenticatedUserPresentationMapper;
    private final ApiResultFactory apiResultFactory;

    public AuthenticatedUserController(
            SyncKeycloakUserUseCase syncKeycloakUserUseCase,
            AuthenticatedUserPresentationMapper authenticatedUserPresentationMapper,
            ApiResultFactory apiResultFactory
    ) {
        this.syncKeycloakUserUseCase = syncKeycloakUserUseCase;
        this.authenticatedUserPresentationMapper = authenticatedUserPresentationMapper;
        this.apiResultFactory = apiResultFactory;
    }

    @Override
    @GetMapping("/me")
    public ApiResult<AuthenticatedUserResponse> me(@CurrentUser AuthenticatedUser currentUser) {
        SyncedKeycloakUser user = syncKeycloakUserUseCase.sync(new KeycloakUserClaims(
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
