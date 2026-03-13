package com.project.auth.application.port.out;

import com.project.auth.domain.model.User;
import com.project.auth.domain.model.UserEmail;

public interface UserRepositoryPort {

    boolean existsByEmail(UserEmail email);

    User save(User user);
}
