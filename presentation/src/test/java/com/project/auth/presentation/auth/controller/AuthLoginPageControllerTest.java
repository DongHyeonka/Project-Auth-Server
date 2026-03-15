package com.project.auth.presentation.auth.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthLoginPageControllerTest {

    private final AuthLoginPageController controller = new AuthLoginPageController();

    @Test
    void loginPageForwardsToStaticHtml() {
        assertThat(controller.loginPage()).isEqualTo("forward:/auth-login.html");
    }
}
