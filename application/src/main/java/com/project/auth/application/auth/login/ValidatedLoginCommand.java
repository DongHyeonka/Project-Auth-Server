package com.project.auth.application.auth.login;

import com.project.auth.domain.user.model.UserEmail;

record ValidatedLoginCommand(
        UserEmail email,
        String password
) {
}
