package com.project.auth.presentation.auth.mapper;

import com.project.auth.application.auth.identity.LoadedKeycloakUser;
import com.project.auth.presentation.auth.dto.AuthenticatedUserResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AuthenticatedUserPresentationMapper {

    public AuthenticatedUserResponse toResponse(LoadedKeycloakUser user, Set<String> authorities) {
        return new AuthenticatedUserResponse(
                user.userId(),
                user.providerSubject(),
                user.email(),
                user.name(),
                user.provider(),
                authorities
        );
    }
}
