package com.project.auth.application.user.signup.port.in;

import com.project.auth.application.user.signup.SignUpCommand;
import com.project.auth.application.user.signup.SignUpResult;

public interface SignUpUseCase {

    SignUpResult signUp(SignUpCommand command);
}
