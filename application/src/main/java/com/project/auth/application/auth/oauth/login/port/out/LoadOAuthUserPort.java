package com.project.auth.application.auth.oauth.login.port.out;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;

import java.util.Optional;

public interface LoadOAuthUserPort {

    Optional<User> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    boolean existsByEmail(UserEmail email);
}
