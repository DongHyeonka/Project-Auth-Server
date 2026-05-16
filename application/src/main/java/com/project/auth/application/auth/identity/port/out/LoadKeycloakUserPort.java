package com.project.auth.application.auth.identity.port.out;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;

import java.util.Optional;

public interface LoadKeycloakUserPort {

    Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    boolean existsByEmail(UserEmail email);
}
