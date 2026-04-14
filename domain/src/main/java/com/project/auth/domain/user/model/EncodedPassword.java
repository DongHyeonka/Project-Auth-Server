package com.project.auth.domain.user.model;

import com.project.auth.domain.user.service.UserPasswordPolicy;

public record EncodedPassword(String value) {

    public EncodedPassword {
        UserPasswordPolicy.validateEncoded(value);
    }

    public static EncodedPassword from(String value) {
        return new EncodedPassword(value);
    }
}
