package com.project.auth.architecture;

import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.application.support.exception.ErrorCode;
import com.project.auth.application.user.exception.UserErrorCode;
import com.project.auth.presentation.support.exception.ApiErrorHttpStatusMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorHttpStatusMapperCoverageTest {

    static Stream<ErrorCode> allClientFacingErrorCodes() {
        return Stream.of(
                Arrays.stream(CommonErrorCode.values()),
                Arrays.stream(AuthErrorCode.values()),
                Arrays.stream(UserErrorCode.values())
        ).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0} must be explicitly mapped")
    @MethodSource("allClientFacingErrorCodes")
    void every_error_code_should_have_explicit_http_status_mapping(ErrorCode errorCode) {
        HttpStatus status = ApiErrorHttpStatusMapper.map(errorCode);

        assertThat(status)
                .as("ErrorCode %s (%s) must have a non-null mapping in ApiErrorHttpStatusMapper",
                        errorCode, errorCode.code())
                .isNotNull();

        if (errorCode != CommonErrorCode.INTERNAL_SERVER_ERROR
                && errorCode != CommonErrorCode.MESSAGE_NOT_WRITABLE) {
            assertThat(status)
                    .as("ErrorCode %s (%s) should not fall through to default INTERNAL_SERVER_ERROR — add an explicit mapping in ApiErrorHttpStatusMapper",
                            errorCode, errorCode.code())
                    .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
