package com.project.auth.presentation.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "로그인한 회원 정보")
        LoginUserResponse user,
        @Schema(description = "auth-server가 발급한 토큰 정보")
        LoginTokenResponse token
) {
}
