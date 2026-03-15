package com.project.auth.presentation.auth.mapper;

import com.project.auth.application.auth.login.LoginCommand;
import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.presentation.auth.dto.LoginRequest;
import com.project.auth.presentation.auth.dto.LoginResponse;
import com.project.auth.presentation.auth.dto.LoginTokenResponse;
import com.project.auth.presentation.auth.dto.LoginUserResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthPresentationMapper {

    public LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(request.email(), request.password());
    }

    public LoginResponse toResponse(LoginResult result) {
        return new LoginResponse(
                new LoginUserResponse(
                        result.userId(),
                        result.email(),
                        result.name(),
                        result.provider()
                ),
                new LoginTokenResponse(
                        result.issuer(),
                        result.accessToken(),
                        result.tokenType(),
                        result.expiresIn(),
                        result.issuedAt(),
                        result.expiresAt()
                )
        );
    }
}
