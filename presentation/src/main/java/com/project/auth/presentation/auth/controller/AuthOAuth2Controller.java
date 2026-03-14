package com.project.auth.presentation.auth.controller;

import com.project.auth.presentation.auth.docs.AuthOAuth2ApiDocs;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@Tag(name = "Auth", description = "OAuth2 소셜 로그인 API")
public class AuthOAuth2Controller implements AuthOAuth2ApiDocs {

    private static final String GOOGLE_AUTHORIZATION_PATH = "/oauth2/authorization/keycloak-google";
    private static final String GITHUB_AUTHORIZATION_PATH = "/oauth2/authorization/keycloak-github";

    @Override
    @GetMapping("/keycloak/google")
    public ResponseEntity<Void> loginWithGoogle() {
        return redirect(GOOGLE_AUTHORIZATION_PATH);
    }

    @Override
    @GetMapping("/keycloak/github")
    public ResponseEntity<Void> loginWithGithub() {
        return redirect(GITHUB_AUTHORIZATION_PATH);
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(path).toString())
                .build();
    }
}
