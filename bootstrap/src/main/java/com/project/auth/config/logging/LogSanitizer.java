package com.project.auth.config.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class LogSanitizer {

    private static final String UNAVAILABLE_VALUE = "-";
    private static final String ANONYMOUS_VALUE = "anonymous";
    private static final String HASH_PREFIX = "sha256:";
    private static final int HASH_HEX_LENGTH = 16;
    private static final int DEFAULT_MAX_LENGTH = 160;
    private static final int USER_AGENT_MAX_LENGTH = 120;
    private static final int REQUEST_PATH_MAX_LENGTH = 200;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private LogSanitizer() {
    }

    public static String userAgent(String value) {
        return normalize(value, USER_AGENT_MAX_LENGTH);
    }

    public static String clientIp(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE_VALUE;
        }
        String normalized = normalize(value, DEFAULT_MAX_LENGTH);
        String[] octets = normalized.split("\\.", -1);
        if (octets.length == 4 && isIpv4Octets(octets)) {
            return octets[0] + "." + octets[1] + "." + octets[2] + ".0";
        }
        return HASH_PREFIX + sha256Hex(normalized).substring(0, HASH_HEX_LENGTH);
    }

    public static String requestPath(String value) {
        return normalize(value, REQUEST_PATH_MAX_LENGTH);
    }

    public static String reason(String value) {
        return normalize(value, DEFAULT_MAX_LENGTH);
    }

    public static String actorId(String value) {
        if (value == null || value.isBlank() || ANONYMOUS_VALUE.equals(value)) {
            return ANONYMOUS_VALUE;
        }
        String normalized = normalize(value, DEFAULT_MAX_LENGTH);
        if (normalized.contains("@")) {
            return maskEmail(normalized);
        }
        return HASH_PREFIX + sha256Hex(normalized).substring(0, HASH_HEX_LENGTH);
    }

    public static String normalize(String value) {
        return normalize(value, DEFAULT_MAX_LENGTH);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE_VALUE;
        }

        StringBuilder builder = new StringBuilder(Math.min(value.length(), maxLength));
        for (int index = 0; index < value.length() && builder.length() < maxLength; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character) || character == '='
                    || character == '|') {
                builder.append('_');
            } else {
                builder.append(character);
            }
        }
        if (value.length() > maxLength) {
            builder.append("...");
        }
        return builder.toString();
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

    private static boolean isIpv4Octets(String[] octets) {
        for (String octet : octets) {
            if (!isIpv4Octet(octet)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv4Octet(String octet) {
        if (octet.isEmpty() || octet.length() > 3) {
            return false;
        }
        int value = 0;
        for (int index = 0; index < octet.length(); index++) {
            char character = octet.charAt(index);
            if (!Character.isDigit(character)) {
                return false;
            }
            value = value * 10 + Character.digit(character, 10);
        }
        return value <= 255;
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
