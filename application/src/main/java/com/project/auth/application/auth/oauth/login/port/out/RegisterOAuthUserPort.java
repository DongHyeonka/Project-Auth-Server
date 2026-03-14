package com.project.auth.application.auth.oauth.login.port.out;

import com.project.auth.domain.user.model.User;

public interface RegisterOAuthUserPort {

    User save(User user);
}
