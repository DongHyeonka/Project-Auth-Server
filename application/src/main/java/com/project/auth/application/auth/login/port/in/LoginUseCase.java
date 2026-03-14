package com.project.auth.application.auth.login.port.in;

import com.project.auth.application.auth.login.LoginCommand;
import com.project.auth.application.auth.login.LoginResult;

public interface LoginUseCase {

    LoginResult login(LoginCommand command);
}
