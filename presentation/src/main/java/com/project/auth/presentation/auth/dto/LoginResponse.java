package com.project.auth.presentation.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        @Schema(description = "회원 식별자", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,
        @Schema(description = "회원 이메일", example = "tester@example.com")
        String email,
        @Schema(description = "회원 이름", example = "테스터")
        String name,
        @Schema(description = "가입 방식", example = "LOCAL")
        String provider,
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,
        @Schema(description = "액세스 토큰 만료 시간(초)", example = "1800")
        long expiresIn,
        @Schema(description = "액세스 토큰 발급 시각", example = "2026-03-14T00:00:00Z")
        Instant issuedAt,
        @Schema(description = "액세스 토큰 만료 시각", example = "2026-03-14T00:30:00Z")
        Instant expiresAt
) {
}
