# 2026-05-04 application Tier 1 95% 도달

## 한 줄 결론

`application.support.exception` 패키지 **100%**, `application.support.logging` 패키지 **96.3%**로 Tier 1 임계치(95%) 통과. dead code 1개(`LogSanitizer.reason`) 제거로 자연 정리 동시 수행. SHA-256 NoSuchAlgorithmException catch 2줄은 환경 의존이라 자연 미커버 인정.

## 변경 범위

신규 단위 테스트 3개:
- `BusinessExceptionTest` — 4개 protected 생성자 + getErrorCode 직접 호출
- `ErrorCodeEnumTest` — `CommonErrorCode`/`AuthErrorCode` 모든 enum 값에 대해 code()/message() 단언 (parameterized)
- `LogSanitizerEdgeCaseTest` — single-arg normalize, malformed email, 비숫자 IPv4 옥텟 등 jqwik이 도달하기 어려운 분기

코드 정리:
- `LogSanitizer.reason(String)` 메서드 제거 — 어디서도 호출되지 않는 dead code

## Before / After 수치 (application 모듈 단위 측정)

### Tier 1 패키지

| 패키지 | BEFORE LINE | AFTER LINE | 변화 |
|---|---|---|---|
| `application/support/exception` | 40.6% | **100.0%** | +59.4%p |
| `application/support/logging` (LogSanitizer) | 90.9% | **96.3%** | +5.4%p |

### application 모듈 전체

| Counter | BEFORE | AFTER |
|---|---|---|
| LINE | 70.9% | 83.3% |
| BRANCH | 78.9% | 84.2% |
| METHOD | 63.6% | 81.5% |
| CLASS | 81.2% | 87.5% |

## 자동 검출 가능해진 회귀

- `BusinessException` 4개 생성자 중 하나가 깨지면 `application:test`에서 즉시 실패
- ErrorCode enum 인스턴스 추가 시 자동으로 단언 대상에 포함
- LogSanitizer 비숫자 옥텟 처리 회귀 즉시 검출

## 잔여 미커버 (2줄, 자연 인정)

`LogSanitizer.sha256Hex`의 `NoSuchAlgorithmException` catch 블록 2줄:

```java
} catch (NoSuchAlgorithmException exception) {
    throw new IllegalStateException("SHA-256 digest is not available.", exception);
}
```

JDK가 SHA-256을 미지원하는 환경은 표준 JRE에서 발생하지 않으므로 단위 테스트로 도달 불가. PIT에서도 mutation 의미 없음. **운영 가시성 안전망(throw IllegalStateException)으로서 유지하되 측정 게이트의 미커버는 인정.**

## 측정 조건

| 항목 | 값 |
|---|---|
| BEFORE 커밋 | `7756028` (presentation Tier 1 직후) |
| AFTER 커밋 | (본 커밋) |
| 측정 명령어 | `./gradlew clean test jacocoTestReport` |

## 첨부

- AFTER 스냅샷: `coverage-history/v0.0.1-SNAPSHOT/2026-05-04-application-tier1/`
