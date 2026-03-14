package com.project.auth.application.auth.login;

public record LoginCommand(
        String email,
        String password
) {
}
