package com.project.auth.infrastructure.persistence.user.mapper;

import com.project.auth.domain.user.model.AuthProvider;
import com.project.auth.infrastructure.persistence.user.entity.UserJpaEntity;
import com.project.auth.infrastructure.support.exception.InfrastructureErrorCode;
import com.project.auth.infrastructure.support.exception.InfrastructureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPersistenceMapperTest {

    private final UserPersistenceMapper mapper = new UserPersistenceMapper();

    @Test
    void toDomain_translates_invalid_persisted_user_state_to_infrastructure_exception() {
        UserJpaEntity invalidEntity = UserJpaEntity.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "not-an-email",
                "테스터",
                AuthProvider.KEYCLOAK,
                "keycloak-subject-1",
                Instant.parse("2026-04-17T00:00:00Z")
        );

        assertThatThrownBy(() -> mapper.toDomain(invalidEntity))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(InfrastructureErrorCode.PERSISTED_DATA_INVALID);
    }
}
