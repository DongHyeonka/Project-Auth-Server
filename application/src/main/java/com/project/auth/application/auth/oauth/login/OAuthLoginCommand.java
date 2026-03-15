package com.project.auth.application.auth.oauth.login;

public record OAuthLoginCommand(
        String provider,
        String providerSubject,
        String email,
        String name
) {
}
