package com.project.auth.infrastructure.persistence.user.mapper;

import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;

public class UserPersistenceMapper {

    public UserJpaEntity toEntity(User user) {
        return UserJpaEntity.of(
                user.getId(),
                user.getEmail(),
                user.getEncodedPassword(),
                user.getName(),
                user.getProvider(),
                user.getProviderSubject(),
                user.getCreatedAt()
        );
    }

    public User toDomain(UserJpaEntity userJpaEntity) {
        return User.restore(
                userJpaEntity.getId(),
                UserEmail.from(userJpaEntity.getEmail()),
                userJpaEntity.getEncodedPassword(),
                UserName.from(userJpaEntity.getName()),
                userJpaEntity.getProvider(),
                userJpaEntity.getProviderSubject(),
                userJpaEntity.getCreatedAt()
        );
    }
}
