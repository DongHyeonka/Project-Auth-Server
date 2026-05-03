package com.project.auth.presentation.support.exception;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ValidationExceptionHandler.fieldFieldToJsonPointer 속성 기반 테스트.
 *
 * 검증 속성:
 * - null/empty 입력은 "/" 반환.
 * - 정상 입력에 대해 결과는 항상 "/"로 시작한다.
 * - 결과에 dot('.') 또는 bracket('['/']')은 그대로 노출되지 않는다(JSON Pointer 형식이므로).
 * - 동일 입력은 항상 동일 출력 (결정론).
 * - "user.email" 같은 dotted path는 두 segment로 분할된다.
 * - "items[0]" 같은 indexed path는 인덱스가 별도 segment로 분리된다.
 */
class JsonPointerConversionPropertyTest {

    @Property
    void empty_or_null_returns_root_pointer(@ForAll("emptyOrNull") String input) {
        assertThat(ValidationExceptionHandler.fieldFieldToJsonPointer(input)).isEqualTo("/");
    }

    @Property
    void result_always_starts_with_slash(@ForAll("nonEmptyField") String field) {
        String result = ValidationExceptionHandler.fieldFieldToJsonPointer(field);
        assertThat(result).startsWith("/");
    }

    @Property
    void result_is_deterministic(@ForAll("nonEmptyField") String field) {
        assertThat(ValidationExceptionHandler.fieldFieldToJsonPointer(field))
                .isEqualTo(ValidationExceptionHandler.fieldFieldToJsonPointer(field));
    }

    @Property
    void dotted_path_splits_into_segments(
            @ForAll @AlphaChars @StringLength(min = 1, max = 10) String head,
            @ForAll @AlphaChars @StringLength(min = 1, max = 10) String tail
    ) {
        String input = head + "." + tail;
        String result = ValidationExceptionHandler.fieldFieldToJsonPointer(input);
        assertThat(result).isEqualTo("/" + head + "/" + tail);
    }

    @Property
    void indexed_path_extracts_index_segment(
            @ForAll @AlphaChars @StringLength(min = 1, max = 10) String head,
            @ForAll @IntRange(min = 0, max = 9999) int index
    ) {
        String input = head + "[" + index + "]";
        String result = ValidationExceptionHandler.fieldFieldToJsonPointer(input);
        assertThat(result).isEqualTo("/" + head + "/" + index);
    }

    @Property
    void nested_indexed_path_combines_correctly(
            @ForAll @AlphaChars @StringLength(min = 1, max = 8) String parent,
            @ForAll @IntRange(min = 0, max = 99) int index,
            @ForAll @AlphaChars @StringLength(min = 1, max = 8) String child
    ) {
        String input = parent + "[" + index + "]." + child;
        String result = ValidationExceptionHandler.fieldFieldToJsonPointer(input);
        assertThat(result).isEqualTo("/" + parent + "/" + index + "/" + child);
    }

    @Property
    void result_never_contains_unescaped_input_slash(
            @ForAll @AlphaChars @StringLength(min = 1, max = 5) String head,
            @ForAll @AlphaChars @StringLength(min = 1, max = 5) String tail
    ) {
        // RFC 6901: 입력의 '/' 는 ~1 로 이스케이프되어야 한다
        String input = head + "/" + tail; // segment 안에 슬래시가 들어간 케이스
        String result = ValidationExceptionHandler.fieldFieldToJsonPointer(input);
        // 이 경로는 dot도 bracket도 없으므로 단일 segment로 처리되어야 한다
        assertThat(result).isEqualTo("/" + head + "~1" + tail);
    }

    @Property
    void result_escapes_tilde_in_segments(
            @ForAll @AlphaChars @StringLength(min = 1, max = 5) String head,
            @ForAll @AlphaChars @StringLength(min = 1, max = 5) String tail
    ) {
        String input = head + "~" + tail;
        String result = ValidationExceptionHandler.fieldFieldToJsonPointer(input);
        assertThat(result).isEqualTo("/" + head + "~0" + tail);
    }

    @Provide
    Arbitrary<String> emptyOrNull() {
        return Arbitraries.of("", null);
    }

    @Provide
    Arbitrary<String> nonEmptyField() {
        Arbitrary<String> simple = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<String> dotted = simple.flatMap(a -> simple.map(b -> a + "." + b));
        Arbitrary<String> indexed = simple.flatMap(a ->
                Arbitraries.integers().between(0, 100).map(i -> a + "[" + i + "]"));
        return Arbitraries.oneOf(simple, dotted, indexed);
    }
}
