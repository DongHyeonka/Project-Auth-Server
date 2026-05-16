# 2026-05-04 presentation Tier 1 95% 도달

## 한 줄 결론

`presentation.support.exception` 4개 핸들러에 단위 테스트(컨테이너 우회, 동적 프록시 + 진짜 생성자)를 추가하여 모듈 단위 측정 기준 패키지 라인 커버리지가 **24.8% → 97.9% (+73.1%p)**, Tier 1 임계치(95%) 안정적 통과. ApiErrorHttpStatusMapper / RequestExceptionHandler / ApplicationExceptionHandler는 100%.

## 변경 범위

신규 단위 테스트 4개 클래스:
- `RequestExceptionHandlerTest` — 13개 핸들러 메서드 직접 호출 (HttpMessageNotReadable, MethodNotSupported, MissingParameter, TypeMismatch, MediaTypeNotSupported/NotAcceptable, MissingHeader, ServletRequestBinding, NoResourceFound, MaxUploadSize, ResponseStatus 4xx/5xx, ErrorResponseException)
- `ApplicationExceptionHandlerTest` — 4개 분기 (BusinessException 매핑, MessageNotWritable committed/uncommitted, Exception 안전망)
- `ValidationExceptionHandlerTest` — MethodArgumentNotValid(field+global), null 메시지 폴백, ConstraintViolation, HandlerMethodValidation(paramName 있음/없음)
- `ApiErrorHttpStatusMapperDefaultBranchTest` — non-sealed default 분기 트리거

기존 통합 테스트는 모두 유지 — 핸들러 동작 종합 검증으로서 정책 13(다층 안전망)에 따라 같은 영역을 두 축으로 강제.

## Before / After 수치 (presentation 모듈 단위 측정)

### 패키지 `presentation.support.exception`

| Counter | BEFORE | AFTER | 변화 |
|---|---|---|---|
| LINE | 24.8% | **97.9%** | +73.1%p |
| BRANCH | 42.3% | **85.5%** | +43.2%p |

### 클래스별 (AFTER)

| 클래스 | LINE | BRANCH |
|---|---|---|
| `ApiErrorHttpStatusMapper` | 100.0% | 100.0% |
| `RequestExceptionHandler` | 100.0% | 100.0% |
| `ApplicationExceptionHandler` | 100.0% | 100.0% |
| `PresentationErrorCode` | 100.0% | (no branch) |
| `ValidationExceptionHandler` | 94.2% (6 miss) | 80.4% (11 miss) |

### presentation 모듈 전체

| Counter | BEFORE | AFTER |
|---|---|---|
| LINE | 24.8% | **90.4%** |
| BRANCH | 42.3% | 83.3% |
| METHOD | 25.0% | 84.6% |

## 자동 검출 가능해진 회귀

- 핸들러 한 메서드를 깨면 `presentation:test` 단계에서 즉시 실패 — 통합 테스트가 다른 모듈에 있어도 무관하게 검증
- jacocoAggregateReport 없이도 운영 빌드의 모듈별 게이트만으로 회귀 차단 가능

## 구현 메모

- Mockito 의존성 도입 없이 **JDK 동적 프록시**로 HttpServletRequest/Response stub 작성
- `ParameterValidationResult`는 클래스이므로 동적 프록시 불가 → 7-arg 생성자를 그대로 사용
- 핸들러 메서드는 패키지-private이라 같은 패키지 테스트가 직접 호출 가능

## 측정 조건

| 항목 | 값 |
|---|---|
| BEFORE 커밋 | `e3693af` (aggregate 도입 직후) |
| AFTER 커밋 | (본 커밋) |
| 측정 명령어 | `./gradlew clean test jacocoTestReport` |
| 비교 대상 | presentation 모듈 단위 측정 (모듈 격리 측정 — 통합 테스트 제외) |

## 한계 / 미해결

- `ValidationExceptionHandler`의 BRANCH 80.4% — 11줄 미커버는 주로 `HandlerMethodValidationException`의 부수 분기와 메시지 폴백의 일부. 다음 작업에서 추가 케이스로 95% 도달 가능.
- application Tier 1 패키지(`application/support/exception`, `application/support/audit`)는 아직 단위 테스트 부족 — 다음 사건(3번)에서 처리.

## 첨부

- AFTER 스냅샷: `coverage-history/v0.0.1-SNAPSHOT/2026-05-04-presentation-tier1/`
