package com.project.auth;

import com.project.auth.config.web.TraceIdFilter;
import com.project.auth.presentation.support.exception.PresentationErrorCode;
import com.project.auth.presentation.support.exception.ValidationExceptionHandler;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(ValidationExceptionHandlerIntegrationTest.ValidationTestConfiguration.class)
class ValidationExceptionHandlerIntegrationTest {

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
    void class_level_validation_failure_is_reported_under_global_bucket() throws Exception {
        String body = """
                { "password": "abc", "passwordConfirm": "different" }
                """;

        mockMvc.perform(post("/test-validation/signup")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.errors['" + ValidationExceptionHandler.GLOBAL_ERROR_KEY + "']").isArray())
                .andExpect(jsonPath("$.errors['" + ValidationExceptionHandler.GLOBAL_ERROR_KEY + "'][0]")
                        .value("passwords must match"));
    }

    @Test
    void field_level_and_class_level_violations_coexist_in_response() throws Exception {
        String body = """
                { "password": "", "passwordConfirm": "different" }
                """;

        mockMvc.perform(post("/test-validation/signup")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['/password']").isArray())
                .andExpect(jsonPath("$.errors['" + ValidationExceptionHandler.GLOBAL_ERROR_KEY + "']").isArray());
    }

    @Test
    void constraint_violation_uses_json_pointer_for_property_path() throws Exception {
        mockMvc.perform(get("/test-validation/search")
                        .with(user("user@example.com").roles("USER"))
                        .param("q", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(PresentationErrorCode.CONSTRAINT_VIOLATION.code()))
                .andExpect(jsonPath("$.errors['/search/q']").exists());
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PasswordsMatchValidator.class)
    public @interface PasswordsMatch {
        String message() default "passwords must match";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, SignupRequest> {
        @Override
        public boolean isValid(SignupRequest value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (value.password() == null || value.passwordConfirm() == null) {
                return true;
            }
            return value.password().equals(value.passwordConfirm());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ValidationTestConfiguration {

        @Bean
        ValidationTestController validationTestController() {
            return new ValidationTestController();
        }
    }

    @RestController
    @Validated
    static class ValidationTestController {

        @PostMapping("/test-validation/signup")
        String signup(@Valid @RequestBody SignupRequest request) {
            return "ok";
        }

        @GetMapping("/test-validation/search")
        String search(@RequestParam @Size(min = 2, message = "must be at least 2 chars") String q) {
            return q;
        }
    }

    @PasswordsMatch
    public record SignupRequest(
            @NotBlank(message = "password is required") String password,
            String passwordConfirm
    ) {
    }
}
