package com.project.auth.infrastructure.persistence.user;

import com.project.auth.application.user.exception.DuplicateUserEmailException;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryUserRepositoryAdapter implements RegisterUserPort, LoadLoginUserPort {

    private final ConcurrentMap<String, User> usersByEmail = new ConcurrentHashMap<>();

    @Override
    public boolean existsByEmail(UserEmail email) {
        return usersByEmail.containsKey(email.value());
    }

    @Override
    public Optional<User> findByEmail(UserEmail email) {
        return Optional.ofNullable(usersByEmail.get(email.value()));
    }

    @Override
    public User save(User user) {
        User previousUser = usersByEmail.putIfAbsent(user.getEmail(), user);
        if (previousUser != null) {
            throw new DuplicateUserEmailException();
        }

        return user;
    }
}
