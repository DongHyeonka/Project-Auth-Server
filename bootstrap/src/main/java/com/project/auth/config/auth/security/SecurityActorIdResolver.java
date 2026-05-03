package com.project.auth.config.auth.security;

import com.project.auth.application.support.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;

public class SecurityActorIdResolver {

    private static final String ANONYMOUS_PRINCIPAL = "anonymous";

    public String resolve(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return LogSanitizer.actorId(principal.getName());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return LogSanitizer.actorId(authentication.getName());
        }

        return LogSanitizer.actorId(ANONYMOUS_PRINCIPAL);
    }
}
