package com.project.auth.application.user.signup.port.out;

import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;

public interface RegisterUserPort {

    boolean existsByEmail(UserEmail email);

    User save(User user);
}
