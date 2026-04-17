package com.project.auth.config.auth.security;

import com.project.auth.application.support.audit.AuthAuditEventType;
import com.project.auth.application.support.audit.AuthAuditFields;
import com.project.auth.config.logging.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger audit = LoggerFactory.getLogger("audit.auth");
    private static final String OAUTH_LOGIN_COMPLETION_PATH = "/api/v1/auth/oauth2/complete";

    public OAuth2LoginSuccessHandler() {
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        audit.atInfo()
                .addKeyValue(AuthAuditFields.EVENT_TYPE, AuthAuditEventType.OAUTH_AUTHENTICATION_SUCCESS.code())
                .addKeyValue(AuthAuditFields.ACTOR_ID, LogSanitizer.actorId(authentication.getName()))
                .log(AuthAuditEventType.OAUTH_AUTHENTICATION_SUCCESS.code());
        setDefaultTargetUrl(OAUTH_LOGIN_COMPLETION_PATH);
        clearAuthenticationAttributes(request);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
