package com.project.auth.presentation.openid;

import java.util.Map;

public interface OpenIdDiscoveryDocumentProvider {

    Map<String, Object> openIdConfiguration();

    Map<String, Object> jwkSet();
}
