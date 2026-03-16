package com.project.auth.presentation.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record LoginTokenResponse(
        @Schema(description = "토큰 발급 주체", example = "https://auth.example.com")
        String issuer,
        @Schema(description = "액세스 토큰", example = "eyJraWQiOiJhdXRoLXJzYS0xIiwiYWxnIjoiUlMyNTYifQ...")
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
