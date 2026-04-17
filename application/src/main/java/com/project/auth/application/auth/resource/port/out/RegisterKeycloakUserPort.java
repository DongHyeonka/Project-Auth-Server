package com.project.auth.application.auth.resource.port.out;

import com.project.auth.domain.user.model.User;

public interface RegisterKeycloakUserPort {

    User save(User user);
}
