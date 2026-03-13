package com.project.auth.presentation.controller;

import com.project.auth.application.code.UserSuccessCode;
import com.project.auth.application.dto.SignUpResult;
import com.project.auth.application.usecase.SignUpUseCase;
import com.project.auth.common.response.ApiResult;
import com.project.auth.presentation.docs.UserSignUpApiDocs;
import com.project.auth.presentation.dto.SignUpRequest;
import com.project.auth.presentation.dto.SignUpResponse;
import com.project.auth.presentation.mapper.UserPresentationMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "회원 가입과 사용자 코어 API")
public class UserSignUpController implements UserSignUpApiDocs {

    private static final UserSuccessCode USER_SIGNED_UP = UserSuccessCode.USER_SIGNED_UP;

    private final SignUpUseCase signUpUseCase;
    private final UserPresentationMapper userPresentationMapper;

    public UserSignUpController(SignUpUseCase signUpUseCase, UserPresentationMapper userPresentationMapper) {
        this.signUpUseCase = signUpUseCase;
        this.userPresentationMapper = userPresentationMapper;
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<ApiResult<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpResult signUpResult = signUpUseCase.signUp(userPresentationMapper.toCommand(request));
        SignUpResponse response = userPresentationMapper.toResponse(signUpResult);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(USER_SIGNED_UP.code(), USER_SIGNED_UP.message(), response));
    }
}
