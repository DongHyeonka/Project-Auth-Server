package com.project.auth.infrastructure.persistence;

import com.project.auth.application.exception.DuplicateUserEmailException;
import com.project.auth.application.port.out.UserRepositoryPort;
import com.project.auth.domain.model.User;
import com.project.auth.domain.model.UserEmail;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryUserRepositoryAdapter implements UserRepositoryPort {

    private final ConcurrentMap<String, User> usersByEmail = new ConcurrentHashMap<>();

    @Override
    public boolean existsByEmail(UserEmail email) {
        return usersByEmail.containsKey(email.value());
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
