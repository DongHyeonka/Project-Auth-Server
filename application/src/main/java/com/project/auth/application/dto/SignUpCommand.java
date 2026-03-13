package com.project.auth.application.dto;

public record SignUpCommand(
        String email,
        String password,
        String name
) {
}
