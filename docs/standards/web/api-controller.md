# API Controller 기준

## 1. 목적

이 문서는 Spring MVC 기반 API controller의 책임, 위치, 작성 방식, 금지사항을 정의한다.

이 문서의 목표는 다음과 같다.

- controller를 HTTP boundary 역할에 집중시킨다.
- request parsing / validation / authentication context extraction / response shaping의 위치를 명확히 한다.
- business rule, transaction, persistence access, external integration이 controller로 새어 들어오지 않게 한다.
- API controller가 프로젝트 전체에서 일관된 구조를 가지게 한다.

## 2. 근거 수준

- Official: Spring Framework / Spring Boot 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 문서의 확장 지점 위에 일반적인 실무 API 설계 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 API endpoint는 기본적으로 @RestController를 사용한다

Spring 공식 문서 기준으로 @RestController는 @Controller와 @ResponseBody를 결합한 annotation이며, @RequestMapping 메서드가 기본적으로 response body semantics를 가진다. JSON/HTTP body를 반환하는 API controller의 기본 선택지는 @RestController다. HTML view rendering이 목적일 때만 @Controller를 사용한다.

프로젝트 규칙:

- REST API endpoint의 기본값은 @RestController
- 서버사이드 템플릿 렌더링 등 view name 반환이 목적일 때만 @Controller
- 같은 controller 안에 API 응답과 view 렌더링을 섞지 않는다

### 3.2 controller는 HTTP boundary translator다

Spring MVC는 controller 메서드에서 request mapping, request input, exception handling, return value rendering을 담당할 수 있게 해 준다. 그러나 이 프로젝트에서 controller의 핵심 책임은 “HTTP 요청을 애플리케이션 입력으로 번역하고, 애플리케이션 결과를 HTTP 응답으로 번역하는 것”이다.

프로젝트 규칙:

- controller는 request를 해석한다
- 필요한 인증 주체/식별자/입력을 추출한다
- application service / use case를 호출한다
- 응답 DTO 또는 ApiResult로 응답을 만든다

그 외의 핵심 business decision은 controller의 기본 책임이 아니다.

### 3.3 controller는 얇게 유지한다

이 항목은 Official + Practice + Project Recommendation 이다.

프로젝트 규칙:

- controller는 가능한 한 짧고 예측 가능해야 한다
- HTTP concern 외의 분기와 규칙은 application/domain으로 이동한다
- controller 하나가 여러 repository, external client, transaction concern을 직접 조립하지 않는다
- “request 파싱 → use case 호출 → response 반환” 흐름이 한눈에 보여야 한다

## 4. 매핑 표준

### 4.1 class-level base path + method-level endpoint를 사용한다

Spring 공식 문서 기준으로 @RequestMapping은 class level에서 shared mapping을, method level에서 구체 endpoint mapping을 표현할 수 있다. 또한 대부분의 controller method는 모든 HTTP method를 받는 일반 @RequestMapping보다 @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping 같은 method-specific shortcut을 쓰는 것이 자연스럽다. 같은 element에 여러 @RequestMapping 계열을 함께 두면 첫 번째만 사용되고 warning이 남는다.

프로젝트 규칙:

- controller 클래스에는 resource base path를 둔다
- endpoint 메서드에는 HTTP method-specific mapping을 사용한다
- endpoint 메서드에 일반 @RequestMapping을 남발하지 않는다
- 한 메서드에 중복 mapping annotation을 두지 않는다

### 4.2 매핑 조건은 필요한 만큼만 사용한다

Spring은 mapping에 URL, HTTP method, params, headers, media types를 모두 조건으로 줄 수 있다. 하지만 이 프로젝트에서는 매핑 조건을 너무 많이 걸어 endpoint를 읽기 어렵게 만드는 것을 지양한다. 꼭 필요한 경우에만 consumes, produces, params, headers를 사용한다.

프로젝트 규칙:

- 기본은 path + HTTP method
- content-type이 중요한 endpoint만 consumes
- response media type이 실제로 갈리는 endpoint만 produces
- params/headers 조건은 routing necessity가 분명할 때만 사용

### 4.3 endpoint path는 리소스 중심으로 둔다

이 항목은 주로 Practice + Project Recommendation 이다.

프로젝트 규칙:

- path는 동사보다 리소스 중심으로 설계한다
- action semantics는 가능한 한 HTTP method로 표현한다
- 도메인적으로 특별한 command endpoint가 필요하면 예외적으로 명시적 action path를 사용할 수 있다
- controller 이름, class path, method path가 함께 읽혔을 때 자원이 자연스럽게 보이게 한다

## 5. 메서드 시그니처 표준

### 5.1 인자는 명시적으로 바인딩한다

Spring MVC controller method는 매우 다양한 인자를 지원한다. HttpServletRequest, HttpServletResponse, WebRequest, HttpSession, @PathVariable, @RequestParam, @RequestHeader, @ModelAttribute, @RequestBody 등 여러 타입과 annotation을 사용할 수 있다. 그러나 지원된다고 해서 아무 인자나 다 쓰는 방향을 기본값으로 두지 않는다.

프로젝트 규칙:

- path 값은 @PathVariable
- query 값은 @RequestParam
- header 값은 @RequestHeader
- JSON body는 @RequestBody
- form/query binding object는 @ModelAttribute
- 각 입력의 출처가 메서드 시그니처에 드러나야 한다

### 5.2 request DTO를 명시적으로 사용한다

Spring 공식 문서 기준으로 @ModelAttribute는 request parameters, URI path variables, headers를 model object에 바인딩하며, 보안상 웹 바인딩 전용 객체를 쓰거나 constructor binding only를 고려하고, property binding이 필요하면 allowedFields를 제한하라고 권장한다. 즉, 웹 입력용 객체 설계는 신중해야 한다.

프로젝트 규칙:

- JSON body는 전용 request DTO에 받는다
- form/query binding도 가능하면 전용 request model로 받는다
- entity/domain object를 직접 @RequestBody / @ModelAttribute 대상으로 쓰지 않는다
- request DTO는 transport model이지 domain model이 아니다

### 5.3 Servlet API 의존은 정말 필요할 때만 허용한다

Spring MVC는 HttpServletRequest, HttpServletResponse, WebRequest 등 직접적인 request/response 접근을 지원한다. 하지만 API controller의 기본 시그니처는 annotation 기반 인자 바인딩으로 충분해야 한다.

프로젝트 규칙:

- request body, path, query, header는 annotation 기반 인자 바인딩을 우선한다
- HttpServletRequest / HttpServletResponse는 다음처럼 정말 필요한 경우에만 사용한다
- request attribute 접근
- low-level header 처리
- cookie 직접 제어
- file streaming / low-level response control
- 단순 입력 추출 때문에 Servlet API를 들고 오지 않는다

### 5.4 변환과 포맷팅은 ad-hoc parsing보다 binder/converter를 우선 검토한다

Spring 공식 문서 기준으로 @InitBinder나 전역 MVC config를 통해 Converter, Formatter, PropertyEditor 등을 등록해 타입 변환과 formatting을 구성할 수 있다. controller 안에서 문자열 파싱 로직을 계속 반복하는 것보다 framework extension point를 쓰는 편이 낫다.

프로젝트 규칙:

- 동일한 문자열 → 타입 변환이 반복되면 converter/formatter를 검토한다
- web binding 전용 커스터마이징은 @InitBinder 또는 전역 conversion 설정으로 뺀다
- controller 본문에 파싱 로직을 반복해서 쓰지 않는다

## 6. 반환값 표준

### 6.1 기본 반환은 body 중심이다

Spring MVC는 @ResponseBody, ResponseEntity, HttpHeaders, ProblemDetail, String view name 등 다양한 반환형을 지원한다. API controller에서는 body 중심 반환이 기본이다. @RestController는 class level @ResponseBody semantics를 제공한다.

프로젝트 규칙:

- 일반 API 성공 응답은 ApiResult<ResponseDto> 또는 프로젝트 표준 response DTO를 반환한다
- view name 반환은 API controller에서 사용하지 않는다
- Map<String, Object> 같은 임시 응답은 승인 후보 코드에서 지양한다

### 6.2 ResponseEntity는 “정말 HTTP 응답을 제어해야 할 때” 사용한다

Spring 공식 문서 기준으로 ResponseEntity<B>는 전체 응답, 즉 HTTP status, headers, body를 함께 지정하는 반환형이다. 따라서 모든 endpoint에서 습관적으로 ResponseEntity를 쓸 필요는 없고, HTTP 응답 제어가 필요한 경우에 의미가 있다.

프로젝트 규칙:

- 기본 성공 200 응답이고 header 제어가 없다면 굳이 ResponseEntity를 강제하지 않는다
- 다음 경우에는 ResponseEntity를 사용한다
- 201 Created + Location
- 204 No Content
- header 제어
- 캐시/조건부 응답
- 파일 다운로드
- endpoint별로 status가 의미 있게 달라지는 경우

### 6.3 응답 body 공통화는 controller보다 advice에서 해결할 수 있다

Spring의 ResponseBodyAdvice는 @ResponseBody 또는 ResponseEntity controller method의 body가 HttpMessageConverter로 쓰이기 직전에 응답을 커스터마이징할 수 있고, @ControllerAdvice로 등록하면 자동 적용된다. 따라서 전역 ApiResult 래핑이나 공통 필드 보강은 controller 메서드마다 반복하지 않고 advice에 둘 수 있다.

프로젝트 규칙:

- 응답 envelope 공통화 정책이 있으면 controller 반복보다 advice를 우선 검토한다
- 다만 controller가 이미 명시적으로 ApiResult를 반환하는 프로젝트라면 이중 래핑을 피한다

## 7. 예외, 검증, 인증 경계

### 7.1 controller는 예외를 직접 처리하는 기본 위치가 아니다

Spring 공식 문서 기준으로 @ExceptionHandler, @InitBinder, @ModelAttribute는 local controller에도 둘 수 있고, @ControllerAdvice / @RestControllerAdvice로 전역 적용도 가능하다. 전역 @ExceptionHandler는 local handler 뒤에 적용된다.

프로젝트 규칙:

- 일반적인 API 예외 처리는 @RestControllerAdvice에 둔다
- controller 메서드 안에서 try-catch로 공통 예외 응답을 반복해서 만들지 않는다
- controller local @ExceptionHandler는 endpoint-local 정책이 정말 필요할 때만 사용한다

### 7.2 controller의 검증 책임은 request boundary까지다

이 항목은 앞서 정리한 validation 문서와 연결되는 Official + Practice + Project Recommendation 이다.

프로젝트 규칙:

- controller는 request DTO / scalar input의 구조 검증을 트리거한다
- business rule 검증, 상태 조회 기반 검증, 불변식 보장은 application/domain에서 담당한다
- controller validation 통과를 domain correctness의 보장으로 간주하지 않는다

### 7.3 인증 주체 접근은 명시적이고 제한적으로 한다

Spring MVC는 다양한 method argument를 허용하므로 인증 객체도 여러 방식으로 전달될 수 있다. 이 프로젝트에서는 인증 객체 접근을 controller 시그니처에서 명시적으로 드러내되, security implementation 세부사항이 controller 전체에 번지지 않게 한다.

프로젝트 규칙:

- 인증 주체는 가능한 한 전용 principal / auth object / argument resolver 결과로 받는다
- controller가 security framework의 저수준 타입에 과도하게 묶이지 않게 한다
- 인증 객체 접근 표준은 이후 Authentication Object Access Standard 문서에서 더 구체화한다

## 8. 의존성 및 금지 규칙

이 항목은 주로 Project Recommendation 이다.

프로젝트 규칙:

- controller는 repository를 직접 호출하지 않는다
- controller는 EntityManager, JDBC template, external client를 직접 조립하지 않는다
- controller에 @Transactional을 기본 금지한다
- controller는 domain entity를 외부 응답 모델로 직접 반환하지 않는다
- controller에서 event 발행, retry, async orchestration을 직접 수행하지 않는다
- controller 메서드가 “HTTP boundary 번역” 이상으로 커지기 시작하면 application service / mapper / advisor / resolver 분리를 검토한다

## 9. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 클래스는 REST API endpoint이므로 @RestController가 자연스러운가?
- class-level path와 method-level HTTP mapping이 명확한가?
- 입력 출처가 메서드 시그니처에서 드러나는가?
- request DTO와 domain/entity가 분리되어 있는가?
- controller가 repository/transaction/business rule을 직접 품고 있지 않은가?
- 응답 형식이 프로젝트 표준(ApiResult 등)에 맞는가?
- 예외 처리와 공통 응답 보강을 advice 쪽으로 밀어냈는가?
