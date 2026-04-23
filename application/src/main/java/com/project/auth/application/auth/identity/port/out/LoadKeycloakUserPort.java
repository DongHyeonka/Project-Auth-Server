package com.project.auth.application.auth.identity.port.out;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;

import java.util.Optional;

public interface LoadKeycloakUserPort {

    boolean existsByEmail(String email);

    Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
