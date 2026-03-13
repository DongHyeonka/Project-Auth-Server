package com.project.auth.infrastructure.persistence;

import com.project.auth.application.port.out.PasswordEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class BcryptPasswordEncoderAdapter implements PasswordEncoderPort {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
