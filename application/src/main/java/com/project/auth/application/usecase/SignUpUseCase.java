package com.project.auth.application.usecase;

import com.project.auth.application.dto.SignUpCommand;
import com.project.auth.application.dto.SignUpResult;

public interface SignUpUseCase {

    SignUpResult signUp(SignUpCommand command);
}
