package com.project.auth;

import com.project.auth.application.support.exception.AuthErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.config.web.TraceIdFilter;
import com.project.auth.domain.user.exception.DomainException;
import com.project.auth.infrastructure.support.exception.InfrastructureErrorCode;
import com.project.auth.infrastructure.support.exception.InfrastructureException;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(ExceptionHandlingIntegrationTest.ExceptionHandlingTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ExceptionHandlingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new TraceIdFilter())
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticated_request_returns_401_json_with_trace_metadata() throws Exception {
        mockMvc.perform(get("/test-support/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTHENTICATION_REQUIRED.code()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.AUTHENTICATION_REQUIRED.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @Test
    void authenticated_user_without_required_role_returns_403_json_without_exposing_principal(
            CapturedOutput output
    ) throws Exception {
        mockMvc.perform(get("/test-support/admin").with(user("alice@example.com").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCESS_DENIED.code()))
                .andExpect(jsonPath("$.message").value(AuthErrorCode.ACCESS_DENIED.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));

        assertThat(output).contains("traceId=");
        assertThat(output).doesNotContain("principal=alice@example.com");
    }

    @Test
    void malformed_json_returns_400_json() throws Exception {
        mockMvc.perform(post("/test-support/body")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.INVALID_REQUEST_BODY.code()))
                .andExpect(jsonPath("$.message").value(PresentationErrorCode.INVALID_REQUEST_BODY.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @Test
    void unsupported_method_returns_405_json() throws Exception {
        mockMvc.perform(post("/test-support/protected").with(user("user@example.com").roles("USER")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.METHOD_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.message").value(PresentationErrorCode.METHOD_NOT_ALLOWED.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @Test
    void client_supplied_trace_id_is_ignored_and_server_generated_value_is_returned() throws Exception {
        mockMvc.perform(post("/test-support/body")
                        .with(user("user@example.com").roles("USER"))
                        .header("X-Trace-Id", "client-provided-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("X-Trace-Id", not("client-provided-trace")))
                .andExpect(jsonPath("$.traceId", not("client-provided-trace")));
    }

    @Test
    void missing_required_header_returns_header_specific_error_code() throws Exception {
        mockMvc.perform(get("/test-support/header-required").with(user("user@example.com").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.MISSING_HEADER.code()))
                .andExpect(jsonPath("$.message").value(PresentationErrorCode.MISSING_HEADER.message()));
    }

    @Test
    void infrastructure_exception_returns_common_999_and_logs_internal_code(
            CapturedOutput output
    ) throws Exception {
        mockMvc.perform(get("/test-support/infrastructure").with(user("ops@example.com").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_SERVER_ERROR.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));

        assertThat(output).contains("traceId=");
        assertThat(output).contains("Infrastructure failure. errorCode=");
        assertThat(output).contains(InfrastructureErrorCode.EXTERNAL_SERVICE_ERROR.code());
        assertThat(output).contains("method=GET requestPath=/test-support/infrastructure");
    }

    @Test
    void access_denied_for_anonymous_user_maps_to_401_authentication_required() throws Exception {
        mockMvc.perform(get("/test-support/access-denied").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.AUTHENTICATION_REQUIRED.code()));
    }

    @Test
    void access_denied_for_authenticated_user_remains_403_forbidden() throws Exception {
        mockMvc.perform(get("/test-support/access-denied").with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCESS_DENIED.code()));
    }

    @Test
    void framework_thrown_4xx_status_preserves_status_and_maps_to_unhandled_client_error() throws Exception {
        mockMvc.perform(get("/test-support/conflict").with(user("user@example.com").roles("USER")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code()));
    }

    @Test
    void framework_thrown_5xx_status_preserves_status_and_collapses_to_common_999() throws Exception {
        mockMvc.perform(get("/test-support/service-unavailable").with(user("user@example.com").roles("USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()));
    }

    @Test
    void uncaught_runtime_exception_hits_safety_net_and_returns_common_999(
            CapturedOutput output
    ) throws Exception {
        mockMvc.perform(get("/test-support/uncaught").with(user("user@example.com").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()));

        assertThat(output).contains("Uncaught exception reached @ExceptionHandler safety net");
    }

    @Test
    void missing_required_query_parameter_returns_400_with_missing_parameter_code() throws Exception {
        mockMvc.perform(get("/test-support/required-param").with(user("user@example.com").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.MISSING_PARAMETER.code()));
    }

    @Test
    void type_mismatch_in_query_parameter_returns_400_with_type_mismatch_code() throws Exception {
        mockMvc.perform(get("/test-support/typed-param")
                        .with(user("user@example.com").roles("USER"))
                        .param("id", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.TYPE_MISMATCH.code()));
    }

    @Test
    void unsupported_content_type_returns_415() throws Exception {
        mockMvc.perform(post("/test-support/body")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<x/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.UNSUPPORTED_MEDIA_TYPE.code()));
    }

    @Test
    void unacceptable_accept_header_returns_406() throws Exception {
        // 406 응답은 협상 가능한 미디어 타입이 없어 본문 직렬화가 불가하므로 status만 단언한다.
        // (핸들러 자체의 라인 커버는 status 결정 시점에 모두 실행된다.)
        mockMvc.perform(get("/test-support/produces-json")
                        .with(user("user@example.com").roles("USER"))
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void unknown_route_returns_404_with_resource_not_found_code() throws Exception {
        mockMvc.perform(get("/test-support/this-path-does-not-exist")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    void method_argument_not_valid_returns_400_with_invalid_input_and_field_errors() throws Exception {
        // @Valid @RequestBody 검증 실패 → MethodArgumentNotValidException 트리거
        mockMvc.perform(post("/test-support/validated-body")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors['/name']").isArray());
    }

    @Test
    void business_exception_returns_mapped_status_and_code() throws Exception {
        mockMvc.perform(get("/test-support/business")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.KEYCLOAK_USER_NOT_FOUND.code()));
    }

    @Test
    void leaked_domain_exception_falls_back_to_common_999_without_exposing_message() throws Exception {
        mockMvc.perform(get("/test-support/domain").with(user("user@example.com").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_SERVER_ERROR.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExceptionHandlingTestConfiguration {

        @Bean
        TestExceptionController testExceptionController() {
            return new TestExceptionController();
        }
    }

    @RestController
    static class TestExceptionController {

        @GetMapping("/test-support/protected")
        String protectedEndpoint() {
            return "ok";
        }

        @PostMapping("/test-support/body")
        String bodyEndpoint(@RequestBody Map<String, Object> payload) {
            return payload.toString();
        }

        @GetMapping("/test-support/admin")
        String adminOnly() {
            throw new org.springframework.security.authorization.AuthorizationDeniedException("Access Denied");
        }

        @GetMapping("/test-support/infrastructure")
        String infrastructureFailure() {
            throw new InfrastructureException(
                    InfrastructureErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Simulated external outage for integration test."
            );
        }

        @GetMapping("/test-support/domain")
        String domainFailure() {
            throw new TestDomainException("테스트용 사용자 노출 메시지");
        }

        @GetMapping("/test-support/header-required")
        String headerRequired(
                @org.springframework.web.bind.annotation.RequestHeader("X-Test-Header") String headerValue
        ) {
            return headerValue;
        }

        @GetMapping("/test-support/conflict")
        String frameworkThrown4xx() {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT);
        }

        @GetMapping("/test-support/service-unavailable")
        String frameworkThrown5xx() {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }

        @GetMapping("/test-support/uncaught")
        String uncaughtRuntime() {
            throw new IllegalStateException("simulated unknown failure");
        }

        @GetMapping("/test-support/access-denied")
        String accessDenied() {
            throw new org.springframework.security.access.AccessDeniedException("simulated denial");
        }

        @GetMapping("/test-support/required-param")
        String requiredParam(@org.springframework.web.bind.annotation.RequestParam("name") String name) {
            return name;
        }

        @GetMapping("/test-support/typed-param")
        String typedParam(@org.springframework.web.bind.annotation.RequestParam("id") long id) {
            return Long.toString(id);
        }

        @GetMapping(value = "/test-support/produces-json", produces = MediaType.APPLICATION_JSON_VALUE)
        String producesJson() {
            return "{\"ok\":true}";
        }

        @PostMapping("/test-support/validated-body")
        String validatedBody(
                @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid ValidatedRequest request
        ) {
            return request.name();
        }

        @GetMapping("/test-support/business")
        String businessException() {
            throw new com.project.auth.application.auth.exception.KeycloakUserNotFoundException();
        }
    }

    record ValidatedRequest(@jakarta.validation.constraints.NotBlank(message = "이름은 필수입니다") String name) {
    }

    static final class TestDomainException extends DomainException {

        private TestDomainException(String message) {
            super(message);
        }
    }
}
