package com.project.auth.presentation.openid.controller;

import com.project.auth.presentation.openid.OpenIdDiscoveryDocumentProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OpenIdDiscoveryController {

    private final OpenIdDiscoveryDocumentProvider openIdDiscoveryDocumentProvider;

    public OpenIdDiscoveryController(OpenIdDiscoveryDocumentProvider openIdDiscoveryDocumentProvider) {
        this.openIdDiscoveryDocumentProvider = openIdDiscoveryDocumentProvider;
    }

    @GetMapping(value = "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openIdConfiguration() {
        return openIdDiscoveryDocumentProvider.openIdConfiguration();
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwkSet() {
        return openIdDiscoveryDocumentProvider.jwkSet();
    }
}
