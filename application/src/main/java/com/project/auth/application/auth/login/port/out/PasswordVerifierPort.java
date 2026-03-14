package com.project.auth.application.auth.login.port.out;

public interface PasswordVerifierPort {

    boolean matches(String rawPassword, String encodedPassword);
}
