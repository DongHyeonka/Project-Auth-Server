package com.project.auth.application.auth.oauth.login;

import com.project.auth.domain.user.model.AuthProvider;

public record OAuthLoginCommand(
        AuthProvider provider,
        String providerSubject,
        String email,
        String name
) {
}
