# Filter / Interceptor / Resolver / Advice 기준

## 1. 목적

이 문서는 Spring MVC 기반 서버에서 요청/응답 경계의 공통 처리 로직을 어디에 둘지 정의한다.

대상은 다음 네 가지다.

- Servlet Filter
- Spring MVC HandlerInterceptor
- HandlerExceptionResolver
- @ControllerAdvice / @RestControllerAdvice / @ExceptionHandler / ResponseBodyAdvice

목표는 다음과 같다.

- HTTP/Servlet 수준 관심사와 MVC/controller 수준 관심사를 분리한다.
- 예외 처리와 응답 포맷 표준화를 한 곳에 모은다.
- business rule, validation, transaction, domain mapping이 web infrastructure 훅 안으로 새어 들어가지 않게 한다.
- 같은 문제를 filter / interceptor / advice 어디에든 중복 구현하는 일을 막는다.

## 2. 근거 수준

- Official: Spring Framework / Spring Boot 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 제약 위에 일반적인 실무 운영 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 계층이 낮을수록 더 일반적인 HTTP 관심사만 둔다

기본 원칙:

- Filter: Servlet/HTTP 인프라 수준 공통 처리
- Interceptor: handler/controller 실행 전후 공통 처리
- @RestControllerAdvice: controller 계층의 예외 응답/공통 응답 규약 처리
- HandlerExceptionResolver: 정말 낮은 수준의 resolver chain 커스터마이징이 필요할 때만 사용

Spring 공식 문서 기준으로 Filter는 filter chain과 target Servlet 전후에 interception-style logic을 적용하는 용도이고, HandlerInterceptor는 handler 실행 전후 callback이며, 예외는 DispatcherServlet이 HandlerExceptionResolver 체인으로 위임합니다. @ControllerAdvice / @RestControllerAdvice는 전역 @ExceptionHandler 적용 지점입니다.

### 3.2 더 위 레벨 도구로 해결 가능한 일은 아래 레벨로 내리지 않는다

프로젝트 규칙:

- controller 예외 응답은 우선 @RestControllerAdvice
- 응답 body 표준화는 우선 ResponseBodyAdvice
- handler 관련 공통 전처리는 우선 HandlerInterceptor
- Servlet container 전체에 걸친 공통 처리만 Filter

즉, 아래 레벨 훅을 “더 강력하니까” 먼저 선택하지 않는다.

### 3.3 이 프로젝트의 에러 응답 기본 포맷은 ApiResult다

Spring은 @ExceptionHandler 또는 @RequestMapping에서 ProblemDetail이나 ErrorResponse를 반환해 RFC 9457 응답을 렌더링할 수 있고, ResponseEntityExceptionHandler도 공식 제공한다. 하지만 이 프로젝트는 공식 확장 지점은 따르되, 응답 본문 포맷의 기본값은 custom ApiResult 로 둔다.

프로젝트 규칙:

- 에러 응답의 프로젝트 기본 표준은 ProblemDetail이 아니라 ApiResult
- 전역 예외 처리의 기본 위치는 @RestControllerAdvice
- built-in MVC 예외와 business exception 모두 프로젝트 공통 ApiResult 규약으로 변환
- 외부 표준 계약이나 특정 연동에서 RFC 9457이 명시적으로 필요할 때만 ProblemDetail 사용을 예외적으로 허용

이 규칙은 Official + Practice + Project Recommendation 이다.
즉, 공식 문서가 제공하는 entry point는 사용하되, 실제 payload shape은 프로젝트 표준으로 통일한다.

## 4. Filter 표준

### 4.1 Filter의 책임

Filter는 Servlet 체인 수준의 공통 처리에 사용한다. Spring 공식 문서에서 Filter는 processing chain과 target Servlet 전후에 interception-style logic을 적용하는 용도다.

허용 예:

- request/response wrapping
- forwarded header 처리
- correlation id / trace id의 very-early binding
- MDC 진입/해제
- Spring Security filter chain에 통합되는 보안 전처리
- controller mapping 이전에 처리되어야 하는 공통 HTTP concern

비허용 예:

- 도메인 검증
- use case 오케스트레이션
- transaction 시작/종료
- repository/JPA 직접 호출을 전제로 한 핵심 업무 처리
- DTO ↔ domain 변환
- 공통 API 에러 응답 본문 구성의 주 책임

### 4.2 custom filter는 OncePerRequestFilter를 우선 검토한다

Spring 공식 문서 기준으로 OncePerRequestFilter는 request 시작 시 단일 호출을 지원하고, ASYNC/ERROR dispatch 관여 여부 제어를 제공한다.

프로젝트 규칙:

- 새 custom filter는 기본적으로 OncePerRequestFilter를 우선 사용한다.
- shouldNotFilterAsyncDispatch, shouldNotFilterErrorDispatch 필요 여부를 명시적으로 검토한다.
- “왜 filter여야 하는가?”를 설명할 수 없으면 interceptor 또는 advice로 올린다.

### 4.3 Filter에는 무거운 의존성을 직접 물지 않는다

Spring Boot 공식 문서는 filter bean이 application lifecycle 초기에 설치되므로 너무 많은 bean의 eager initialization을 유발하지 않게 주의하라고 하며, DataSource나 JPA configuration 의존은 좋지 않다고 설명한다.

프로젝트 규칙:

- filter에서 repository, entity manager, transaction-heavy service 직접 의존을 기본 금지한다.
- filter가 복잡한 서비스 계층을 호출해야 한다면 설계를 다시 검토한다.
- 인증/인가 체계는 개별 custom filter 남발보다 security/filter chain 구조에 맞춘다.

### 4.4 Filter 등록과 순서는 명시적 필요가 있을 때만 건드린다

Spring Boot는 Filter bean을 자동 등록하고, 필요하면 FilterRegistrationBean으로 매핑과 order를 제어할 수 있다. dispatcher type 미지정 시 기본은 REQUEST다.

프로젝트 규칙:

- 순서 의존이 없으면 순서 지정 최소화
- 순서가 필요하면 이유를 주석 또는 문서에 남긴다
- request body를 읽거나 wrapping하는 filter는 더 이른 순서 배치를 신중히 검토한다

## 5. HandlerInterceptor 표준

### 5.1 Interceptor의 책임

HandlerInterceptor는 handler/controller 실행 전후의 가벼운 공통 처리에 사용한다. Spring 공식 문서도 interceptor를 handler 관련 fine-grained preprocessing 용도로 설명한다.

허용 예:

- 인증 완료 이후의 요청자 정보 추출
- audit context 세팅/해제
- handler 기반 lightweight access policy
- locale/theme 같은 MVC handler 관련 전처리
- controller 호출 전후의 가벼운 메타데이터 기록

비허용 예:

- 보안의 주 진입점
- request/response wrapping
- body 읽기/변형
- transaction 제어
- 핵심 비즈니스 검증
- domain object 생성/조립

### 5.2 Interceptor를 보안 레이어의 중심으로 사용하지 않는다

Spring 공식 문서는 interceptor가 annotated controller path matching과 mismatch 가능성이 있어 security layer로는 이상적이지 않으며, 일반적으로 Spring Security 또는 Servlet filter chain에 통합된 접근을 더 이르게 적용하라고 권장한다.

프로젝트 규칙:

- 인증/인가의 중심은 interceptor가 아니라 security/filter chain
- interceptor는 보안 체계가 끝난 뒤 controller 실행에 가까운 공통 처리에만 사용

### 5.3 @ResponseBody / ResponseEntity 응답 변경 지점으로 쓰지 않는다

Spring 공식 문서 기준으로 @ResponseBody와 ResponseEntity 응답은 HandlerAdapter 내부에서 body가 쓰이고 커밋된 뒤 postHandle이 호출되므로, 이 시점은 응답 변경 지점으로 늦다. 이 경우 ResponseBodyAdvice를 사용해야 한다.

프로젝트 규칙:

- JSON 응답 envelope 적용
- 공통 body 필드 삽입
- 에러 응답 본문 공통 보강
- ApiResult 응답 래핑/정규화

이런 작업은 interceptor가 아니라 ResponseBodyAdvice 또는 @RestControllerAdvice에 둔다.

### 5.4 async controller가 있으면 interceptor lifecycle을 단순 가정하지 않는다

프로젝트 규칙:

- thread-local 정리 책임이 interceptor에 있다면 async request 존재 여부를 반드시 점검한다
- async endpoint가 있다면 interceptor cleanup이 sync 요청과 동일하게 호출된다고 가정하지 않는다
- async lifecycle이 중요하면 별도 async 처리 훅 설계를 검토한다

이 항목은 Spring MVC async 처리 모델을 반영한 Official + Practice 규칙이다.

## 6. HandlerExceptionResolver 표준

### 6.1 Resolver는 저수준 exception chain 확장 지점이다

Spring 공식 문서 기준으로 여러 HandlerExceptionResolver를 체인으로 둘 수 있고, order에 따라 순서가 정해지며, resolver는 ModelAndView, empty ModelAndView, 또는 null을 반환할 수 있다. null이면 다음 resolver가 계속 처리한다.

프로젝트 규칙:

- 일반 애플리케이션 예외 응답의 기본 수단으로 custom HandlerExceptionResolver를 만들지 않는다
- 기본 선택은 @RestControllerAdvice + @ExceptionHandler
- custom resolver는 framework integration이나 아주 낮은 수준의 예외 변환이 필요할 때만 허용

### 6.2 resolver는 ApiResult 표준화의 주 도구가 아니다

프로젝트 규칙:

- business exception → HTTP 응답 매핑은 @RestControllerAdvice
- ApiResult.fail(...) 생성도 기본적으로 advice에서 수행
- resolver는 “정말 advice보다 아래 레벨에서 처리해야 하는 상황”에만 사용

즉, ApiResult를 도입한다고 해서 resolver 쪽으로 내려가지 않는다.
응답 포맷 표준화의 중심은 여전히 advice다.

## 7. @ControllerAdvice / @RestControllerAdvice 표준

### 7.1 전역 예외 처리는 @RestControllerAdvice를 기본으로 한다

Spring 공식 문서 기준으로 @RestControllerAdvice는 @ControllerAdvice + @ResponseBody의 shortcut이며, 전역 @ExceptionHandler는 local controller의 @ExceptionHandler 뒤에 적용된다. 기본적으로 모든 controller에 적용된다.

프로젝트 규칙:

- REST API 서버의 전역 예외 처리 기본값은 @RestControllerAdvice
- controller별 local @ExceptionHandler 남발을 피한다
- 공통 에러 정책은 소수의 advice에 집중시킨다

### 7.2 이 프로젝트의 전역 에러 응답은 ApiResult로 통일한다

프로젝트 규칙:

- business exception, validation exception, built-in MVC exception 모두 가능한 한 ApiResult 규약으로 변환한다
- 에러 응답 구조는 프로젝트 전역에서 일관되게 유지한다
- controller가 직접 에러 body를 조립하지 않는다
- advice가 HTTP status와 ApiResult body를 함께 결정한다

권장 방향 예시:

- HTTP status는 표준 의미를 유지
- body는 ApiResult의 실패 형식으로 통일
- 내부 stack trace, framework class name, 구현 세부사항은 노출 금지
- 외부 노출용 에러 코드와 메시지는 분리 가능하게 설계

이 규칙은 Official + Practice 에 가깝다.
Spring은 ProblemDetail을 공식 지원하지만, 실제 프로젝트에서는 별도의 공통 envelope를 유지하는 경우가 많고, Spring의 공식 advice/exception handler 확장 지점은 그런 custom body에도 그대로 사용할 수 있다.

### 7.3 ProblemDetail은 기본이 아니라 예외적 옵션이다

Spring은 ProblemDetail, ErrorResponse, ResponseEntityExceptionHandler를 공식 지원한다. 또한 Spring Boot는 spring.mvc.problemdetails.enabled를 통해 built-in exception의 problem details 처리도 자동 구성할 수 있다.

프로젝트 규칙:

- 프로젝트 기본 에러 포맷은 ApiResult
- ProblemDetail은 다음 경우에만 예외적으로 허용
- 외부 표준 계약이 RFC 9457을 요구하는 경우
- 특정 API만 공개 표준 준수가 더 중요한 경우
- 타 시스템과의 호환성 때문에 application/problem+json이 필요한 경우
- 프로젝트 내부/일반 REST API에는 ProblemDetail과 ApiResult를 혼용하지 않는다

### 7.4 built-in MVC 예외도 프로젝트 포맷으로 흡수한다

Spring 공식 문서 기준으로 ResponseEntityExceptionHandler는 Spring MVC 예외와 ErrorResponseException을 다루는 편의 base class다.

프로젝트 규칙:

- built-in MVC 예외가 많고 이를 일관된 ApiResult로 바꿔야 하면 ResponseEntityExceptionHandler 확장을 검토한다
- built-in 예외를 ProblemDetail 그대로 노출하는 방향은 프로젝트 기본값이 아니다
- business exception과 framework exception이 서로 다른 응답 형식을 가지지 않게 한다

## 8. ResponseBodyAdvice 표준

### 8.1 응답 body 공통 가공은 ResponseBodyAdvice를 사용한다

Spring 공식 문서 기준으로 ResponseBodyAdvice는 @ResponseBody 또는 ResponseEntity controller method 실행 후, HttpMessageConverter가 body를 쓰기 전에 응답을 커스터마이징하는 확장 지점이다.

프로젝트 규칙:

- 성공 응답의 공통 envelope 적용
- ApiResult.success(...) 형태의 일관화
- 에러 응답 body의 공통 필드 보강
- trace id, timestamp, request id 같은 공통 값 삽입

이런 책임은 ResponseBodyAdvice에 둘 수 있다.

### 8.2 다만 전역 래핑은 “명확한 규약”이 있을 때만 사용한다

프로젝트 규칙:

- 모든 성공 응답을 ApiResult로 래핑할지 여부는 프로젝트 API 계약에 맞춰 일관되게 정한다
- file download, streaming, SSE, 이미 포맷이 고정된 응답에는 전역 wrapping을 피한다
- supports(...) 조건을 좁게 잡아 surprise를 줄인다
- controller가 이미 ApiResult를 반환하는 프로젝트라면 ResponseBodyAdvice로 이중 래핑하지 않는다

즉, ResponseBodyAdvice는 강력하지만 “마법처럼 몰래 바꾸는 곳”이 아니라 공식적인 응답 규약 적용 지점이어야 한다.

## 9. 위치별 금지 규칙

다음은 기본 금지다.

Filter:

- transaction
- repository/JPA 직접 호출
- business validation
- DTO/domain mapping
- ApiResult 에러 본문 생성의 주 수단

Interceptor:

- 인증/인가의 주 구현
- body wrapping
- @ResponseBody 응답 변형
- transaction
- 핵심 use case 호출

HandlerExceptionResolver:

- 일반 business exception 처리의 기본 수단
- 팀 공통 ApiResult 포맷의 주 진입점

ControllerAdvice / ResponseBodyAdvice:

- domain 규칙 실행
- repository 접근
- 핵심 오케스트레이션
- endpoint별 business branching 누적

## 10. 선택 기준 요약

다음 질문으로 결정한다.

- request/response를 Servlet 수준에서 감싸거나 아주 이른 시점에 처리해야 하는가? Filter
- 특정 handler/controller 실행 전후의 공통 처리인가? HandlerInterceptor
- controller 예외를 HTTP 응답으로 바꾸는가? @RestControllerAdvice + @ExceptionHandler
- 성공/실패 body를 프로젝트 공통 ApiResult 규약에 맞게 가공해야 하는가? ResponseBodyAdvice 또는 @RestControllerAdvice
- 정말 resolver chain 자체를 커스터마이징해야 하는가? HandlerExceptionResolver
