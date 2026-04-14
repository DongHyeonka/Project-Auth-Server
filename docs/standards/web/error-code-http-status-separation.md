# Error Code / HTTP Status Separation 기준

## 1. 목적

이 문서는 API 실패 응답에서 HTTP status와 application error code의 역할을 분리하는 기준을 정의한다.

이 문서의 목표는 다음과 같다.

- HTTP status를 프로토콜 의미에 맞게 사용한다
- business/domain/application 오류 식별은 별도의 ErrorCode로 관리한다
- controller/advice에서 status와 code를 뒤섞어 쓰는 일을 막는다
- 실패 응답이 운영, 클라이언트 처리, 로그 분석에서 일관되게 동작하게 한다

## 2. 근거 수준

- Official: HTTP Semantics(RFC 9110), Spring Framework 공식 문서/Javadoc에서 직접 확인되는 내용
- Official + Practice: 공식 의미 위에 일반적인 실무 API 설계 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 HTTP status는 프로토콜 수준 의미다

HTTP status code는 응답의 결과와 의미를 나타내는 표준 3자리 코드이며, 첫 번째 자리가 응답 클래스(1xx~5xx)를 결정합니다. 4xx는 요청 자체가 잘못되었거나 현재 요청을 이행할 수 없는 경우이고, 5xx는 서버가 유효해 보이는 요청을 수행하지 못한 경우입니다.

프로젝트 규칙:

- HTTP status는 HTTP 관점의 결과를 나타낸다
- status는 transport/protocol 의미를 표현한다
- status를 business/domain 세부 사유 식별자로 남용하지 않는다

### 3.2 ErrorCode는 애플리케이션 수준 의미다

ErrorCode는 HTTP 표준 개념이 아니라, 프로젝트가 정의하는 기계 판독용 애플리케이션 오류 식별자다.

프로젝트 규칙:

- ErrorCode는 business/application/framework error를 구분하는 식별자다
- 클라이언트의 세부 분기, 운영 로그 분류, 문서화, 모니터링에 사용한다
- ErrorCode는 HTTP status를 대체하지 않는다

이 항목은 Official + Practice 이다. HTTP가 status의 의미를 정의하고, 세부 오류 분류는 애플리케이션이 별도로 설계하는 것이 자연스럽다.

### 3.3 status와 code는 서로 다른 질문에 답한다

프로젝트 규칙:

- HTTP status는 “HTTP 요청을 어떤 범주로 처리했는가?”에 답한다
- ErrorCode는 “애플리케이션에서 정확히 어떤 종류의 실패인가?”에 답한다

예:

- 400 Bad Request + REQUEST_VALIDATION_FAILED
- 409 Conflict + DUPLICATE_EMAIL
- 401 Unauthorized + INVALID_ACCESS_TOKEN
- 503 Service Unavailable + UPSTREAM_AUTH_SERVER_UNAVAILABLE

즉, 두 값은 중복이 아니라 서로 다른 층위의 정보다.

## 4. HTTP status 사용 규칙

### 4.1 유효한 HTTP status만 사용한다

HTTP status의 유효 범위는 100~599이며, 그 밖의 값은 HTTP status로는 유효하지 않습니다. RFC 9110도 600~999 같은 값은 내부 통신에서 비표준적으로 쓰일 수는 있어도 HTTP 응답 status로는 유효하지 않다고 설명합니다.

프로젝트 규칙:

- 6xx, 7xx 같은 custom status code 사용 금지
- status는 표준 HTTP status만 사용
- 세부 오류 분기는 status가 아니라 ErrorCode로 해결한다

### 4.2 status는 최대한 표준 의미에 가깝게 선택한다

프로젝트 규칙:

- 입력 형식/검증 실패 → 400 계열
- 인증 실패 → 401
- 권한 부족 → 403
- 리소스 없음 → 404
- 상태 충돌/중복/현재 상태와의 모순 → 409
- 의미적으로 처리 불가능한 요청을 별도로 구분할 합의가 있으면 422 검토 가능
- 예상 못 한 서버 오류 → 500
- 일시적 외부 의존성 실패 → 502/503/504 중 의미에 맞는 값 선택

이 항목은 Official + Practice 이다. RFC 9110이 status class 의미를 정의하고, 세부 매핑은 API 설계자가 해당 의미에 맞게 선택해야 한다.

### 4.3 같은 business family가 항상 같은 status일 필요는 없지만, 이유는 분명해야 한다

프로젝트 규칙:

- 같은 ErrorCode family라도 상황에 따라 status가 달라질 수 있다
- 다만 같은 의미의 오류에 status가 들쭉날쭉하면 안 된다
- status 선택 기준은 문서와 ErrorCode 정책에 남긴다

예:

- AUTHENTICATION_FAILED 류는 보통 401
- AUTHORIZATION_DENIED 류는 보통 403
- RESOURCE_CONFLICT 류는 보통 409

## 5. ErrorCode 사용 규칙

### 5.1 ErrorCode는 중앙 정책 타입으로 관리한다

프로젝트 규칙:

- ErrorCode는 enum 또는 이에 준하는 중앙 정책 타입으로 관리한다
- controller/advice/service 각 파일에 문자열 리터럴로 흩뿌리지 않는다
- code, 기본 message, 기본 httpStatus를 함께 관리할 수 있다

이 규칙은 실무적으로 가장 흔한 안정화 방식이며, 앞선 응답 포맷 규약과도 맞물린다.

### 5.2 ErrorCode는 외부 계약이다

프로젝트 규칙:

- 한 번 공개된 ErrorCode는 API 계약으로 취급한다
- 이름 변경, 삭제, 의미 변경은 호환성 영향이 있다
- 로그용 내부 키와 외부 응답용 code를 필요하면 분리한다

### 5.3 message는 ErrorCode의 기본 외부 메시지로 관리할 수 있다

프로젝트 규칙:

- 기본 외부 메시지는 ErrorCode가 가진다
- advice는 예외를 적절한 ErrorCode로 매핑하는 책임에 집중한다
- ex.getMessage()를 외부 응답 메시지 기본값으로 쓰지 않는다

## 6. status와 ErrorCode의 관계

### 6.1 하나의 status 아래 여러 ErrorCode가 올 수 있다

HTTP는 status class로 넓은 의미를 표현하므로, 하나의 400/409/500 아래에 여러 세부 application code가 오는 것이 자연스럽다. 이는 HTTP status가 세부 business 오류 식별용이 아니기 때문이다.

프로젝트 규칙:

400 아래:

- REQUEST_VALIDATION_FAILED
- INVALID_QUERY_PARAMETER
- MALFORMED_JSON_REQUEST

409 아래:

- DUPLICATE_EMAIL
- SESSION_ALREADY_REVOKED
- RESOURCE_VERSION_CONFLICT

처럼 관리할 수 있다.

### 6.2 하나의 ErrorCode는 기본 status를 가진다

프로젝트 규칙:

- 각 ErrorCode는 기본적으로 하나의 대표 HTTP status를 가진다
- 기본 status는 중앙 정책 타입에서 관리한다
- 예외적 override가 필요한 경우에만 advice에서 분기한다

### 6.3 “200 OK + success=false”를 기본 실패 전략으로 쓰지 않는다

HTTP status는 응답의 결과 의미를 담는 표준 필드이므로, 실패를 body의 success=false에만 넣고 status를 무조건 200으로 보내는 방식은 status 의미를 약화시킨다. RFC 9110은 status code가 요청 결과와 응답 의미를 나타낸다고 명확히 정의한다.

프로젝트 규칙:

- 실패 응답은 적절한 4xx/5xx status를 함께 사용한다
- ApiResult.success=false는 body 규약 보강용이지, status 대체물이 아니다
- “모든 응답은 200” 전략을 기본 금지한다

## 7. Spring 사용 규칙

### 7.1 일반 실패 응답은 @RestControllerAdvice + ResponseEntity를 기본으로 한다

Spring은 @ExceptionHandler에서 ResponseEntity를 반환해 status와 body를 함께 제어할 수 있게 하고, @ControllerAdvice/@RestControllerAdvice로 전역 적용할 수 있다. ResponseEntity는 status·headers·body를 함께 표현하는 타입이다.

프로젝트 규칙:

- 공통 실패 응답은 @RestControllerAdvice에서 생성
- body는 ApiResult.fail(ErrorCode...)
- status는 ErrorCode가 가진 기본 status 또는 정책에 맞는 값 사용

### 7.2 예외 클래스에 @ResponseStatus를 기본 전략으로 두지 않는다

Spring의 ResponseStatusExceptionResolver는 @ResponseStatus와 ResponseStatusException을 status로 매핑한다. 하지만 @ResponseStatus Javadoc은 예외 클래스에 이 애노테이션을 붙이거나 reason을 주면 sendError가 사용되고, REST API에는 부적합할 수 있으므로 이런 경우 ResponseEntity를 선호하라고 명시합니다.

프로젝트 규칙:

- business exception 클래스에 @ResponseStatus를 기본적으로 붙이지 않는다
- 특히 reason 사용 금지
- 예외는 domain/application 의미를 표현하고, HTTP status 변환은 advice에서 수행한다

### 7.3 ResponseStatusException은 제한적으로 사용한다

Spring은 ResponseStatusException을 공식 지원하고 resolver가 이를 status로 처리한다. 다만 이것은 HTTP-aware 예외이므로 controller/web adapter 쪽에서는 유용할 수 있지만, application/domain 핵심 로직까지 전파되는 기본 모델로 두는 것은 바람직하지 않다.

프로젝트 규칙:

- web adapter/controller 레벨의 즉시 HTTP 실패 표현이 필요할 때 제한적으로 사용 가능
- application/domain/service의 기본 예외 모델로 채택하지 않는다
- 프로젝트 기본 경로는 여전히 “도메인/애플리케이션 예외 → advice에서 ErrorCode/status 매핑”이다

## 8. 설계 권장안

### 8.1 ErrorCode가 기본 status를 가진다

권장 구조:

- ErrorCode
- httpStatus
- code
- message
- ApiResult.fail(ErrorCode)
- advice는 예외를 ErrorCode로 매핑

이 구조는 status와 code의 역할을 분리하면서도, 운영 시 일관된 실패 정책을 유지하기 쉽다.

### 8.2 advice는 문자열 조립보다 매핑에 집중한다

프로젝트 규칙:

- advice는 예외 → ErrorCode 선택
- ApiResult는 ErrorCode에서 code/message를 읽어 body 생성
- status는 ErrorCode.httpStatus() 또는 명시적 override로 결정

## 9. 금지 규칙

다음은 기본 금지다.

- 6xx/7xx 같은 custom HTTP status 사용
- 실패를 무조건 200 OK로 응답하고 body에만 실패 표시
- advice/controller에 "DUPLICATE_EMAIL" 같은 문자열 하드코딩
- ex.getMessage()를 그대로 외부 응답 메시지로 사용
- domain/application 예외 클래스에 @ResponseStatus(reason=...) 사용
- HTTP status와 ErrorCode를 사실상 같은 값처럼 중복 설계
- endpoint마다 같은 오류에 다른 status를 제멋대로 사용

## 10. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 status는 HTTP 의미로 설명 가능한가?
- 이 세부 실패 식별은 ErrorCode로 따로 표현되는가?
- 100~599 범위의 표준 status만 쓰고 있는가?
- 실패인데도 200으로 보내고 있지 않은가?
- ErrorCode가 중앙 정책 타입으로 관리되는가?
- @ResponseStatus(reason=...) 대신 advice + ResponseEntity를 쓰고 있는가?
