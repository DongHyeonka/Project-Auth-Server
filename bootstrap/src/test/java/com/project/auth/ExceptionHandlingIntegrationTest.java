package com.project.auth;

import com.project.auth.application.auth.exception.AuthErrorCode;
import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.config.web.TraceIdFilter;
import com.project.auth.domain.user.exception.DomainException;
import com.project.auth.infrastructure.support.exception.InfrastructureErrorCode;
import com.project.auth.infrastructure.support.exception.InfrastructureException;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
    void authenticated_user_without_required_role_returns_403_json_and_logs_principal(
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

        assertThat(output).contains("Access denied [traceId=");
        assertThat(output).contains("principal=alice@example.com");
        assertThat(output).contains("GET /test-support/admin");
    }

    @Test
    void malformed_json_returns_400_json() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST_BODY.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INVALID_REQUEST_BODY.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    @Test
    void unsupported_method_returns_405_json() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.METHOD_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.METHOD_NOT_ALLOWED.message()))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
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

        assertThat(output).contains("Infrastructure failure [traceId=");
        assertThat(output).contains(InfrastructureErrorCode.VAULT_TRANSIT_FAILED.code());
        assertThat(output).contains("GET /test-support/infrastructure");
    }

    @Test
    void domain_exception_returns_safe_user_facing_message() throws Exception {
        mockMvc.perform(get("/test-support/domain").with(user("user@example.com").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CommonErrorCode.DOMAIN_RULE_VIOLATION.code()))
                .andExpect(jsonPath("$.message").value("테스트용 사용자 노출 메시지"))
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

        @GetMapping("/test-support/admin")
        String adminOnly() {
            throw new org.springframework.security.authorization.AuthorizationDeniedException("Access Denied");
        }

        @GetMapping("/test-support/infrastructure")
        String infrastructureFailure() {
            throw new InfrastructureException(
                    InfrastructureErrorCode.VAULT_TRANSIT_FAILED,
                    "Simulated Vault outage for integration test."
            );
        }

        @GetMapping("/test-support/domain")
        String domainFailure() {
            throw new TestDomainException("테스트용 사용자 노출 메시지");
        }
    }

    static final class TestDomainException extends DomainException {

        private TestDomainException(String message) {
            super(message);
        }
    }
}
