package com.project.auth.presentation.support.exception;

final class LogValueSanitizer {

    private static final int MAX_LENGTH = 200;

    private LogValueSanitizer() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        StringBuilder builder = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
        for (int index = 0; index < value.length() && builder.length() < MAX_LENGTH; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character) || character == '='
                    || character == '|') {
                builder.append('_');
            } else {
                builder.append(character);
            }
        }
        if (value.length() > MAX_LENGTH) {
            builder.append("...");
        }
        return builder.toString();
    }
}
