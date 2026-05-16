package com.project.auth.application.support.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jqwik 속성 테스트가 도달하기 어려운 LogSanitizer의 edge case를 직접 호출.
 *
 * 환경 의존(SHA-256 미지원) 분기는 단위 테스트로도 트리거 불가하므로 미커버 인정.
 */
class LogSanitizerEdgeCaseTest {

    @Test
    void normalize_single_arg_uses_default_max_length() {
        // public single-arg normalize(value)는 normalize(value, DEFAULT_MAX_LENGTH)에 위임
        assertThat(LogSanitizer.normalize("hello")).isEqualTo("hello");
    }

    @ParameterizedTest
    @ValueSource(strings = {"@example.com", "user@", "@"})
    void mask_email_returns_triple_star_for_malformed_emails(String malformed) {
        // atIndex <= 0 또는 atIndex == length-1 → "***"
        assertThat(LogSanitizer.actorId(malformed)).isEqualTo("***");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.2.3.999",   // value > 255
            "1.2.3.1234",  // octet.length() > 3
            "1.2.3.abc"    // !Character.isDigit
    })
    void client_ip_falls_back_to_hash_when_octet_invalid(String input) {
        String result = LogSanitizer.clientIp(input);
        assertThat(result).startsWith("sha256:");
    }
}
