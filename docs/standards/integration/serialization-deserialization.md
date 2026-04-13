# Integration Serialization / Deserialization 기준

## 1. 목적

이 문서는 외부 API / integration 호출에서 request serialization과 response deserialization 기준을 정의한다.

이 문서의 목표는 다음과 같다.

- provider 계약과 내부 모델을 분리한다
- 외부 payload 변화에 대한 내성을 높인다
- media type, 필드명, null/absent, 에러 바디, 날짜/시간 포맷을 일관되게 처리한다
- serialization concern이 application/domain으로 번지지 않게 한다

## 2. 근거 수준

- Official: Spring Framework / Spring Boot / Jackson 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 Tolerant Reader 같은 실무 패턴을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 외부 serialization은 provider contract가 결정한다

Spring의 REST client는 HTTP 본문을 상위 Java 객체로 읽고 쓰며, JSON 외에도 application/x-www-form-urlencoded, multipart, byte array, XML 같은 형식을 converter로 다룹니다. 따라서 외부 연동 직렬화 기준은 우리 내부 JSON 취향이 아니라 provider가 요구하는 content type, field shape, wire format 이 먼저다.

프로젝트 규칙:

- provider가 JSON을 요구하면 JSON으로
- provider가 form-urlencoded를 요구하면 form으로
- provider가 XML/byte/binary를 요구하면 그 형식으로 보낸다
- “우리 서비스는 JSON 표준이 있으니 외부도 JSON으로 통일”을 금지한다

### 3.2 외부 DTO와 내부 모델은 반드시 분리한다

Spring client가 DTO 변환을 쉽게 해 준다고 해서, provider DTO를 내부 모델처럼 써도 된다는 뜻은 아니다. Tolerant Reader 관점에서도 payload를 읽는 코드는 한 경계에 모아야 나머지 시스템이 변경에 덜 민감해진다.

프로젝트 규칙:

- 외부 request DTO / response DTO는 provider 계약 전용 타입
- application/domain은 외부 DTO를 직접 모른다
- adapter 경계에서 내부 command/result/failure reason으로 변환한다

### 3.3 읽기는 느슨하게, 쓰기는 명시적으로 한다

Jackson의 ignoreUnknown=true는 외부 응답이 필드를 추가해도 파싱을 덜 깨뜨리게 해 준다. 반면 outbound request는 provider가 받지 않는 필드를 보내거나, null과 absent를 헷갈리게 보내면 계약 오류가 생긴다. 따라서 외부 연동에서는 response는 tolerant reader, request는 explicit writer 전략을 기본으로 둔다.

프로젝트 규칙:

- 외부 response DTO는 additive change에 관대할 수 있다
- 외부 request DTO는 보내는 필드를 명시적으로 통제한다
- 내부 객체를 그대로 직렬화해서 provider에 보내지 않는다

## 4. Request serialization 표준

### 4.1 outbound request는 provider 계약에 정확히 맞춘다

Spring REST client는 DTO를 바탕으로 본문을 직렬화하지만, 실제 field name과 media type은 adapter가 정해야 한다. Jackson의 @JsonProperty는 외부 필드명을 DTO 경계에서 맞추는 공식 수단이다.

프로젝트 규칙:

- provider field name mismatch는 외부 request DTO에서 해결한다
- 내부 필드명/도메인 용어를 provider 계약에 맞춰 바꾸지 않는다
- provider-specific enum/string/value shape를 request DTO에 국소화한다

### 4.2 request DTO는 allowlist 방식으로 설계한다

프로젝트 규칙:

- provider에 보낼 필드만 request DTO에 둔다
- 내부 계산값, 디버그 값, 서버 내부 상태를 request DTO에 섞지 않는다
- “언젠가 쓸 수 있으니 같이 보내자”를 금지한다

### 4.3 null과 absent는 provider 계약 기준으로 명시한다

프로젝트 규칙:

- provider가 null과 필드 omission을 다르게 해석하면 반드시 구분한다
- 기본 정책은 “의미가 다르면 DTO와 mapper에서 명시적으로 처리”
- 전역 NON_NULL 같은 설정으로 provider별 의미를 무심코 바꾸지 않는다

### 4.4 media type은 명시적으로 맞춘다

Spring converter는 JSON, form, multipart, byte array, XML 등을 지원한다. 외부 연동에서는 특히 OAuth/token 발급, webhook, 파일 업로드, binary download처럼 JSON이 아닌 형식이 흔하다.

프로젝트 규칙:

- application/json을 기본 추정값으로 두지 않는다
- provider가 요구하는 Content-Type과 Accept를 adapter에서 명시한다
- form 요청은 JSON DTO를 억지로 보내지 않는다

## 5. Response deserialization 표준

### 5.1 external response DTO는 tolerant reader를 기본 검토한다

Jackson의 @JsonIgnoreProperties(ignoreUnknown = true)는 인식하지 못한 필드를 deserialization에서 무시한다. Fowler의 Tolerant Reader도 producer가 필드를 추가해도 consumer가 덜 깨지도록 payload reading을 느슨하게 설계하라고 설명한다.

프로젝트 규칙:

- third-party response DTO는 ignoreUnknown=true를 기본 검토한다
- provider가 필드를 추가해도 우리 파싱이 즉시 깨지지 않게 한다
- 단, first-party API request DTO까지 이 정책을 일반화하지 않는다

### 5.2 success body와 error body를 분리한다

프로젝트 규칙:

- 성공 응답 DTO와 오류 응답 DTO를 따로 둔다
- provider error JSON을 success DTO에 억지로 파싱하지 않는다
- error body는 adapter가 읽고 내부 failure reason/exception으로 번역한다

### 5.3 raw Map/JsonNode는 마지막 수단이다

Spring/Jackson은 상위 객체 매핑과 custom deserializer를 지원한다. 따라서 외부 응답 구조가 완전히 동적이지 않다면 typed DTO가 기본이다. raw map/tree는 계약이 너무 불안정하거나 일부 필드만 읽을 때의 마지막 수단으로 본다.

프로젝트 규칙:

- 기본은 typed response DTO
- 정말 불안정한 payload만 JsonNode/Map 허용
- raw tree를 application/domain까지 들고 가지 않는다
- boundary에서 읽고 안정적인 내부 모델로 바꾼다

### 5.4 외부 enum은 바로 domain enum에 연결하지 않는다

프로젝트 규칙:

- provider enum/string 값은 외부 DTO 또는 mapper 단계에서 해석한다
- domain enum에 provider 값을 직접 박아 넣지 않는다
- provider가 새 enum 값을 추가할 수 있으면 UNKNOWN/기본 처리 전략을 둔다

## 6. 날짜/시간/숫자 규칙

### 6.1 날짜/시간 형식은 provider 계약을 따른다

Jackson의 JavaTimeModule은 java.time 타입을 지원하고, timestamps 기능이 꺼져 있으면 보통 ISO-8601 문자열을 사용한다. 하지만 외부 연동에서는 provider가 epoch millis, string, custom format 중 무엇을 쓰는지가 더 중요하다.

프로젝트 규칙:

- provider가 ISO-8601을 쓰면 Instant/OffsetDateTime 등으로 명시적으로 읽는다
- provider가 epoch number를 쓰면 그 계약을 DTO/커스텀 deserializer에서 처리한다
- 내부 표준 시간 타입을 provider wire format 때문에 오염시키지 않는다

### 6.2 숫자/정밀도는 domain 의미를 잃지 않게 한다

프로젝트 규칙:

- 금액, 환율, 정산 수치처럼 정밀도가 중요한 값은 double을 기본값으로 두지 않는다
- provider가 문자열 금액을 보내면 문자열 → 안전한 내부 수치 타입으로 변환한다
- 숫자 파싱 실패는 provider parsing failure로 다루고 domain 예외와 섞지 않는다

## 7. Jackson / mapper / module 규칙

### 7.1 메서드 안에서 new ObjectMapper()를 만들지 않는다

Spring Boot는 auto-configured JSON mapper와 RestClient.Builder/WebClient.Builder를 제공하고, 그 builder에는 converter/codecs와 적절한 공통 구성이 반영된다. 메서드마다 새 mapper를 만들면 그 구성을 우회하게 된다.

프로젝트 규칙:

- adapter 메서드 안 new ObjectMapper() 금지
- 공통 builder와 공통 mapper를 우선 사용한다
- provider 특수 규칙이 있으면 adapter configuration에서 분리해 구성한다

### 7.2 전역 @JacksonComponent / @JacksonMixin은 진짜 공통 규칙에만 쓴다

Spring Boot는 @JacksonComponent를 자동 등록하고, @JacksonMixin도 auto-configured mapper에 등록한다. 즉, 이 둘은 전역 영향 이 있다. 따라서 provider 하나만을 위한 특수 직렬화 규칙을 전역에 뿌리는 것은 신중해야 한다.

프로젝트 규칙:

- 여러 연동/여러 DTO에 공통인 serializer/deserializer만 전역 등록
- 특정 provider 전용 weird format은 adapter-local configuration 우선
- provider 하나 때문에 전체 애플리케이션 JSON 규칙을 바꾸지 않는다

### 7.3 imperative/reactive client가 쓰는 JSON mapper 경계를 의식한다

Boot는 imperative HTTP clients와 reactive HTTP clients에 대해 각각 pre-configured builder를 제공하고, preferred JSON mapper 설정도 분리해 둔다.

프로젝트 규칙:

- RestClient/WebClient에서 provider-specific codec/mapper를 바꿀 때 범위를 명시한다
- imperative client용 변경이 reactive client 전체에 번지지 않게 한다
- “한 군데 바꾸면 다 되겠지” 식 전역 변경을 지양한다

## 8. 검증 / 번역 규칙

### 8.1 파싱 성공과 비즈니스 성공을 같은 것으로 보지 않는다

프로젝트 규칙:

- JSON/XML/form parsing 성공은 “wire format 해석 성공”일 뿐
- provider가 business failure body를 200으로 줄 수도 있다
- adapter는 파싱 후에 success/error semantics를 다시 해석한다

### 8.2 deserialization 예외는 provider parsing failure로 번역한다

프로젝트 규칙:

- malformed payload, required field missing, unexpected type mismatch는 integration parsing failure로 번역한다
- application/domain이 Jackson 예외 타입을 직접 보지 않게 한다
- provider contract drift 여부를 운영에서 추적 가능하게 한다

## 9. 관측 가능성 규칙

### 9.1 payload 전문 로그를 기본 금지한다

외부 payload는 PII, 토큰, 비밀값, 내부 식별자 등을 포함할 수 있다. 이전 observability 기준과 마찬가지로, serialization/deserialization 문제를 추적한다는 이유로 request/response 전문을 기본 로그에 남기지 않는다. 이 점은 OWASP의 민감정보 로그 금지 원칙과도 맞다.

프로젝트 규칙:

- 기본 로그는 provider, operation, status, contentType, payloadBytes, parse failure type 정도만
- payload 원문은 기본 금지
- 꼭 필요하면 테스트/격리 환경에서 제한적으로 남긴다

### 9.2 parse failure는 contract drift 신호로 남긴다

프로젝트 규칙:

- deserialization 실패는 단순 예외로 묻지 않는다
- provider, operation, content type, failing field/shape 정도를 안전하게 남긴다
- “provider contract가 변했을 수 있음”을 운영에서 추적할 수 있어야 한다

## 10. 테스트 규칙

### 10.1 외부 DTO는 fixture 기반 계약 테스트를 둔다

프로젝트 규칙:

- 대표 성공 응답
- 대표 오류 응답
- provider가 필드를 추가한 응답
- 일부 필드 누락 응답
- 에 대한 parsing 테스트를 둔다
- provider 예시 payload나 실제 캡처 샘플을 fixture로 관리할 수 있다

### 10.2 request serialization도 golden sample로 확인한다

프로젝트 규칙:

- provider에 보내는 JSON/form/XML shape를 golden sample로 검증한다
- field name, null/absent, date/time format, enum value가 계약대로 직렬화되는지 확인한다
- “직렬화는 framework가 알아서 하겠지”에 기대지 않는다

## 11. 다른 문서와의 경계

이 문서는 외부 provider payload의 serialization/deserialization 만 다룬다.
아래 주제의 source of truth는 별도 문서다.

- external API client structure
- timeout
- retry
- outbound idempotency
- fallback
- exception translation

이 문서는 위 문서를 반복하지 않고, payload contract를 읽고 쓰는 경계 규칙 만 정의한다.

## 12. 금지 규칙

다음은 기본 금지다.

- 내부 domain/entity를 외부 request/response DTO로 직접 사용
- provider response DTO를 application/domain 시그니처에 그대로 전달
- 메서드 안 new ObjectMapper() 생성
- 특정 provider 응답 대응을 위해 전역 mapper 규칙을 무심코 변경
- first-party API strict 정책과 external response tolerant 정책을 혼동
- success/error body를 같은 DTO로 억지 파싱
- payload 전문 로그를 기본으로 남김
- 외부 enum/string 값을 바로 domain enum에 박아 넣음

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- provider request/response DTO와 내부 모델이 분리되어 있는가?
- outbound request가 provider contract를 정확히 반영하는가?
- external response는 additive change에 대해 필요한 만큼 tolerant한가?
- success body와 error body DTO가 분리되어 있는가?
- null과 absent 의미를 provider 계약 기준으로 다루는가?
- provider-specific weird format이 adapter 경계 안에 갇혀 있는가?
- RestClient/WebClient의 공통 builder/mapper 구성을 우회하지 않는가?
- serialization/deserialization fixture 테스트가 있는가?
