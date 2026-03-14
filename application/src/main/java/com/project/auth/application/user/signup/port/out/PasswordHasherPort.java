package com.project.auth.application.user.signup.port.out;

public interface PasswordHasherPort {

    String encode(String rawPassword);
}
