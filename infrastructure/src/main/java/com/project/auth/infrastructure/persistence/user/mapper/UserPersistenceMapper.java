package com.project.auth.infrastructure.persistence.user.mapper;

import com.project.auth.domain.user.exception.DomainException;
import com.project.auth.domain.user.model.User;
import com.project.auth.domain.user.model.UserEmail;
import com.project.auth.domain.user.model.UserName;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;
import com.project.auth.infrastructure.support.exception.InfrastructureErrorCode;
import com.project.auth.infrastructure.support.exception.InfrastructureException;

public class UserPersistenceMapper {

    public UserJpaEntity toEntity(User user) {
        return UserJpaEntity.of(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getProvider(),
                user.getProviderSubject(),
                user.getCreatedAt()
        );
    }

    public User toDomain(UserJpaEntity userJpaEntity) {
        try {
            return User.restore(
                    userJpaEntity.getId(),
                    UserEmail.from(userJpaEntity.getEmail()),
                    UserName.from(userJpaEntity.getName()),
                    userJpaEntity.getProvider(),
                    userJpaEntity.getProviderSubject(),
                    userJpaEntity.getCreatedAt()
            );
        } catch (DomainException | IllegalArgumentException | NullPointerException exception) {
            throw new InfrastructureException(
                    InfrastructureErrorCode.PERSISTED_DATA_INVALID,
                    exception
            );
        }
    }
}
