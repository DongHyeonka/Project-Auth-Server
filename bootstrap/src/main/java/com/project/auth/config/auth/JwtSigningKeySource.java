package com.project.auth.config.auth;

public interface JwtSigningKeySource {

    JwtSigningKeyMaterial load();
}
