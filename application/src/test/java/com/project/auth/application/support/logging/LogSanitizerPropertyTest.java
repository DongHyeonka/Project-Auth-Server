package com.project.auth.application.support.logging;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LogSanitizer 속성 기반 테스트.
 *
 * 검증 대상 속성:
 * - normalize: null/blank → "-", 결과 길이 한도 보장(단, "..." 접미사 허용),
 *              제어/공백/'='/'|' 문자 제거.
 * - clientIp:  IPv4 형식이면 마지막 옥텟이 0으로 마스킹, 그 외는 sha256 prefix.
 * - actorId:   이메일이면 마스킹, 그 외 anonymous/blank/null은 "anonymous", 나머지는 sha256 prefix.
 * - requestPath: 일반 normalize와 동일 정책 적용 + 더 큰 길이 한도(200).
 */
class LogSanitizerPropertyTest {

    private static final int DEFAULT_MAX = 160;
    private static final int REQUEST_PATH_MAX = 200;
    private static final int USER_AGENT_MAX = 120;

    @Property
    void normalize_null_or_blank_returns_dash(@ForAll @From("blankOrNull") String input) {
        assertThat(LogSanitizer.normalize(input)).isEqualTo("-");
    }

    @Property
    void normalize_strips_control_whitespace_equals_pipe_chars(@ForAll @StringLength(min = 1, max = 300) String raw) {
        String result = LogSanitizer.normalize(raw);
        if (result.equals("-")) {
            return;
        }
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            // "..." 접미사 자체는 정규화되지 않는 평문이므로 통과
            if (ch == '.') continue;
            assertThat(Character.isISOControl(ch))
                    .as("ISO control char leaked at index %d", i)
                    .isFalse();
            assertThat(ch == '=' || ch == '|')
                    .as("forbidden delimiter '=' or '|' leaked at index %d", i)
                    .isFalse();
            assertThat(Character.isWhitespace(ch) && ch != '_')
                    .as("whitespace leaked at index %d", i)
                    .isFalse();
        }
    }

    @Property
    void normalize_output_length_is_bounded_by_max_plus_three(@ForAll @StringLength(min = 0, max = 1000) String raw) {
        String result = LogSanitizer.normalize(raw);
        if (result.equals("-")) {
            return;
        }
        // sanitizer는 maxLength까지의 문자 + 입력이 maxLength 초과 시 "..." 접미사를 붙인다
        assertThat(result.length()).isLessThanOrEqualTo(DEFAULT_MAX + 3);
    }

    @Property
    void requestPath_output_length_is_bounded(@ForAll @StringLength(min = 0, max = 1000) String raw) {
        String result = LogSanitizer.requestPath(raw);
        if (result.equals("-")) {
            return;
        }
        assertThat(result.length()).isLessThanOrEqualTo(REQUEST_PATH_MAX + 3);
    }

    @Property
    void userAgent_output_length_is_bounded(@ForAll @StringLength(min = 0, max = 1000) String raw) {
        String result = LogSanitizer.userAgent(raw);
        if (result.equals("-")) {
            return;
        }
        assertThat(result.length()).isLessThanOrEqualTo(USER_AGENT_MAX + 3);
    }

    @Property
    void clientIp_with_valid_ipv4_masks_last_octet(@ForAll("ipv4") String ipv4) {
        String result = LogSanitizer.clientIp(ipv4);
        assertThat(result).endsWith(".0");
        assertThat(result.split("\\.")).hasSize(4);
    }

    @Property
    void clientIp_with_non_ipv4_falls_back_to_hash(@ForAll @StringLength(min = 1, max = 200) String value) {
        // IPv4 형식이면 그대로 하위 검증을 통과하므로 이 속성에서는 제외
        if (looksLikeIpv4(value)) return;
        String result = LogSanitizer.clientIp(value);
        if ("-".equals(result)) {
            assertThat(value.trim()).isEmpty();
            return;
        }
        assertThat(result).startsWith("sha256:");
        // sha256 hex prefix는 16자
        assertThat(result.length()).isEqualTo("sha256:".length() + 16);
    }

    @Property
    void clientIp_for_blank_returns_dash(@ForAll @From("blankOrNull") String input) {
        assertThat(LogSanitizer.clientIp(input)).isEqualTo("-");
    }

    @Property
    void actorId_for_blank_anonymous_returns_anonymous(@ForAll @From("blankOrNullOrAnonymous") String input) {
        assertThat(LogSanitizer.actorId(input)).isEqualTo("anonymous");
    }

    @Property
    void actorId_for_email_masks_local_part(@ForAll("email") String email) {
        String result = LogSanitizer.actorId(email);
        assertThat(result).contains("@");
        // local prefix는 최대 2글자 + ***
        int atIndex = result.indexOf('@');
        String localResult = result.substring(0, atIndex);
        assertThat(localResult).endsWith("***");
        // 마스킹 결과 길이는 prefix(1~2) + "***" = 4~5
        assertThat(localResult.length()).isBetween(4, 5);
    }

    @Property
    void actorId_for_non_email_non_blank_returns_hash_prefix(
            @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        if (value.isBlank() || "anonymous".equals(value) || value.contains("@")) return;
        String result = LogSanitizer.actorId(value);
        assertThat(result).startsWith("sha256:");
        assertThat(result.length()).isEqualTo("sha256:".length() + 16);
    }

    // ---------- Arbitraries ----------

    @Provide
    Arbitrary<String> blankOrNull() {
        return Arbitraries.of("", " ", "   ", "\t", "\n", null);
    }

    @Provide
    Arbitrary<String> blankOrNullOrAnonymous() {
        return Arbitraries.of("", " ", "anonymous", null);
    }

    @Provide
    Arbitrary<String> ipv4() {
        Arbitrary<Integer> octet = Arbitraries.integers().between(0, 255);
        return octet.flatMap(a ->
                octet.flatMap(b ->
                        octet.flatMap(c ->
                                octet.map(d -> a + "." + b + "." + c + "." + d))));
    }

    @Provide
    Arbitrary<String> email() {
        Arbitrary<String> local = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> domain = Arbitraries.strings()
                .alpha().ofMinLength(2).ofMaxLength(20);
        return local.flatMap(l -> domain.map(d -> l + "@" + d + ".com"));
    }

    private static boolean looksLikeIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) return false;
            for (int i = 0; i < p.length(); i++) {
                if (!Character.isDigit(p.charAt(i))) return false;
            }
            int n = Integer.parseInt(p);
            if (n > 255) return false;
        }
        return true;
    }
}
