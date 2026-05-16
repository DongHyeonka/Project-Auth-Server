# 예외 처리 정책 (Exception Handling Policy)

이 문서는 본 인증 서버의 **예외 처리·응답·로그·audit 인프라가 표현하는 13가지 정책**과, 각 정책이 코드/테스트의 어느 위치에 어떤 메커니즘으로 박혀 있는지 정리한다. 이 정책은 비즈니스 예외(서비스 로직 도메인 예외)는 다루지 않으며, 그것은 도메인/서비스 설계의 일부로 별도로 관리한다.

## 0. 사용 원칙

각 정책은 다음 4가지 중 **최소 둘 이상**으로 표현되어 있어야 한다. 셋 이상이면 강한 정책이고, 하나뿐이면 회귀 위험이 있다.

1. **타입 시스템** (sealed, 시그니처 좁히기) — 컴파일 단계에서 위반 차단
2. **명시적 단언** (정확값 테스트, classpath 가드) — 런타임 회귀 차단
3. **운영 가시성** (WARN 로그, sentinel 값, audit) — 사일런트 실패 노출
4. **코드 옆 가이드** (Javadoc) — 미래 변경자가 의도를 알 수 있음

> 정책을 새로 추가하거나 기존 정책을 변경할 때는, 위 4가지 중 어느 축으로 강제할지 *설계 결정*을 SUMMARY 문서나 PR 본문에 함께 남긴다.

---

## 정책 1. "내부 에러 코드는 클라이언트 응답 경로에 절대 닿지 않는다"

| 표현 위치 | 메커니즘 |
|---|---|
| `application/ErrorCode.java` | `sealed permits ClientFacingErrorCode, ExternalErrorCode` — 모든 ErrorCode는 두 갈래 중 하나로만 분류 가능 |
| `application/ClientFacingErrorCode.java` | `non-sealed` 마커 — 클라이언트 노출 가능한 코드의 표식 |
| `application/ExternalErrorCode.java` | `non-sealed` 마커 — 내부 분류용 (InfrastructureErrorCode 구현) |
| `presentation/ApiErrorHttpStatusMapper.java` | `map(ClientFacingErrorCode)` — **시그니처가 ExternalErrorCode를 받지 않음** → InfrastructureErrorCode를 매퍼에 넣으면 컴파일 실패 |
| `application/BusinessException.java` | `errorCode` 필드를 `ClientFacingErrorCode`로 좁힘 — BusinessException 생성 시점에 ExternalErrorCode를 넣을 수 없음 |
| `bootstrap/InfrastructureExceptionHandler.java` | InfrastructureException은 별도 핸들러에서 항상 `COMMON-999`로 정규화 |

**검증 방법:** "InfrastructureErrorCode가 클라이언트 응답에 들어간다"는 시나리오는 **컴파일 단계에서 차단**된다. 런타임 테스트가 아니라 타입 시스템.

**축:** 타입 시스템 + 코드 옆 가이드

---

## 정책 2. "신규 ClientFacingErrorCode가 추가되면 매핑/테스트 누락이 자동 감지된다"

| 표현 위치 | 메커니즘 |
|---|---|
| `presentation/ApiErrorHttpStatusMapper.java` `mapXxx` | enum별 exhaustive switch — 새 enum 값이 추가되면 컴파일 실패 |
| `presentation/ApiErrorHttpStatusMapper.java` default 분기 | non-sealed 구현이 늘어난 경우 default 분기가 **WARN 로그**로 운영에 노출 (조용한 500 방지) |
| `bootstrap/test/ApiErrorHttpStatusMapperClientFacingCoverageTest` `exact_mapping_table_must_cover_every_client_facing_error_code_enum_value` | 신규 enum 값이 매핑 테이블에 없으면 테스트 실패 |
| 같은 파일 `every_client_facing_error_code_implementation_on_classpath_is_covered_by_the_table` | 신규 ClientFacingErrorCode **구현체 클래스**가 테이블에 없으면 테스트 실패 (ArchUnit ClassFileImporter로 classpath 스캔) |
| 같은 파일 `each_client_facing_error_code_maps_to_its_exact_expected_http_status` | 모든 enum별로 정확한 status를 표 형식으로 단언 — mutation 저항 |
| 같은 파일 `all_client_facing_error_codes_have_unique_string_codes` | code 문자열 중복 금지 |
| 같은 파일 + jqwik | `ApiErrorHttpStatusMapperPropertyTest` — 모든 enum이 4xx/5xx, 결정론, 401/403 정확값 |

**검증 방법:** 누군가 `BillingErrorCode implements ClientFacingErrorCode`를 추가하면, 매퍼에 분기를 추가하고 테이블에 등록하지 않는 한 **classpath 가드 테스트가 실패**한다.

**축:** 타입 시스템 + 명시적 단언 + 운영 가시성

---

## 정책 3. "@RestControllerAdvice를 우회하는 경로에서도 ApiResult 계약과 status는 보존된다"

| 표현 위치 | 메커니즘 |
|---|---|
| `bootstrap/ApiErrorController.java` `error()` | `/error` 엔드포인트가 컨테이너의 `RequestDispatcher.ERROR_STATUS_CODE`를 직접 읽고 그대로 보존 |
| 같은 파일 본문 작성부 | 본문은 ApiResult로 정규화하되 status는 컨테이너 결정 그대로 |
| 같은 파일 `classify(HttpStatus)` | 5xx → COMMON-999 정규화, 4xx → PresentationErrorCode 분류 매핑 |
| `bootstrap/test/ApiErrorControllerIntegrationTest` | 404/405/418/503/missing/unknown status 모두 status 보존을 검증 |

**검증 방법:** 필터 단 예외나 `sendError(404)` 호출이 `/error`로 흘러가도 클라이언트는 **진짜 404**를 받는다. 통합 테스트로 강제.

**축:** 명시적 단언 + 코드 옆 가이드

---

## 정책 4. "예외는 advice → /error → 무응답 순으로 다층 안전망을 거친다"

| 계층 | 표현 위치 | 책임 |
|---|---|---|
| HIGHEST_PRECEDENCE | `bootstrap/InfrastructureExceptionHandler` | 인프라 장애 → COMMON-999 |
| HIGHEST_PRECEDENCE+5 | `bootstrap/SecurityResponseExceptionHandler` | 인증/인가 예외 → 401/403 + audit |
| HIGHEST_PRECEDENCE+10 | `presentation/ValidationExceptionHandler` | 검증 실패 → 400 + errors |
| HIGHEST_PRECEDENCE+20 | `presentation/RequestExceptionHandler` | 요청 형식 오류 + 프레임워크 throws |
| LOWEST_PRECEDENCE | `presentation/ApplicationExceptionHandler` | BusinessException + MessageNotWritable + **Exception.class 안전망** |
| ErrorController | `bootstrap/ApiErrorController` | advice를 우회한 모든 경로 |

`@Order` 값으로 우선순위가 코드에 명시되어 있고, **`Exception.class` 안전망**이 advice 안에 있어 모든 RuntimeException이 일관된 ApiResult로 정규화된다.

**축:** 코드 옆 가이드(`@Order`) + 명시적 단언(통합 테스트)

---

## 정책 5. "AccessDenied는 익명 401과 인증된 403으로 의미 분기"

| 표현 위치 | 메커니즘 |
|---|---|
| `bootstrap/SecurityResponseExceptionHandler.java` `handleAccessDeniedException` | `isAnonymous(...)` 1차 — SecurityContext 인증 상태 |
| 같은 파일 `isAnonymous(Authentication, HttpServletRequest)` | `request.getUserPrincipal()` 폴백 — 비동기 ThreadLocal 전파 누락 시 잘못된 401 방어 |
| `bootstrap/test/ExceptionHandlingIntegrationTest` `access_denied_for_anonymous_user_maps_to_401_authentication_required` | 익명 401 검증 |
| 같은 파일 `access_denied_for_authenticated_user_remains_403_forbidden` | 인증된 403 검증 |

**축:** 명시적 단언 + 코드 옆 가이드(스레드 가정 Javadoc)

---

## 정책 6. "보안 이벤트는 어느 경로로 들어와도 동일한 audit 채널에 적재된다"

| 진입 경로 | 표현 위치 |
|---|---|
| 필터 단(AuthenticationEntryPoint/AccessDeniedHandler) | `bootstrap/SecurityExceptionHandler` `recordAudit(...)` |
| 컨트롤러 단(@PreAuthorize 등) | `bootstrap/SecurityResponseExceptionHandler` 모든 분기 `recordAudit(...)` |
| 공통 | 둘 다 `SecurityAuditTrailWriter.record(...)` 사용 → `audit.auth` 로거로 동일 형식 적재 |
| audit 실패 격리 | 양쪽 핸들러 모두 try/catch로 격리 — audit 실패가 응답을 막지 않음 |

**축:** 명시적 단언(audit 호출) + 운영 가시성(공통 로거 채널)

---

## 정책 7. "응답 본문은 ApiResult 한 가지 envelope으로 통일된다"

| 표현 위치 | 메커니즘 |
|---|---|
| `presentation/ApiResult.java` | record 형태로 봉인된 응답 envelope |
| 같은 파일 `errors` 필드 | validation 진단을 `data`와 분리 → OpenAPI에서 oneOf 모델링 불필요 |
| 같은 파일 `@JsonInclude(NON_NULL)` | null 필드는 직렬화 제외 |
| `presentation/ApiResultFactory.java` | success/failure/failure(errors) 4가지 진입점만 노출 |
| `bootstrap/RequestBoundApiResultFactory.java` | 모든 응답에 traceId·timestamp 자동 주입 |
| 같은 파일 `MISSING_TRACE_ID = "-"` | TraceIdFilter 누락 시에도 명시적 sentinel 노출 (조용한 null 방지) |

**축:** 타입 시스템(record + interface) + 운영 가시성(sentinel)

---

## 정책 8. "검증 실패 응답은 단일 키 형식(JSON Pointer)을 사용한다"

| 표현 위치 | 메커니즘 |
|---|---|
| `presentation/ValidationExceptionHandler.java` `toJsonPointer(Path)` | ConstraintViolation의 Jakarta Path → JSON Pointer (RFC 6901) |
| 같은 파일 `fieldFieldToJsonPointer(String)` | Spring BindingResult의 dotted/bracketed path → JSON Pointer |
| 같은 파일 `GLOBAL_ERROR_KEY = "__global__"` | 클래스 레벨/cross-field 에러 전용 버킷 — `getGlobalErrors()` 누락 방지 |
| 같은 파일 `UNRESOLVED_VIOLATION_MESSAGE` | null 메시지 폴백 — JSON에 `[null]` 노출 방지 |
| 같은 파일 `escapeJsonPointerSegment` | `~`, `/` 정확한 RFC 6901 이스케이프 |
| `bootstrap/test/ValidationExceptionHandlerIntegrationTest` | 세 가지 핸들러 모두 JSON Pointer 형식 검증 |
| `presentation/test/JsonPointerConversionPropertyTest` (jqwik) | 8개 속성 — 이스케이프, dotted/indexed/nested |

**축:** 명시적 단언(테스트 + 속성) + 운영 가시성(sentinel)

---

## 정책 9. "운영 로그에는 PII나 raw URI가 절대 들어가지 않는다"

| 표현 위치 | 메커니즘 |
|---|---|
| `application/LogSanitizer.java` | 모든 외부 입력값 sanitization 진입점 (제어문자/`=`/`|`/길이 제한) |
| `actorId(...)` | 이메일 마스킹 또는 sha256 해시 prefix |
| `clientIp(...)` | IPv4 마지막 옥텟 0으로 마스킹, IPv6 해시 |
| 모든 핸들러 | `LogSanitizer.requestPath(request.getRequestURI())` 통일 — raw URI 직접 로깅 금지 |
| `infrastructure/InfrastructureException.java` Javadoc | detailMessage에 금지/허용 항목 명시 (가이드 코드 옆에 박힘) |
| `application/test/LogSanitizerPropertyTest` (jqwik) | 11개 속성 — 길이 한도, 제어문자 제거, IPv4 마스킹, 이메일 마스킹, 해시 prefix |

**축:** 명시적 단언(속성 테스트) + 코드 옆 가이드(Javadoc 금지/허용 목록)

---

## 정책 10. "응답이 이미 커밋되면 재시도하지 않는다"

| 표현 위치 | 메커니즘 |
|---|---|
| `presentation/ApplicationExceptionHandler.java` `handleMessageNotWritableException` | HttpMessageNotWritable 핸들러가 `response.isCommitted()` 체크 후 빈 ResponseEntity 반환 (null 반환 회피로 재진입 차단) |
| `bootstrap/SecurityExceptionHandler.java` `commence()`/`handle()` | 양쪽 모두 `response.isCommitted()` 체크 |

**축:** 명시적 단언 + 코드 옆 가이드(왜 빈 ResponseEntity인지 Javadoc)

---

## 정책 11. "엔드포인트 호출 한 건의 로그 1줄로 운영 트리아지가 가능하다"

모든 핸들러 로그 라인이 다음 필드를 포함:
- `method=` (request.getMethod())
- `requestPath=` (LogSanitizer.requestPath 통일)
- `errorCode=` 또는 `status=`
- 추가 컨텍스트(parameter, header, contentType 등)
- traceId는 MDC를 통해 SLF4J 패턴(`%X{traceId}`)으로 자동 prefix

→ 한 줄만 봐도 "어떤 사용자가 어떤 경로에 어떤 메서드로 어떤 에러를 받았다"가 즉시 보인다.

**축:** 코드 옆 가이드(로그 포맷 규약) + 명시적 단언(통합 테스트의 로그 단언)

---

## 정책 12. "presentation 모듈은 Spring Security를 직접 의존하지 않는다"

| 표현 위치 | 메커니즘 |
|---|---|
| `bootstrap/test/LayerDependencyArchitectureTest` `presentation_must_not_read_security_context_directly` | ArchUnit으로 강제 |
| 같은 파일 `presentation_must_not_accept_raw_spring_security_authentication` | ArchUnit으로 강제 |
| `bootstrap/SecurityResponseExceptionHandler.java` | Security 의존 advice는 bootstrap에 둠 (룰 우회 없이 정직하게 분리) |

**축:** 타입 시스템(ArchUnit이 사실상 컴파일 게이트) + 코드 옆 가이드(Javadoc에 "왜 bootstrap에 있는가")

---

## 정책 13. "한 PR 단위의 정책 변경은 회귀 테스트의 단언과 동시에 변경된다"

회귀 안전망의 두께:

| 단언 강도 | 표현 위치 |
|---|---|
| 정확값 매핑 | `each_client_facing_error_code_maps_to_its_exact_expected_http_status` (16개 케이스) |
| 누락 가드 | `exact_mapping_table_must_cover_every_client_facing_error_code_enum_value` |
| classpath 가드 | `every_client_facing_error_code_implementation_on_classpath_is_covered_by_the_table` |
| 코드 유일성 | `all_client_facing_error_codes_have_unique_string_codes` |
| status 보존 | `ApiErrorControllerIntegrationTest`의 6 케이스 |
| 익명 vs 인증 | `ExceptionHandlingIntegrationTest`의 2 케이스 |
| 프레임워크 4xx/5xx | 동일 파일의 2 케이스 |
| validation 통합 | `ValidationExceptionHandlerIntegrationTest`의 3 케이스 |
| 안전망 | `uncaught_runtime_exception_hits_safety_net_and_returns_common_999` |
| traceId | `client_supplied_trace_id_is_ignored_and_server_generated_value_is_returned` |

**축:** 명시적 단언 + 운영 가시성

---

## 후속 작업 (정책 변경/추가 대기 중)

- **i18n** — `ClientFacingErrorCode.message()`가 한국어 하드코딩. MessageSource 도입 후 정책 14 추가 예정.
- **Aggregate JaCoCo 리포트** — 통합 테스트가 다른 모듈 코드를 커버하는 정도를 정확히 측정 (정책 13 강화).
- **`**/config/**` exclusion 정밀화** — 핵심 핸들러를 normal coverage report에 포함.
- **PIT mutation 점수 게이트 활성화** — 6개월 후 Tier 1 85%+.

---

## 참고

- 커버리지 임계치 정책: [`testing-coverage-policy.md`](testing-coverage-policy.md)
- 사건 단위 측정 기록: [`testing-history/README.md`](testing-history/README.md)
- 1~3차 리뷰 적용 이력: git log `refactor(error)`, `fix(error)` 커밋 메시지
