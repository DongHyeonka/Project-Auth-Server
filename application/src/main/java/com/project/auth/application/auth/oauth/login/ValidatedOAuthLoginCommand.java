package com.project.auth.application.auth.oauth.login;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;

record ValidatedOAuthLoginCommand(
        AuthProvider provider,
        String providerSubject,
        UserEmail email,
        UserName name
) {
}
