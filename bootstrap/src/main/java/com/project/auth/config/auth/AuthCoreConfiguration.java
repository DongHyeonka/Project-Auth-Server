package com.project.auth.config.auth;

import com.project.auth.application.auth.login.LoginService;
import com.project.auth.application.auth.login.port.in.LoginUseCase;
import com.project.auth.application.auth.login.port.out.IssueLoginTokenPort;
import com.project.auth.application.auth.login.port.out.LoadLoginUserPort;
import com.project.auth.application.auth.login.port.out.PasswordVerifierPort;
import com.project.auth.application.user.signup.SignUpService;
import com.project.auth.application.user.signup.port.in.SignUpUseCase;
import com.project.auth.application.user.signup.port.out.PasswordHasherPort;
import com.project.auth.application.user.signup.port.out.RegisterUserPort;
import com.project.auth.infrastructure.persistence.user.InMemoryUserRepositoryAdapter;
import com.project.auth.infrastructure.security.password.BcryptPasswordEncoderAdapter;
import com.project.auth.infrastructure.security.token.NimbusJwtTokenIssuerAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthCoreConfiguration {

    @Bean
    public InMemoryUserRepositoryAdapter inMemoryUserRepositoryAdapter() {
        return new InMemoryUserRepositoryAdapter();
    }

    @Bean
    public RegisterUserPort registerUserPort(InMemoryUserRepositoryAdapter inMemoryUserRepositoryAdapter) {
        return inMemoryUserRepositoryAdapter;
    }

    @Bean
    public LoadLoginUserPort loadLoginUserPort(InMemoryUserRepositoryAdapter inMemoryUserRepositoryAdapter) {
        return inMemoryUserRepositoryAdapter;
    }

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
    public IssueLoginTokenPort issueLoginTokenPort(JwtProperties jwtProperties, Clock systemClock) {
        return new NimbusJwtTokenIssuerAdapter(
                jwtProperties.issuer(),
                jwtProperties.secret(),
                jwtProperties.accessTokenExpiration(),
                systemClock
        );
    }

    @Bean
    public LoginUseCase loginUseCase(
            LoadLoginUserPort loadLoginUserPort,
            PasswordVerifierPort passwordVerifierPort,
            IssueLoginTokenPort issueLoginTokenPort
    ) {
        return new LoginService(loadLoginUserPort, passwordVerifierPort, issueLoginTokenPort);
    }
}
