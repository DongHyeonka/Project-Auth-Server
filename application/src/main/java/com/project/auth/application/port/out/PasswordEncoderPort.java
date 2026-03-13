package com.project.auth.application.port.out;

public interface PasswordEncoderPort {

    String encode(String rawPassword);
}
