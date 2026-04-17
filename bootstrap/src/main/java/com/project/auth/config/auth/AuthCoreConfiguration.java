package com.project.auth.config.auth;

import com.project.auth.application.auth.login.LoginService;
import com.project.auth.application.auth.login.LoginAuthenticationService;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.auth.oauth.login.OAuthLoginService;
import com.project.auth.application.auth.oauth.login.OAuthUserRegistrationService;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.support.audit.AuthAuditEventPublisher;
import com.project.auth.application.user.signup.SignUpRegistrationService;
import com.project.auth.application.user.signup.SignUpService;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.infrastructure.security.password.BcryptPasswordEncoderAdapter;
import com.project.auth.infrastructure.security.token.NimbusJwtTokenIssuerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.nimbusds.jose.JWSSigner;

import java.time.Clock;

@Configuration
public class AuthCoreConfiguration {

    @Bean
    public BcryptPasswordEncoderAdapter bcryptPasswordEncoderAdapter() {
        return new BcryptPasswordEncoderAdapter();
    }

    @Bean
    public PasswordHasherPort passwordHasherPort(BcryptPasswordEncoderAdapter bcryptPasswordEncoderAdapter) {
        return bcryptPasswordEncoderAdapter;
    }

    @Bean
    public PasswordVerifierPort passwordVerifierPort(BcryptPasswordEncoderAdapter bcryptPasswordEncoderAdapter) {
        return bcryptPasswordEncoderAdapter;
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SignUpUseCase signUpUseCase(
            SignUpRegistrationService signUpRegistrationService
    ) {
        return new SignUpService(signUpRegistrationService);
    }

    @Bean
    public SignUpRegistrationService signUpRegistrationService(
            RegisterUserPort registerUserPort,
            PasswordHasherPort passwordHasherPort,
            Clock systemClock,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        return new SignUpRegistrationService(
                registerUserPort,
                passwordHasherPort,
                systemClock,
                authAuditEventPublisher
        );
    }

    @Bean
    public IssueLoginTokenPort issueLoginTokenPort(
            JwtProperties jwtProperties,
            JwtSigningKeyMaterial jwtSigningKeyMaterial,
            JWSSigner jwtSigner,
            Clock systemClock
    ) {
        return new NimbusJwtTokenIssuerAdapter(
                jwtProperties.issuer(),
                jwtSigningKeyMaterial.activeKeyId(),
                jwtSigner,
                jwtProperties.accessTokenExpiration(),
                systemClock
        );
    }

    @Bean
    public IssueOAuthLoginTokenPort issueOAuthLoginTokenPort(IssueLoginTokenPort issueLoginTokenPort) {
        return issueLoginTokenPort::issue;
    }

    @Bean
    public LoginUseCase loginUseCase(
            LoginAuthenticationService loginAuthenticationService,
            IssueLoginTokenPort issueLoginTokenPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        return new LoginService(
                loginAuthenticationService,
                issueLoginTokenPort,
                authAuditEventPublisher
        );
    }

    @Bean
    public LoginAuthenticationService loginAuthenticationService(
            LoadLoginUserPort loadLoginUserPort,
            PasswordVerifierPort passwordVerifierPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        return new LoginAuthenticationService(
                loadLoginUserPort,
                passwordVerifierPort,
                authAuditEventPublisher
        );
    }

    @Bean
    public OAuthLoginUseCase oAuthLoginUseCase(
            OAuthUserRegistrationService oAuthUserRegistrationService,
            IssueOAuthLoginTokenPort issueOAuthLoginTokenPort,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        return new OAuthLoginService(
                oAuthUserRegistrationService,
                issueOAuthLoginTokenPort,
                authAuditEventPublisher
        );
    }

    @Bean
    public OAuthUserRegistrationService oAuthUserRegistrationService(
            LoadOAuthUserPort loadOAuthUserPort,
            RegisterOAuthUserPort registerOAuthUserPort,
            Clock systemClock,
            AuthAuditEventPublisher authAuditEventPublisher
    ) {
        return new OAuthUserRegistrationService(
                loadOAuthUserPort,
                registerOAuthUserPort,
                systemClock,
                authAuditEventPublisher
        );
    }
}
