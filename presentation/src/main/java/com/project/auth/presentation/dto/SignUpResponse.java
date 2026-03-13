package com.project.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record SignUpResponse(
        @Schema(description = "회원 식별자", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "회원 이메일", example = "tester@example.com")
        String email,
        @Schema(description = "회원 이름", example = "테스터")
        String name,
        @Schema(description = "가입 방식", example = "LOCAL")
        String provider,
        @Schema(description = "가입 시각", example = "2026-03-13T00:00:00Z")
        Instant registeredAt
) {
}
