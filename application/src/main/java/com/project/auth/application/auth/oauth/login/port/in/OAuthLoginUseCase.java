package com.project.auth.application.auth.oauth.login.port.in;

import com.project.auth.application.auth.login.LoginResult;
import com.project.auth.application.auth.oauth.login.OAuthLoginCommand;

public interface OAuthLoginUseCase {

    LoginResult login(OAuthLoginCommand command);
}
