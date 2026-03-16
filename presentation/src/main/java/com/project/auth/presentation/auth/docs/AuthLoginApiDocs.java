package com.project.auth.presentation.auth.docs;

import com.project.auth.common.response.ApiResult;
import com.project.auth.presentation.auth.dto.LoginRequest;
import com.project.auth.presentation.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

public interface AuthLoginApiDocs {

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 검증하고 auth-server가 직접 발급한 JWT를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": true,
                                              "code": "AUTH_LOGIN_SUCCEEDED",
                                              "message": "로그인이 완료되었습니다.",
                                              "data": {
                                                "user": {
                                                  "userId": "11111111-1111-1111-1111-111111111111",
                                                  "email": "tester@example.com",
                                                  "name": "테스터",
                                                  "provider": "LOCAL"
                                                },
                                                "token": {
                                                  "issuer": "https://auth.example.com",
                                                  "accessToken": "eyJraWQiOiJhdXRoLXJzYS0xIiwiYWxnIjoiUlMyNTYifQ...",
                                                  "tokenType": "Bearer",
                                                  "expiresIn": 1800,
                                                  "issuedAt": "2026-03-14T00:00:00Z",
                                                  "expiresAt": "2026-03-14T00:30:00Z"
                                                }
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": false,
                                              "code": "AUTH-001",
                                              "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
                                              "data": null
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<ApiResult<LoginResponse>> login(LoginRequest request);
}
