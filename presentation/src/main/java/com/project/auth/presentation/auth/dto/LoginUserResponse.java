package com.project.auth.presentation.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record LoginUserResponse(
        @Schema(description = "회원 식별자", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "회원 이메일", example = "tester@example.com")
        String email,
        @Schema(description = "회원 이름", example = "테스터")
        String name,
        @Schema(description = "가입 방식", example = "LOCAL")
        String provider
) {
}
