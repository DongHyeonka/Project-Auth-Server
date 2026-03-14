package com.project.auth.presentation.user.mapper;

import com.project.auth.application.user.signup.SignUpCommand;
import com.project.auth.application.user.signup.SignUpResult;
import com.project.auth.presentation.user.dto.SignUpRequest;
import com.project.auth.presentation.user.dto.SignUpResponse;
import org.springframework.stereotype.Component;

@Component
public class UserPresentationMapper {

    public SignUpCommand toCommand(SignUpRequest request) {
        return new SignUpCommand(request.email(), request.password(), request.name());
    }

    public SignUpResponse toResponse(SignUpResult result) {
        return new SignUpResponse(
                result.userId(),
                result.email(),
                result.name(),
                result.provider(),
                result.registeredAt()
        );
    }
}
