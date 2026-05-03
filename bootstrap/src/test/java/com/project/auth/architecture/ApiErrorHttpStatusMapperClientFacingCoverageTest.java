package com.project.auth.architecture;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.ClientFacingErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
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
                Arguments.of(PresentationErrorCode.MESSAGE_NOT_WRITABLE, HttpStatus.INTERNAL_SERVER_ERROR)
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

    @Test
    void all_client_facing_error_codes_have_unique_string_codes() {
        Stream<ClientFacingErrorCode> all = Stream.of(
                Arrays.stream(CommonErrorCode.values()),
                Arrays.stream(AuthErrorCode.values()),
                Arrays.stream(PresentationErrorCode.values())
        ).flatMap(s -> s);

        List<String> codes = all.map(ClientFacingErrorCode::code).toList();

        assertThat(codes)
                .as("ClientFacingErrorCode.code() values must be unique across enums to avoid client-side ambiguity")
                .doesNotHaveDuplicates();
    }
}
