package com.project.auth.application.support.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AuthAuditEvent(AuditLevel level, String eventType, Map<String, String> fields) {

    public AuthAuditEvent {
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static AuthAuditEvent info(String eventType, Object... keyValues) {
        return new AuthAuditEvent(AuditLevel.INFO, eventType, toFields(keyValues));
    }

    public static AuthAuditEvent warn(String eventType, Object... keyValues) {
        return new AuthAuditEvent(AuditLevel.WARN, eventType, toFields(keyValues));
    }

    private static Map<String, String> toFields(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must contain an even number of elements");
        }

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object key = Objects.requireNonNull(keyValues[index], "field key must not be null");
            Object value = Objects.requireNonNull(keyValues[index + 1], "field value must not be null");
            fields.put(key.toString(), value.toString());
        }
        return fields;
    }
}
