package com.project.auth.presentation.auth.current;

public record OAuth2LoginPrincipal(
        String provider,
        String providerSubject,
        String email,
        String name
) {
}
