package com.project.auth.application.user.signup;

import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;

record ValidatedSignUpCommand(
        UserEmail email,
        String rawPassword,
        UserName name
) {
}
