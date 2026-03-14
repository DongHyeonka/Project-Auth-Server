package com.project.auth.application.auth.login.port.out;

import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;

import java.util.Optional;

public interface LoadLoginUserPort {

    Optional<User> findByEmail(UserEmail email);
}
