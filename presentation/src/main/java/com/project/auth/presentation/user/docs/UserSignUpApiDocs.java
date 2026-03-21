package com.project.auth.presentation.user.docs;

import com.project.auth.presentation.user.dto.SignUpRequest;
import com.project.auth.presentation.user.dto.SignUpResponse;
import com.project.auth.presentation.support.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

public interface UserSignUpApiDocs {

    @Operation(
            summary = "회원가입",
            description = "로컬 계정 기준 회원가입을 처리하고 공통 응답 형식으로 결과를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": true,
                                              "code": "USER_SIGNED_UP",
                                              "message": "회원가입이 완료되었습니다.",
                                              "data": {
                                                "userId": "11111111-1111-1111-1111-111111111111",
                                                "email": "tester@example.com",
                                                "name": "테스터",
                                                "provider": "LOCAL",
                                                "registeredAt": "2026-03-13T00:00:00Z"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": false,
                                              "code": "COMMON-001",
                                              "message": "입력값이 올바르지 않습니다.",
                                              "data": {
                                                "email": "이메일 형식이 올바르지 않습니다."
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "중복 이메일",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "success": false,
                                              "code": "USER-004",
                                              "message": "이미 가입된 이메일입니다.",
                                              "data": null
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<ApiResult<SignUpResponse>> signUp(SignUpRequest request);
}
