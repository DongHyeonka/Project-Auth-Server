package com.project.auth.architecture;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorHttpStatusMapperClientFacingCoverageTest {

    static Stream<Arguments> exactMappings() {
        return Stream.of(
                Arguments.of(CommonErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),

                Arguments.of(AuthErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED),
                Arguments.of(AuthErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN),
                Arguments.of(AuthErrorCode.KEYCLOAK_CLAIMS_INVALID, HttpStatus.BAD_REQUEST),
                Arguments.of(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(AuthErrorCode.KEYCLOAK_ACCOUNT_CONFLICT, HttpStatus.CONFLICT),

                Arguments.of(PresentationErrorCode.INVALID_INPUT, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.INVALID_REQUEST_BODY, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.MISSING_PARAMETER, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.CONSTRAINT_VIOLATION, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.MISSING_HEADER, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.REQUEST_BINDING_FAILED, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED),
                Arguments.of(PresentationErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                Arguments.of(PresentationErrorCode.NOT_ACCEPTABLE, HttpStatus.NOT_ACCEPTABLE),
                Arguments.of(PresentationErrorCode.PAYLOAD_TOO_LARGE, HttpStatus.CONTENT_TOO_LARGE),
                Arguments.of(PresentationErrorCode.MESSAGE_NOT_WRITABLE, HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of(PresentationErrorCode.UNHANDLED_CLIENT_ERROR, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.TYPE_MISMATCH, HttpStatus.BAD_REQUEST),
                Arguments.of(PresentationErrorCode.INVALID_PARAMETER, HttpStatus.BAD_REQUEST)
        );
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("exactMappings")
    void each_client_facing_error_code_maps_to_its_exact_expected_http_status(
            ClientFacingErrorCode errorCode,
            HttpStatus expectedStatus
    ) {
        HttpStatus actual = ApiErrorHttpStatusMapper.map(errorCode);

        assertThat(actual)
                .as("ClientFacingErrorCode %s (%s) must map to %s",
                        errorCode, errorCode.code(), expectedStatus)
                .isEqualTo(expectedStatus);
    }

    @Test
    void exact_mapping_table_must_cover_every_client_facing_error_code_enum_value() {
        Set<ClientFacingErrorCode> declaredInTable = exactMappings()
                .map(args -> (ClientFacingErrorCode) args.get()[0])
                .collect(Collectors.toCollection(HashSet::new));

        Set<ClientFacingErrorCode> allEnumValues = new HashSet<>();
        allEnumValues.addAll(List.of(CommonErrorCode.values()));
        allEnumValues.addAll(List.of(AuthErrorCode.values()));
        allEnumValues.addAll(List.of(PresentationErrorCode.values()));

        Set<ClientFacingErrorCode> missing = new HashSet<>(allEnumValues);
        missing.removeAll(declaredInTable);

        assertThat(missing)
                .as("Every ClientFacingErrorCode enum value must have an exact-status assertion in the mapping table. "
                        + "Newly added enum values must be added to exactMappings().")
                .isEmpty();
    }

    /**
     * Catches the case where a future module declares a new ClientFacingErrorCode
     * implementation (enum or otherwise) without updating either the mapping
     * table or the mapper's switch — both of which silently fall through to
     * INTERNAL_SERVER_ERROR via the non-sealed default branch.
     */
    @Test
    void every_client_facing_error_code_implementation_on_classpath_is_covered_by_the_table() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.project.auth");

        List<Class<?>> implementations = new ArrayList<>();
        for (var javaClass : classes) {
            if (javaClass.isAssignableTo(ClientFacingErrorCode.class) && !javaClass.isInterface()) {
                Class<?> reflected = javaClass.reflect();
                if (reflected.equals(ClientFacingErrorCode.class)) {
                    continue;
                }
                // 테스트 안에서 default 분기를 트리거하기 위해 만든 익명/내부 구현체는 가드 대상이 아니다.
                if (reflected.getName().contains("Test$") || reflected.isAnonymousClass()) {
                    continue;
                }
                implementations.add(reflected);
            }
        }

        Set<Class<?>> tableImplementations = exactMappings()
                .map(args -> args.get()[0].getClass())
                .collect(Collectors.toCollection(HashSet::new));

        Set<Class<?>> uncovered = new HashSet<>(implementations);
        uncovered.removeAll(tableImplementations);

        assertThat(uncovered)
                .as("Every ClientFacingErrorCode implementation discovered on the classpath must contribute "
                        + "at least one entry to exactMappings(). Uncovered types fall through the mapper's "
                        + "non-sealed default branch and silently 500.")
                .isEmpty();
    }

    @Test
    void unmapped_client_facing_error_code_implementation_falls_back_to_500() {
        // 매퍼의 default 분기를 명시적으로 트리거 — non-sealed marker라 이런 구현이 가능하다.
        ClientFacingErrorCode unknown = new ClientFacingErrorCode() {
            @Override public String code() { return "UNKNOWN-001"; }
            @Override public String message() { return "unknown"; }
        };
        assertThat(ApiErrorHttpStatusMapper.map(unknown)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void all_client_facing_error_codes_have_unique_string_codes() {
        List<ClientFacingErrorCode> all = new java.util.ArrayList<>();
        all.addAll(List.of(CommonErrorCode.values()));
        all.addAll(List.of(AuthErrorCode.values()));
        all.addAll(List.of(PresentationErrorCode.values()));

        List<String> codes = all.stream().map(ClientFacingErrorCode::code).toList();

        assertThat(codes)
                .as("ClientFacingErrorCode.code() values must be unique across enums to avoid client-side ambiguity")
                .doesNotHaveDuplicates();
    }
}
