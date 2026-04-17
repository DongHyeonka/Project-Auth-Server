package com.project.auth.architecture;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.exception.ErrorCode;
import com.project.auth.application.support.exception.UserErrorCode;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorHttpStatusMapperClientFacingCoverageTest {

    static Stream<ErrorCode> allClientFacingErrorCodes() {
        return Stream.of(
                Arrays.stream(CommonErrorCode.values()),
                Arrays.stream(PresentationErrorCode.values()),
                Arrays.stream(AuthErrorCode.values()),
                Arrays.stream(UserErrorCode.values())
        ).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0} should not fall through to default 500")
    @MethodSource("allClientFacingErrorCodes")
    void client_facing_error_codes_should_not_fall_through_to_default_internal_server_error(ErrorCode errorCode) {
        HttpStatus status = ApiErrorHttpStatusMapper.map(errorCode);

        assertThat(status)
                .as("Client-facing ErrorCode %s (%s) must produce a non-null HTTP status in ApiErrorHttpStatusMapper",
                        errorCode, errorCode.code())
                .isNotNull();

        if (errorCode != CommonErrorCode.INTERNAL_SERVER_ERROR
                && errorCode != PresentationErrorCode.MESSAGE_NOT_WRITABLE) {
            assertThat(status)
                    .as("Client-facing ErrorCode %s (%s) should not accidentally fall through to default INTERNAL_SERVER_ERROR in ApiErrorHttpStatusMapper",
                            errorCode, errorCode.code())
                    .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
