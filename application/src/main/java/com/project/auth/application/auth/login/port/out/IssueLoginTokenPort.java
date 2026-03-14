package com.project.auth.application.auth.login.port.out;

import com.project.auth.application.auth.token.IssuedAccessToken;
import com.project.auth.domain.user.model.User;

public interface IssueLoginTokenPort {

    IssuedAccessToken issue(User user);
}
