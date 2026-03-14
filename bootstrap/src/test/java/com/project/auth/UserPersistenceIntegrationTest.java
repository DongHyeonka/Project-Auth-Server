package com.project.auth;

import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.user.signup.SignUpCommand;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.domain.user.model.UserEmail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserPersistenceIntegrationTest {

    @Autowired
    private SignUpUseCase signUpUseCase;

    @Autowired
    private LoadLoginUserPort loadLoginUserPort;

    @Test
    void signUpPersistsUserIntoDatabase() {
        signUpUseCase.signUp(new SignUpCommand("persisted@example.com", "password123", "영속유저"));

        assertThat(loadLoginUserPort.findByEmail(UserEmail.from("persisted@example.com")))
                .isPresent()
                .get()
                .extracting(user -> user.getEmail(), user -> user.getName(), user -> user.getProvider().name())
                .containsExactly("persisted@example.com", "영속유저", "LOCAL");
    }
}
