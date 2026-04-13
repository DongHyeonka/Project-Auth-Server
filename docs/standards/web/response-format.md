# Response Format 기준

## 1. 목적

이 문서는 API 응답 본문의 형식과 공통 규약을 정의한다.

이 문서의 목표는 다음과 같다.

- 성공/실패 응답의 구조를 일관되게 만든다
- controller마다 제각각인 응답 body 형식을 막는다
- HTTP status와 응답 body의 역할을 구분한다
- 공통 응답 envelope와 실제 business payload의 책임을 분리한다

## 2. 근거 수준

- Official: Spring Framework 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 문서의 확장 지점 위에 일반적인 실무 API 설계 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 이 프로젝트의 JSON API 기본 응답 형식은 ApiResult<T>다

Spring은 응답 body를 @ResponseBody/ResponseEntity로 직렬화하고, 필요하면 ResponseBodyAdvice로 body를 공통 가공할 수 있게 한다. 따라서 프로젝트는 Spring의 공식 응답 처리 지점을 그대로 사용하되, 실제 JSON 응답 본문 형식은 custom envelope인 ApiResult<T>로 표준화한다.

프로젝트 규칙:

- 일반 JSON API 응답의 기본 형식은 ApiResult<T>
- controller마다 서로 다른 임의 JSON 구조를 만들지 않는다
- ApiResult는 transport-level envelope이고, 실제 payload는 T가 담당한다

### 3.2 응답 형식은 “공통 envelope”와 “실제 data”를 분리한다

Spring 공식 문서가 ResponseEntity와 body object를 분리해서 다루는 구조를 제공하는 것처럼, 이 프로젝트도 응답의 공통 필드와 business payload를 분리한다.

프로젝트 규칙:

- 공통 응답 정보는 ApiResult가 담당
- 실제 비즈니스 데이터는 data가 담당
- business DTO 안에 다시 success, code, message를 중복으로 넣지 않는다

### 3.3 응답 형식은 전역 규약이어야 한다

Spring은 ResponseBodyAdvice를 통해 @ResponseBody나 ResponseEntity 응답을 전역적으로 가공할 수 있다. 즉, 응답 형식 통일은 controller 개별 구현이 아니라 프레임워크 확장 지점에서 중앙 관리할 수 있다.

프로젝트 규칙:

- 응답 형식 표준화는 controller마다 수동으로 맞추는 것보다 공통 규약으로 관리한다
- 같은 API 군 안에서는 성공/실패 응답 형식이 일관되어야 한다
- endpoint마다 envelope 유무가 달라지는 surprise를 만들지 않는다

## 4. 표준 응답 구조

### 4.1 성공 응답

프로젝트 기본 형식 예시:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Success",
  "data": {
    "userId": "u_123",
    "email": "user@example.com"
  },
  "meta": null
}
```

프로젝트 규칙:

- 성공 응답은 success=true
- 성공 응답의 표준 code는 기본적으로 SUCCESS
- 실제 payload는 data
- 부가 정보가 필요하면 meta 사용 가능
- 단순 성공이더라도 응답 구조를 임의로 바꾸지 않는다

이 항목은 Project Recommendation 이다.

### 4.2 실패 응답

Spring은 예외를 HTTP 응답으로 렌더링하는 공식 지점으로 @ExceptionHandler, @ControllerAdvice, ResponseEntityExceptionHandler를 제공한다. 이 프로젝트는 그 지점을 사용해 실패 응답도 ApiResult 형식으로 통일한다.

프로젝트 기본 형식 예시:

```json
{
  "success": false,
  "code": "REQUEST_VALIDATION_FAILED",
  "message": "Request validation failed",
  "data": {
    "email": "must not be blank"
  },
  "meta": {
    "requestId": "..."
  }
}
```

프로젝트 규칙:

- 실패 응답은 success=false
- 실패 원인 식별자는 반드시 code에 둔다
- 외부 노출 메시지는 message
- 상세 오류 정보가 필요하면 data 또는 별도 표준 필드에 둔다
- 내부 예외 스택트레이스, 클래스명, 민감정보를 응답 body에 넣지 않는다

## 5. ApiResult 설계 규칙

### 5.1 envelope는 얇고 안정적이어야 한다

프로젝트 규칙:

- ApiResult 필드는 최소한으로 유지한다
- envelope 구조는 쉽게 자주 바꾸지 않는다
- 응답 본문 규약은 business DTO보다 더 안정적인 계약으로 다룬다

권장 기본 필드:

- success
- code
- message
- data
- meta (선택)

### 5.2 공통 필드와 business 필드를 섞지 않는다

프로젝트 규칙:

- ApiResult 바깥과 data 안의 의미를 섞지 않는다
- pagination, cursor, totalCount 같은 응답 보조 정보는 규칙적으로 meta 또는 명시적 pagination DTO에 둔다
- business payload 안에 공통 상태 필드를 섞어 넣지 않는다

### 5.3 code는 문자열이지만 정책적으로 중앙 관리한다

이 문서는 응답 형식 문서이므로 code 체계 자체의 상세 규칙은 다음 문서인 Error Code/HTTP Status Separation Standard에서 다룬다. 다만 응답 형식 관점에서 code는 항상 존재하는 공통 식별자여야 한다.

프로젝트 규칙:

- 성공/실패 모두 code 필드를 가진다
- code는 advice/controller에서 임의 문자열로 흩뿌리지 않는다
- ErrorCode 같은 중앙 정책 타입을 통해 관리한다

## 6. ResponseEntity 사용 규칙

Spring 공식 문서 기준으로 ResponseEntity는 headers, body, status를 함께 지정하는 반환형이다. 따라서 전체 HTTP 응답을 제어할 필요가 있을 때 의미가 있다.

프로젝트 규칙:

- 단순 200 JSON 응답이면 꼭 ResponseEntity를 강제하지 않는다
- 다음 경우에는 ResponseEntity를 사용한다
- 201 Created
- 204 No Content
- custom header
- 캐시/조건부 응답
- 다운로드/streaming
- endpoint별 status 제어가 중요한 경우

### 6.1 body 표준화와 ResponseEntity는 충돌하지 않아야 한다

프로젝트 규칙:

- ResponseEntity<ApiResult<T>>는 허용된다
- 다만 ResponseEntity는 HTTP 제어용이고, ApiResult는 body 규약용이라는 역할 분리를 유지한다
- controller가 HTTP 제어도 없는데 습관적으로 ResponseEntity<ApiResult<T>>를 남발하지 않는다

## 7. ResponseBodyAdvice 사용 규칙

Spring의 ResponseBodyAdvice는 @ResponseBody 또는 ResponseEntity controller method 실행 후, HttpMessageConverter가 body를 쓰기 전에 응답을 커스터마이징하는 확장 지점이다.

프로젝트 규칙:

- 전역 envelope 적용이 필요하면 ResponseBodyAdvice를 사용할 수 있다
- 이미 ApiResult인 응답은 다시 감싸지 않는다
- file response, streaming response, SSE, 이미 형식이 고정된 외부 계약 응답은 전역 래핑 대상에서 제외한다
- supports(...) 조건은 넓게 열기보다 명시적으로 제어한다

### 7.1 전역 래핑은 “마법”이 아니라 명시적 규약이어야 한다

프로젝트 규칙:

- 팀이 “모든 JSON 성공 응답을 자동으로 ApiResult.success(...)로 감싼다”는 규칙을 합의한 경우에만 전역 래핑을 쓴다
- 그렇지 않으면 controller가 명시적으로 ApiResult를 반환하게 한다
- 두 방식이 혼재되면 응답 규약 이해 비용이 커지므로 기본 전략 하나를 정한다

## 8. 예외 응답 형식 규칙

Spring은 @ControllerAdvice/@ExceptionHandler, ResponseEntityExceptionHandler, DefaultHandlerExceptionResolver 등을 통해 예외를 HTTP 응답으로 연결할 수 있다. 이 프로젝트는 그 공식 메커니즘 위에서 예외 응답도 ApiResult 형식으로 통일한다.

프로젝트 규칙:

- 공통 예외 응답은 @RestControllerAdvice에서 생성한다
- controller 안에서 실패 응답 body를 직접 조립하는 것을 기본 금지한다
- framework 예외와 business 예외가 서로 다른 JSON 구조를 가지지 않게 한다

### 8.1 실패 응답의 message는 외부 노출용이어야 한다

프로젝트 규칙:

- message는 클라이언트에 보여줄 수 있는 수준으로 제한한다
- ex.getMessage()를 그대로 외부에 노출하는 것을 기본값으로 두지 않는다
- 내부 로그 메시지와 외부 응답 메시지를 분리한다

## 9. 예외적 응답 형식

### 9.1 envelope를 적용하지 않는 응답

Spring MVC는 body object뿐 아니라 HttpHeaders, file/streaming 관련 반환형 등도 지원한다. 모든 응답이 JSON envelope여야 하는 것은 아니다.

프로젝트 규칙:

다음은 ApiResult envelope 적용 대상에서 제외할 수 있다.

- 파일 다운로드
- binary response
- streaming/SSE
- redirect
- 204 No Content
- 외부 표준 계약이 별도 형식을 강제하는 응답

### 9.2 HTML/view 응답과 JSON API 응답을 섞지 않는다

프로젝트 규칙:

- JSON API는 ApiResult 또는 명시적 API 응답 규약을 따른다
- view rendering 응답은 별도 controller/경계로 분리한다
- 한 controller 안에서 HTML 응답 규약과 JSON envelope 규약을 섞지 않는다

## 10. 금지 규칙

다음은 기본 금지다.

- endpoint마다 제각각 다른 성공/실패 JSON 형식 사용
- controller 안에서 임시 Map<String, Object>로 응답 구조 조립
- ApiResult 바깥과 data 안에 공통 필드 중복
- ResponseBodyAdvice에서 무조건 이중 래핑
- 예외 메시지를 그대로 외부 응답에 노출
- HTTP status와 응답 body code/message 역할을 뒤섞기
- 파일/스트리밍 응답까지 무리하게 JSON envelope로 감싸기

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 응답은 프로젝트 표준 envelope(ApiResult)를 따르는가?
- ApiResult와 business payload의 역할이 분리되어 있는가?
- ResponseEntity를 쓰는 이유가 status/header 제어 때문인가?
- 전역 응답 래핑이 있다면 이중 래핑을 막고 있는가?
- 실패 응답도 성공 응답과 같은 큰 형식을 유지하는가?
- envelope 예외 대상(파일, 스트리밍 등)을 따로 처리하고 있는가?
