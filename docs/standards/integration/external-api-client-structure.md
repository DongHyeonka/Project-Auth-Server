# External API Client Structure 기준

## 1. 목적

이 문서는 외부 API 호출용 client 구조와 책임 분리를 정의한다.

이 문서의 목표는 다음과 같다.

- 외부 연동 코드를 application/domain에서 분리한다
- HTTP client 선택 기준을 일관되게 만든다
- request/response DTO, mapper, exception translation 위치를 명확히 한다
- 관측 가능성, 설정, 인증 헤더 주입, 공통 customization을 한곳에 모은다

## 2. 근거 수준

- Official: Spring Framework / Spring Boot 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 지원 방식 위에 일반적인 실무 연동 구조를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 외부 API client는 infrastructure adapter다

Spring은 RestClient, WebClient, HTTP Service Client를 모두 “원격 HTTP 서비스 호출” 도구로 제공한다. 즉, 이들은 비즈니스 로직이 아니라 외부 시스템 경계 접근 수단이다. 이 프로젝트에서는 외부 API client를 기본적으로 infrastructure 레이어의 adapter로 본다.

프로젝트 규칙:

- 외부 API client는 infrastructure/integration adapter에 둔다
- application/domain이 RestClient, WebClient, ResponseEntity, HTTP status, provider-specific DTO를 직접 다루지 않는다
- 외부 호출은 port/adapter 경계를 통해 사용한다

### 3.2 외부 API 연동 구조의 기본 단위는 “adapter + DTO + mapper + translation”이다

Spring이 HTTP client 자체는 제공하지만, 어떤 DTO를 어떻게 매핑하고 어떤 예외로 번역할지는 애플리케이션이 책임져야 한다. 따라서 이 프로젝트는 외부 API client를 단순한 HTTP 호출 클래스가 아니라 연동 adapter 패키지로 다룬다.

프로젝트 기본 구조:

- client adapter
- external request DTO
- external response DTO
- mapper / translator
- provider-specific exception translation
- 설정(properties / builder customization)

### 3.3 외부 연동 계약과 내부 모델은 분리한다

Spring의 message conversion과 HTTP service interface는 DTO를 손쉽게 직렬화/역직렬화해 주지만, 그것이 곧 외부 DTO를 내부 모델처럼 써도 된다는 뜻은 아니다. 이 프로젝트에서는 외부 API request/response DTO를 내부 application/domain 모델과 분리한다.

프로젝트 규칙:

- 외부 API request/response DTO는 provider contract 전용 타입이다
- application/domain은 외부 DTO를 직접 알지 않는다
- adapter 경계에서 내부 command/result 또는 domain 값으로 변환한다

## 4. HTTP client 선택 기준

### 4.1 imperative 애플리케이션 기본값은 RestClient

Spring Boot는 비리액티브 애플리케이션이면 RestClient 또는 RestTemplate를 사용할 수 있다고 설명하고, Spring Framework는 RestClient를 동기식 fluent API 로 설명하며, RestTemplate는 현재 RestClient 쪽이 더 권장되는 방향이라고 명시한다.

프로젝트 규칙:

- 일반 Spring MVC / imperative 애플리케이션의 외부 HTTP 호출 기본값은 RestClient
- 새 코드에서 RestTemplate를 기본 선택지로 두지 않는다
- 동기 블로킹 호출이 자연스러운 use case에는 RestClient를 우선한다

### 4.2 reactive 애플리케이션 또는 진짜 non-blocking 경계에는 WebClient

Spring Boot는 WebFlux 기반 non-blocking reactive 애플리케이션이면 WebClient 사용을 권장한다. WebClient는 fully reactive client이며, Boot는 WebClient.Builder를 미리 구성해서 제공한다.

프로젝트 규칙:

- 애플리케이션 자체가 reactive이거나, non-blocking end-to-end 흐름이 필요한 경우 WebClient
- 단순히 최신 API라는 이유만으로 imperative 서비스에 WebClient를 기본 도입하지 않는다
- reactive client를 도입할 때는 호출부부터 반환 타입, backpressure, timeout 모델까지 함께 고려한다

### 4.3 HTTP Service Client는 선언적 계약이 분명할 때 허용한다

Spring은 @HttpExchange, @GetExchange, @PostExchange 등으로 정의한 인터페이스에 프록시를 붙이는 HTTP Service Client를 공식 지원하고, Boot는 이를 import하고 group으로 묶는 기능도 제공한다.

프로젝트 규칙:

- 외부 API 계약이 안정적이고 메서드 시그니처가 선언적으로 잘 드러나는 경우 HTTP Service Client 허용
- 다만 복잡한 동적 요청 조립, 세밀한 에러 처리, 낮은 수준의 HTTP 제어가 많으면 RestClient/WebClient를 우선 검토한다
- 선언형 인터페이스를 쓰더라도 adapter 경계와 DTO 분리 규칙은 그대로 유지한다

## 5. Builder / 공통 구성 규칙

### 5.1 Boot가 자동 구성한 builder를 주입해서 사용한다

Spring Boot는 WebClient.Builder와 RestClient.Builder를 prototype bean으로 자동 구성하고, 이를 주입해 사용하는 것을 강하게 권장한다. Boot가 제공하는 builder를 사용해야 HTTP resource 공유, codec 반영, 적절한 request factory, 그리고 관측/계측이 함께 적용된다. RestClient.create()를 직접 쓰면 auto-configuration과 customizer 적용이 따라오지 않는다.

프로젝트 규칙:

- RestClient.Builder / WebClient.Builder는 주입받아 사용한다
- RestClient.create() / WebClient.builder()를 코드 곳곳에서 직접 호출하는 것을 기본 금지한다
- 공통 관측, SSL, codec, 인증 헤더, timeout 설정을 우회하지 않는다

### 5.2 공통 customization은 builder/customizer/group에 둔다

Spring Boot는 RestClientCustomizer, WebClient.Builder, SSL bundle 적용, HTTP Service client group 등을 통해 공통 구성을 모을 수 있다고 설명한다. HTTP Service group은 URL뿐 아니라 timeout, SSL, auth customization 같은 공통 특성을 공유할 수 있다.

프로젝트 규칙:

- base URL, timeout, SSL, 공통 header, user-agent, auth header 삽입은 공통 구성으로 관리
- client마다 같은 interceptor/filter/header 삽입 로직을 복붙하지 않는다
- provider 단위의 공통 설정은 group 또는 전용 configuration으로 묶는다

### 5.3 builder는 “전역 기본값 + 클라이언트별 좁은 추가 설정” 구조로 쓴다

Spring Boot 문서는 RestClient.Builder customization은 범위를 좁게 적용할수록 좋고, builder가 stateful이므로 필요하면 clone을 고려하라고 설명한다.

프로젝트 규칙:

- 전역 공통값은 customizer/configuration
- 특정 provider에만 필요한 설정은 그 adapter 구성 지점에서 추가
- 하나의 builder를 여러 외부 시스템에 무비판적으로 뒤섞어 쓰지 않는다

## 6. 패키지 / 타입 구조 규칙

### 6.1 provider별 또는 capability별로 구조를 분리한다

프로젝트 규칙:

- 외부 시스템이 다르면 패키지를 분리한다
- 하나의 외부 시스템 안에서도 계약이 크면 capability 단위로 나눌 수 있다

권장 예:

```text
integration/keycloak/...
integration/payment/...
integration/email/...
```

또는

```text
integration/keycloak/token/...
integration/keycloak/user/...
```

### 6.2 한 adapter는 한 외부 계약 또는 한 capability를 담당한다

프로젝트 규칙:

- 하나의 client class가 외부 시스템 전체를 거대한 god client처럼 다루지 않는다
- 토큰 발급, 사용자 조회, 세션 폐기처럼 책임이 다르면 분리한다
- 다만 지나치게 잘게 쪼개서 공통 설정이 흩어지지 않게 provider 구성과 capability 구성을 함께 본다

### 6.3 외부 DTO, 내부 결과, 매퍼를 분리한다

프로젝트 규칙:

- *Request, *Response는 외부 계약용 DTO
- *Result, *Command, *FailureReason 등은 내부용 모델
- DTO → 내부 결과 변환은 mapper/translator가 담당
- application/domain은 외부 JSON 필드명과 provider-specific enum을 모른다

## 7. 인증 / 헤더 / URL 규칙

### 7.1 base URL은 코드 하드코딩이 아니라 설정 기반으로 둔다

Spring Boot는 HTTP Service groups에서 logical name과 property 기반 URL lookup을 사용하는 방향을 설명하며, absolute URL 하드코딩은 production에 이상적이지 않다고 말한다.

프로젝트 규칙:

- base URL은 properties/configuration으로 관리
- 코드 안 https://... 하드코딩을 기본 금지
- 환경별 URL 차이는 설정으로 해결한다

### 7.2 인증 헤더 삽입은 adapter 공통 레이어에서 처리한다

Spring 문서는 RestClient에 default header, interceptor, request initializer를 둘 수 있고, HTTP Service group에도 authorization header 삽입 같은 customization을 연결할 수 있다고 설명한다.

프로젝트 규칙:

- Authorization, API key, user-agent, correlation header는 공통 client 구성에서 삽입
- business 로직에서 매번 header를 조립하지 않는다
- 토큰 갱신/획득 로직도 provider adapter 경계에 둔다

### 7.3 URI template와 path variable을 우선 사용한다

Spring Framework는 RestClient, WebClient, RestTemplate가 URI template와 URI builder를 지원한다고 설명한다.

프로젝트 규칙:

- string concatenation으로 URL을 만들지 않는다
- path/query 조립은 template / builder 방식으로 처리한다
- query parameter 의미가 드러나게 작성한다

## 8. 반환 / 예외 / 번역 규칙

### 8.1 adapter는 ResponseEntity, raw status, client exception을 그대로 위로 올리지 않는다

Spring의 client는 HTTP status, body, exception을 직접 다룰 수 있지만, application/domain이 그 디테일을 그대로 보게 두면 외부 계약이 내부 계층으로 번진다.

프로젝트 규칙:

- adapter는 내부 결과 타입 또는 port 계약 타입을 반환한다
- application은 WebClientResponseException, HttpStatusCodeException, ClientResponse 같은 타입을 직접 다루지 않는다
- HTTP status 해석은 adapter 안에서 끝낸다

### 8.2 provider-specific 실패는 integration exception으로 번역한다

프로젝트 규칙:

- 외부 401/403/404/409/5xx를 그대로 application에 노출하지 않는다
- provider-specific error body는 integration exception 또는 내부 failure reason으로 번역한다
- 예외 번역 상세 규칙은 별도 exception-translation.md에서 source of truth로 둔다

### 8.3 2xx만 성공으로 보는 단순 규칙을 넘어서 provider 계약을 해석한다

프로젝트 규칙:

- HTTP 200이어도 business failure payload이면 실패로 번역할 수 있다
- 반대로 일부 4xx가 provider 계약상 “정상적인 부재/중복 상태”라면 내부 의미로 적절히 번역한다
- 성공/실패 판정 기준은 provider contract 단위로 명시한다

## 9. DTO / 직렬화 규칙

### 9.1 외부 요청/응답 DTO는 provider contract에 맞춘다

Spring은 RestClient, WebClient, HTTP Service Client 모두 message conversion으로 DTO를 JSON과 매핑한다. 이 DTO는 provider JSON 계약에 맞춰야 하며, 내부 표준 DTO와 동일할 필요가 없다.

프로젝트 규칙:

- 외부 JSON 필드명은 외부 DTO에서만 해결한다
- provider-specific field naming, enum, optionality는 외부 DTO에 국소화한다
- 내부 모델 필드명을 외부 계약 때문에 바꾸지 않는다

### 9.2 외부 응답 파싱 정책은 first-party API보다 더 lenient할 수 있다

Spring/Jackson 조합은 DTO 역직렬화를 유연하게 지원한다. 외부 시스템은 필드 추가/응답 shape 변화가 일어날 수 있으므로, third-party response DTO는 first-party API request DTO보다 lenient 정책을 택할 수 있다. 이 세부 기준은 별도 serialization 문서에서 다루되, external client 구조에서도 이 방향을 따른다.

프로젝트 규칙:

- 외부 response DTO는 unknown field 허용 가능
- 외부 request DTO는 provider 요구에 맞춰 엄격하게 작성
- 내부 domain/application DTO와 정책을 섞지 않는다

## 10. 관측 가능성 규칙

### 10.1 외부 API client는 관측 가능해야 한다

Spring Boot Actuator는 RestTemplate, WebClient, RestClient의 HTTP client instrumentation을 지원하고, 이를 위해 auto-configured builder를 사용하라고 설명한다. Spring Framework observability 문서는 기본 저카디널리티 키로 method, uri template, client.name, status, outcome, error를 정의한다.

프로젝트 규칙:

- 외부 API client는 auto-configured builder를 통해 관측 가능성을 확보한다
- metrics/traces/logs에서 최소한 client.name, method, uri template, status, error를 추적 가능하게 한다
- raw full URL과 payload 전문을 로그 기본값으로 남기지 않는다

### 10.2 URI template를 유지한다

Spring observability 문서는 low cardinality key로 uri template를 쓰고, host/port를 제외한 template 개념을 사용한다.

프로젝트 규칙:

- 외부 호출 관측에서는 가능한 한 URI template를 유지한다
- /users/123 같은 실제 path 대신 /users/{id} 같은 템플릿이 추적 가능하게 한다
- 메트릭 태그에 고카디널리티 path를 그대로 쓰지 않는다

## 11. 문서 간 경계

이 문서는 구조와 책임 분리를 다룬다. 아래 주제의 세부 규칙은 별도 문서를 source of truth로 둔다.

- timeout
- retry
- idempotency
- serialization/deserialization
- fallback
- exception translation

이 문서는 위 주제들을 다시 처음부터 반복하지 않고, 외부 API client 구조 안에서 어디에 둘지만 정의한다.

## 12. 금지 규칙

다음은 기본 금지다.

- controller/application/domain에서 RestClient/WebClient 직접 호출
- RestClient.create() / WebClient.builder()를 여기저기서 직접 생성
- base URL 하드코딩
- 외부 DTO를 내부 application/domain 메서드 시그니처에 그대로 전달
- ResponseEntity, raw status code, client exception을 그대로 내부에 전파
- giant external client 하나에 모든 provider capability 몰아넣기
- 인증 헤더/공통 header를 business code에서 매번 조립
- provider payload 전문을 기본 로그로 남기기

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 외부 연동 코드는 infrastructure adapter에 위치하는가?
- 애플리케이션 성격에 맞게 RestClient/WebClient/HTTP Service Client를 선택했는가?
- auto-configured builder를 주입해 사용하고 있는가?
- base URL, auth, timeout, SSL, 공통 customization이 공통 구성에 모여 있는가?
- 외부 request/response DTO와 내부 모델이 분리되어 있는가?
- adapter가 provider-specific HTTP 디테일을 내부로 누수시키지 않는가?
- 관측 가능성(metrics/traces/logs)이 확보되어 있는가?
