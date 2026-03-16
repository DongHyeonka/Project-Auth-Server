package com.project.auth.config.auth;

import com.project.auth.application.auth.login.LoginService;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.auth.oauth.login.OAuthLoginService;
import com.project.auth.application.auth.oauth.login.port.in.OAuthLoginUseCase;
import com.project.auth.application.auth.oauth.login.port.out.IssueOAuthLoginTokenPort;
import com.project.auth.application.auth.oauth.login.port.out.LoadOAuthUserPort;
import com.project.auth.application.auth.oauth.login.port.out.RegisterOAuthUserPort;
import com.project.auth.application.user.signup.SignUpService;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.infrastructure.security.password.BcryptPasswordEncoderAdapter;
import com.project.auth.infrastructure.security.token.NimbusJwtTokenIssuerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;

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
            RegisterUserPort registerUserPort,
            PasswordHasherPort passwordHasherPort,
            Clock systemClock
    ) {
        return new SignUpService(registerUserPort, passwordHasherPort, systemClock);
    }

    @Bean
    public IssueLoginTokenPort issueLoginTokenPort(
            JwtProperties jwtProperties,
            JwtSigningKeyMaterial jwtSigningKeyMaterial,
            JwtEncoder jwtEncoder,
            Clock systemClock
    ) {
        return new NimbusJwtTokenIssuerAdapter(
                jwtProperties.issuer(),
                jwtSigningKeyMaterial.activeKeyId(),
                jwtEncoder,
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
            LoadLoginUserPort loadLoginUserPort,
            PasswordVerifierPort passwordVerifierPort,
            IssueLoginTokenPort issueLoginTokenPort
    ) {
        return new LoginService(loadLoginUserPort, passwordVerifierPort, issueLoginTokenPort);
    }

    @Bean
    public OAuthLoginUseCase oAuthLoginUseCase(
            LoadOAuthUserPort loadOAuthUserPort,
            RegisterOAuthUserPort registerOAuthUserPort,
            IssueOAuthLoginTokenPort issueOAuthLoginTokenPort,
            Clock systemClock
    ) {
        return new OAuthLoginService(
                loadOAuthUserPort,
                registerOAuthUserPort,
                issueOAuthLoginTokenPort,
                systemClock
        );
    }
}
