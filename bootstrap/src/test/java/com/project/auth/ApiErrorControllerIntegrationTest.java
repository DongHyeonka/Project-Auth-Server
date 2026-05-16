package com.project.auth;

import com.project.auth.application.support.exception.CommonErrorCode;
import com.project.auth.config.web.TraceIdFilter;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@link com.project.auth.config.web.ApiErrorController} preserves
 * the container-decided status code instead of always returning 500. This path is
 * what handles filter-thrown exceptions, response.sendError(...) calls, and any
 * double-fault that bypasses @ExceptionHandler.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApiErrorControllerIntegrationTest {

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
    void error_endpoint_preserves_404_status_and_classifies_as_resource_not_found() throws Exception {
        mockMvc.perform(get("/error")
                        .with(user("user@example.com").roles("USER"))
                        .with(req -> {
                            req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
                            req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/some/missing/path");
                            return req;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    @Test
    void error_endpoint_preserves_405_status_and_classifies_as_method_not_allowed() throws Exception {
        mockMvc.perform(get("/error")
                        .with(user("user@example.com").roles("USER"))
                        .with(req -> {
                            req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 405);
                            req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/some/path");
                            return req;
                        }))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.METHOD_NOT_ALLOWED.code()));
    }

    @Test
    void error_endpoint_falls_back_to_unhandled_client_error_for_unmapped_4xx() throws Exception {
        mockMvc.perform(get("/error")
                        .with(user("user@example.com").roles("USER"))
                        .with(req -> {
                            req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 418);
                            req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/teapot");
                            return req;
                        }))
                .andExpect(status().is(418))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.UNHANDLED_CLIENT_ERROR.code()));
    }

    @Test
    void error_endpoint_collapses_5xx_to_common_999_while_preserving_status() throws Exception {
        mockMvc.perform(get("/error")
                        .with(user("user@example.com").roles("USER"))
                        .with(req -> {
                            req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 503);
                            req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/some/path");
                            return req;
                        }))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()));
    }

    @Test
    void error_endpoint_treats_missing_status_attribute_as_500() throws Exception {
        mockMvc.perform(get("/error").with(user("user@example.com").roles("USER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()));
    }

    @Test
    void error_endpoint_handles_unknown_status_code_safely() throws Exception {
        mockMvc.perform(get("/error")
                        .with(user("user@example.com").roles("USER"))
                        .with(req -> {
                            req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 999);
                            req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/some/path");
                            return req;
                        }))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()));
    }
}
