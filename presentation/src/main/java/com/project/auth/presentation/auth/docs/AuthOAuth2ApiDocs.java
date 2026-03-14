package com.project.auth.presentation.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

public interface AuthOAuth2ApiDocs {

    @Operation(
            summary = "Google 소셜 로그인 시작",
            description = "Keycloak 로그인 화면으로 이동하되 Google 브로커를 우선 선택하도록 요청합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Keycloak 인증 페이지로 리다이렉트")
    })
    ResponseEntity<Void> loginWithGoogle();

    @Operation(
            summary = "GitHub 소셜 로그인 시작",
            description = "Keycloak 로그인 화면으로 이동하되 GitHub 브로커를 우선 선택하도록 요청합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Keycloak 인증 페이지로 리다이렉트")
    })
    ResponseEntity<Void> loginWithGithub();
}
