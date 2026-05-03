# Validation 예외 처리 Deep-Dive

[02-error-handling.md](./02-error-handling.md) 의 단계 3 (`ValidationExceptionHandler`) 에서 다루지 않은 *세부 동작* 만 모은 문서. 핵심 architecture 는 02 가 source of truth.

## Why

기본 처리 (Bean Validation 실패 → 400) 만으로는 클라이언트가 *어느 필드가 왜 실패했는지* 알 수 없다. 또한 `ConstraintViolation` 같은 Jakarta Validation 타입은 HTTP 요청 검증과 `@ConfigurationProperties` 검증 두 곳에 모두 등장하지만 *언제, 어디서, 어떻게* 실패하는지가 다르다. 이를 한 데 묶어서 다루지 않으면 핸들러 구현이 일관성을 잃는다.

## Validation 예외를 응답 데이터로 정규화

validation 계열 예외는 단순히 `"요청 값이 올바르지 않습니다."` 한 줄만 주는 것이 아니라, 어떤 필드가 왜 실패했는지도 함께 내려준다. 이를 위해 `ValidationExceptionHandler` 는 `ApiResult<Map<String, List<String>>>` 를 사용한다.

```java
Map<String, List<String>> errors = new LinkedHashMap<>();
errors.computeIfAbsent(field, key -> new ArrayList<>())
        .add(message);
```

이 구조의 의미는 다음과 같다.

- `String`: 실패한 필드명 또는 파라미터명. 예: `email`, `password`, `title`
- `List<String>`: 해당 필드에서 발생한 검증 메시지 목록. 한 필드에 여러 제약이 동시에 실패할 수 있으므로 리스트로 보관
- `LinkedHashMap`: 검증 오류가 수집된 순서를 최대한 유지해 응답이 매번 같은 모양으로 보이게 함

예를 들어 이메일과 비밀번호가 동시에 실패하면 응답 `data` 는 다음과 비슷해진다.

```json
{
  "success": false,
  "code": "COMMON-001",
  "message": "요청 값이 올바르지 않습니다.",
  "data": {
    "email": [
      "유효한 이메일 형식이 아닙니다."
    ],
    "password": [
      "비밀번호는 8자 이상 50자 이하여야 합니다."
    ]
  },
  "traceId": "4f5c7b90a2de118c",
  "timestamp": "2026-04-10T13:10:00Z"
}
```

한 필드에서 여러 제약이 동시에 실패하면 리스트가 길어진다.

```json
{
  "password": [
    "비어 있을 수 없습니다.",
    "8자 이상이어야 합니다."
  ]
}
```

`computeIfAbsent` 는 *"키가 없으면 기본 컬렉션을 만들고, 있으면 기존 컬렉션을 재사용"* 하는 `Map` 인터페이스의 메서드. 즉 validation 응답에서는 *"처음 등장한 필드면 빈 리스트를 만들고, 이미 있으면 그 리스트에 메시지를 하나 더 추가"* 하는 역할.

## `ConstraintViolation`, `MessageSourceResolvable` 이 실제로 뜻하는 것

validation 예외 흐름에서 자주 보이는 타입은 아래처럼 역할이 다르다.

- `ConstraintViolation`: 검증 실패 1 건을 표현하는 객체. 실패한 경로, 메시지, 잘못된 값 같은 메타데이터를 가짐
- `ConstraintViolationException`: `ConstraintViolation` 여러 건을 모아 던지는 예외
- `MessageSourceResolvable`: Spring 이 메시지 코드, 치환 인자, 기본 메시지를 나중에 해석할 수 있도록 감싼 타입

현재 `ValidationExceptionHandler` 는 이 정보를 이렇게 사용한다.

- `ConstraintViolationException` 처리 시: 각 `ConstraintViolation` 에서 `propertyPath` 와 `message` 를 꺼내 `field → messages` 구조로 정규화
- `HandlerMethodValidationException` 처리 시: Spring 이 준 `MessageSourceResolvable` 목록에서 `getDefaultMessage()` 를 꺼내 응답 메시지로 사용

즉 현재 구현은 다국어 메시지 해석까지는 하지 않고, Spring 이 계산한 기본 메시지를 API 응답에 그대로 실어 주는 쪽에 가깝다. 향후 다국어 응답이 필요해지면 `MessageSourceResolvable` 의 코드와 인자를 이용해 locale 별 메시지로 바꿀 수 있다.

## `ConstraintViolationException` 과 `@ConfigurationProperties` 검증의 관계

`ConstraintViolation` 자체는 HTTP 요청 전용 개념이 아니라 Jakarta Validation 의 공통 모델이다. 그래서 아래처럼 `@Validated` 와 `@NotBlank` 를 붙인 `@ConfigurationProperties` 에도 같은 검증 개념이 적용된다.

```java
@Validated
@ConfigurationProperties(prefix = "app.docs")
public record AppDocsProperties(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String version
) {
}
```

다만 **언제, 어디서 실패하느냐는 완전히 다르다.**

- HTTP 요청 검증: `DispatcherServlet` 이후에 발생하며 `ValidationExceptionHandler` 가 잡아 `400` JSON 응답으로 변환
- `@ConfigurationProperties` 검증: 애플리케이션 시작 시점에 바인딩 / 검증 중 발생하며, 서버가 뜨기 전에 실패함

즉 `AppDocsProperties` 같은 설정 검증 실패는 *"잘못된 요청 값"* 이라기보다 *"잘못된 애플리케이션 설정"* 이다. 이 경우는 `CommonErrorCode.CONSTRAINT_VIOLATION` 으로 API 응답을 만드는 흐름이 아니라, 애플리케이션 부팅 실패로 이어지는 것이 일반적.

정리하면:

- 같은 Bean Validation 애노테이션 (`@NotBlank`, `@Pattern`, `@NotNull`) 을 써도
- 웹 요청 검증은 클라이언트 입력 검증이고
- `@ConfigurationProperties` 검증은 서버 설정 검증이다.

그래서 문구 *"요청 값이 제약 조건을 위반했습니다."* 는 `ValidationExceptionHandler` 가 다루는 웹 요청 검증 컨텍스트에만 정확하게 맞는 표현.

## Result / Trade-offs

### 얻은 이점

- 클라이언트가 어느 필드의 어느 제약이 실패했는지 알 수 있어 폼 UX 가능
- `LinkedHashMap` 으로 응답 순서가 안정적이라 스냅샷 테스트 가능
- 같은 Bean Validation 애노테이션이 *요청 검증* 과 *설정 검증* 두 컨텍스트에서 다르게 흐른다는 점이 문서화되어, 신입이 핸들러 추가 시 잘못된 가정을 줄일 수 있음

### 감수한 비용

- 응답 데이터 형태가 *단순 string* 이 아니라 *map of list of string* 이라 클라이언트 파서가 약간 복잡해짐
- 다국어 메시지 해석은 안 함 (현재 단일 locale 가정) — 추후 i18n 시 `MessageSourceResolvable` 코드 / 인자 사용으로 확장 필요

### 남은 리스크

- validation 응답의 상세 필드 구조가 API 스펙으로 굳어질 경우, 향후 RFC 7807 등 다른 오류 포맷으로 바꿀 때 마이그레이션 비용이 생김
- `@ConfigurationProperties` 검증 실패는 부팅 실패로 이어지므로 helm rollout 시 *"왜 Pod 가 안 뜨지"* 의 원인이 될 수 있음 — 운영 매뉴얼에 *"부팅 실패 시 application.yml 의 검증 항목 먼저 확인"* 추가 권장

## References

- [02-error-handling.md](./02-error-handling.md) — 에러 핸들링 핵심 architecture
- 핸들러 코드: [ValidationExceptionHandler.java](../../../presentation/src/main/java/com/project/auth/presentation/support/exception/ValidationExceptionHandler.java)
- 응답: [ApiResult.java](../../../presentation/src/main/java/com/project/auth/presentation/support/response/ApiResult.java)
- 설정 검증 예시: [AppDocsProperties.java](../../../bootstrap/src/main/java/com/project/auth/config/openapi/AppDocsProperties.java)
