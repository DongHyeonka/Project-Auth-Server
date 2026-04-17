package com.project.auth.application.support.audit;

import com.project.auth.domain.user.model.UserEmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class AuthAuditFields {

    public static final String EVENT_TYPE = "eventType";
    public static final String ACTOR_ID = "actorId";
    public static final String USER_ID_HASH = "userIdHash";
    public static final String EMAIL_MASKED = "emailMasked";
    public static final String PROVIDER = "provider";
    public static final String REASON = "reason";
    public static final String METHOD = "method";
    public static final String REQUEST_PATH = "requestPath";
    public static final String KEY_ID = "keyId";
    public static final String EXPIRES_IN_SECONDS = "expiresInSeconds";
    public static final String SUBJECT = "subject";

    private static final String HASH_PREFIX = "sha256:";
    private static final int HASH_HEX_LENGTH = 16;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private AuthAuditFields() {
    }

    public static String maskedEmail(UserEmail email) {
        Objects.requireNonNull(email, "email must not be null");
        return maskEmail(email.value());
    }

    public static String userIdHash(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return HASH_PREFIX + sha256Hex(userId.toString()).substring(0, HASH_HEX_LENGTH);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        return localPartPrefix(localPart) + "***@" + domain;
    }

    private static String localPartPrefix(String localPart) {
        if (localPart.length() == 1) {
            return localPart;
        }
        return localPart.substring(0, Math.min(localPart.length(), 2));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
    }
}
