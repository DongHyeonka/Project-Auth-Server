package com.project.auth.application.auth.identity.internal;

import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;

record ValidatedKeycloakUserClaims(
        String subject,
        UserEmail email,
        UserName name
) {
}
