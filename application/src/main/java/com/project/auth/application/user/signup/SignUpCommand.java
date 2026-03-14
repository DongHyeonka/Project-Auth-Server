package com.project.auth.application.user.signup;

public record SignUpCommand(
        String email,
        String password,
        String name
) {
}
