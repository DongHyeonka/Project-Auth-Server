package com.project.auth.application.auth.oauth.login.port.out;

import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;

public interface IssueOAuthLoginTokenPort {

    IssuedAccessToken issue(User user);
}
