package com.project.auth.presentation.auth.docs;

import com.project.auth.presentation.auth.current.AuthenticatedUser;
import com.project.auth.presentation.auth.dto.AuthenticatedUserResponse;
import com.project.auth.presentation.support.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

public interface AuthenticatedUserApiDocs {

    @Operation(
            summary = "현재 사용자 조회",
            description = "Keycloak access token을 검증한 뒤 이미 연결된 내부 사용자를 조회해 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "현재 사용자 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": true,
                                              "code": "SUCCESS",
                                              "message": "요청이 성공했습니다.",
                                              "data": {
                                                "userId": "11111111-1111-1111-1111-111111111111",
                                                "subject": "f7b9d4a6-1111-4444-9999-8c6a1f9a0001",
                                                "email": "tester@example.com",
                                                "name": "테스터",
                                                "provider": "KEYCLOAK",
                                                "authorities": ["ROLE_USER"]
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", description = "유효한 Bearer token이 없음"),
            @ApiResponse(responseCode = "404", description = "연결된 내부 Keycloak 사용자가 없음")
    })
    ApiResult<AuthenticatedUserResponse> me(AuthenticatedUser currentUser);
}
