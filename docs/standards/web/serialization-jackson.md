# Serialization / Jackson 기준

## 1. 목적

이 문서는 API JSON 직렬화/역직렬화와 Jackson 사용 기준을 정의한다.

이 문서의 목표는 다음과 같다.

- JSON 계약을 DTO 중심으로 안정적으로 관리한다
- Jackson 설정과 애노테이션 사용을 일관되게 만든다
- domain/entity에 transport concern이 스며들지 않게 한다
- 날짜/시간, null, unknown field, 민감 필드, 커스텀 serializer의 기준을 명확히 한다

## 2. 근거 수준

- Official: Spring Framework / Spring Boot / Jackson 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 확장 지점 위에 일반적인 실무 API 설계 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 직렬화는 web boundary concern이다

Spring MVC에서 JSON 직렬화/역직렬화는 HttpMessageConverter가 담당하고, Jackson converter는 typed bean이나 untyped map을 JSON으로 읽고 쓸 수 있습니다. 따라서 이 프로젝트에서 serialization/Jackson 규칙은 presentation/web boundary concern 으로 본다.

프로젝트 규칙:

- JSON 계약은 controller boundary의 request/response DTO가 중심이다
- domain/entity가 JSON 계약의 중심이 되지 않는다
- Jackson 규칙 때문에 domain 모델을 뒤틀지 않는다

### 3.2 DTO 설계가 Jackson 애노테이션보다 우선한다

Spring과 Jackson은 애노테이션으로 매핑을 많이 바꿀 수 있게 해 주지만, 이 프로젝트의 기본값은 애노테이션으로 억지로 맞추기보다 DTO를 명시적으로 분리하는 것이다. Spring이 @JsonView, mixin, custom mapper를 지원하더라도, 그 지원 자체가 곧 그것을 기본 설계 수단으로 삼으라는 뜻은 아니다.

프로젝트 규칙:

- request DTO / response DTO 분리를 기본값으로 둔다
- Jackson 애노테이션은 보조 수단 이다
- JSON shape를 맞추기 위해 entity/domain에 애노테이션을 덕지덕지 붙이지 않는다

### 3.3 전역 규칙은 mapper 설정으로, 예외는 DTO에서 처리한다

Spring Boot는 Jackson mapper를 자동 구성하고 다양한 spring.jackson.* 설정을 제공한다. 즉, naming, inclusion, timezone, visibility 같은 공통 규칙은 전역 mapper 정책으로 관리할 수 있다.

프로젝트 규칙:

- 공통 규칙은 전역 Jackson 설정으로 관리한다
- 특정 계약 예외만 DTO/필드 레벨 애노테이션으로 처리한다
- controller마다 new ObjectMapper()를 만들어 제각각 직렬화하지 않는다

## 4. DTO 우선 규칙

### 4.1 request/response DTO가 JSON 계약의 source of truth다

프로젝트 규칙:

- JSON 요청 형식은 request DTO가 정의한다
- JSON 응답 형식은 response DTO가 정의한다
- entity/domain/persistence model을 직렬화 계약의 source of truth로 두지 않는다

### 4.2 entity와 Jackson 애노테이션을 결합하지 않는다

프로젝트 규칙:

- JPA entity를 직접 JSON 응답으로 내보내지 않는다
- entity에 @JsonIgnore, @JsonManagedReference, @JsonBackReference 같은 애노테이션으로 API 문제를 해결하는 것을 기본 금지한다
- 엔티티 순환 참조, lazy loading, 내부 식별자 노출 문제는 DTO 변환으로 해결한다

### 4.3 request DTO와 response DTO를 하나로 합치지 않는다

프로젝트 규칙:

- 하나의 DTO를 입력/출력 겸용으로 두는 것을 기본 금지한다
- 비밀번호, 내부 상태, 서버 소유 필드 같은 방향성 차이는 DTO 분리로 해결한다
- @JsonProperty(access = WRITE_ONLY/READ_ONLY)는 예외적 escape hatch일 뿐, 기본 설계 수단이 아니다

## 5. 필드 이름 규칙

### 5.1 기본 naming은 lowerCamelCase다

프로젝트 규칙:

- 내부 API JSON 기본 naming은 lowerCamelCase
- 프로젝트 전체 기본 naming 전략은 하나로 유지한다
- DTO마다 제각각 snake_case / kebab-case / camelCase를 섞지 않는다

### 5.2 외부 계약 이름 변경은 @JsonProperty로 국소화한다

Jackson의 @JsonProperty는 외부에 노출할 property name을 지정하는 데 사용할 수 있습니다.

프로젝트 규칙:

- 외부 공급자 계약이나 레거시 호환 때문에 필드명이 다를 때만 @JsonProperty를 사용한다
- 내부 표준 naming을 DTO 전체에 포기하지 않는다
- naming mismatch를 해결하려고 domain 필드명을 외부 계약에 맞춰 바꾸지 않는다

## 6. 날짜/시간 규칙

### 6.1 public timestamp는 java.time + ISO-8601 문자열을 기본으로 한다

JavaTimeModule은 java.time 타입을 지원하며, WRITE_DATES_AS_TIMESTAMPS가 꺼져 있으면 대부분의 java.time 타입을 ISO-8601 문자열로 직렬화합니다. Spring Boot Actuator API도 timestamp 입력을 ISO 8601 offset date-time으로 요구합니다.

프로젝트 규칙:

- public timestamp 기본값은 ISO-8601 문자열
- epoch number timestamp를 공개 API 기본값으로 두지 않는다
- java.util.Date보다 java.time 타입을 우선한다

### 6.2 시점(timestamp)은 OffsetDateTime 또는 Instant를 우선한다

프로젝트 규칙:

- 외부 시스템과 교환하는 timestamp는 OffsetDateTime 또는 Instant를 우선 검토한다
- 시간대 정보가 없는 LocalDateTime을 public timestamp 기본값으로 두지 않는다
- 날짜만 필요하면 LocalDate
- 시간만 의미가 있으면 정말 필요한 경우에만 LocalTime

### 6.3 전역 timezone/date-format은 명시적으로 관리한다

Spring Boot는 spring.jackson.date-format, spring.jackson.time-zone 같은 전역 설정 프로퍼티를 제공합니다.

프로젝트 규칙:

- 시간 직렬화 정책은 전역 설정 또는 공통 mapper 설정으로 명시한다
- DTO별 임의 @JsonFormat 남발을 지양한다
- 특정 필드만 예외 형식이 필요한 경우에만 필드 레벨 포맷을 둔다

## 7. null / absent 규칙

### 7.1 null omission은 계약 변경 효과가 있으므로 신중하게 쓴다

Jackson의 @JsonInclude와 Boot의 spring.jackson.default-property-inclusion은 null/empty 값을 응답에서 제외하도록 설정할 수 있습니다. 하지만 필드 omission은 단순 직렬화 최적화가 아니라 응답 계약 의미 변경 이 될 수 있다.

프로젝트 규칙:

- 전역 NON_NULL을 무비판적으로 켜지 않는다
- 필드 omission이 API 의미상 “존재하지 않음”을 뜻할 때만 선택적으로 쓴다
- “null일 때 숨기면 보기 좋다”는 이유만으로 계약을 흔들지 않는다

### 7.2 envelope와 payload의 null 정책을 구분한다

프로젝트 규칙:

- ApiResult 같은 공통 envelope 필드는 가능한 안정적으로 유지한다
- business payload 필드 omission 여부는 DTO 계약 단위에서 결정한다
- data, meta, 상세 필드의 null/absent 정책을 뒤섞지 않는다

## 8. unknown property 규칙

### 8.1 Spring 기본 동작에 기대기보다 프로젝트 정책을 명시한다

Spring의 Jackson2ObjectMapperBuilder 기본값은 FAIL_ON_UNKNOWN_PROPERTIES를 끄고, Jackson의 @JsonIgnoreProperties(ignoreUnknown=true)도 unknown input field를 무시하는 용도로 쓸 수 있습니다.

프로젝트 규칙:

- unknown property 정책은 API군 단위의 명시적 정책 으로 둔다
- DTO마다 제멋대로 strict / lenient를 섞지 않는다

### 8.2 first-party API request DTO는 기본적으로 strict를 권장한다

프로젝트 권장 규칙:

- 우리가 소유한 public/internal API request DTO는 기본적으로 unknown field를 실패 처리 하도록 권장
- client 오타, 잘못된 계약 사용, 조용한 무시를 빨리 발견하는 쪽을 선호한다
- 필요하면 Spring 기본값을 프로젝트 정책에 맞게 override한다

### 8.3 external webhook / third-party callback DTO는 lenient를 허용한다

Jackson의 @JsonIgnoreProperties(ignoreUnknown=true)는 deserialization 시 인식하지 못하는 필드를 무시하도록 하는 공식 수단입니다.

프로젝트 규칙:

- 외부 공급자 webhook, callback, third-party response DTO는 ignoreUnknown=true를 허용할 수 있다
- 외부가 필드를 추가해도 우리 파싱이 깨지지 않아야 하는 integration DTO에 한해 사용한다
- 이 경우에도 first-party API request DTO와 같은 기준으로 섞지 않는다

## 9. 애노테이션 사용 규칙

### 9.1 @JsonProperty(access = ...)는 예외적으로만 쓴다

Jackson은 property access를 read-only / write-only / read-write로 제어할 수 있습니다.

프로젝트 규칙:

- WRITE_ONLY는 비밀번호처럼 입력만 받고 출력하면 안 되는 필드에 한해 제한적으로 사용
- READ_ONLY는 서버 계산값처럼 응답에는 나가지만 입력받으면 안 되는 필드에 한해 제한적으로 사용
- 기본 해결책은 여전히 request/response DTO 분리다

### 9.2 @JsonView는 public API 기본 설계 수단으로 쓰지 않는다

Spring MVC는 @JsonView를 controller method에서 지원하지만, 메서드당 직접 지정 가능한 view는 하나이며, 여러 shape를 장기 유지하는 public API 계약 관리에는 DTO 분리가 더 명확하다.

프로젝트 규칙:

- @JsonView를 public API summary/detail/versioning의 기본 수단으로 사용하지 않는다
- endpoint별 shape 차이는 별도 response DTO로 표현한다
- @JsonView는 관리용/내부용 제한된 케이스에서만 예외적으로 검토한다

### 9.3 @JsonIgnore는 마지막 수단으로 쓴다

프로젝트 규칙:

- 필드 숨김 문제를 @JsonIgnore로 즉석에서 막기보다 DTO 구조를 먼저 재검토한다
- @JsonIgnore가 많아지면 DTO/entity 책임이 흐려졌다는 신호로 본다

## 10. 커스텀 serializer/deserializer 규칙

### 10.1 교차 절단(cross-cutting) 직렬화는 전역 구성요소로 등록한다

Spring MVC는 custom JsonMapper/builder를 converter에 주입할 수 있고, Spring Boot는 전역 mapper 설정과 커스터마이저, 모듈, mixin 등록 지점을 제공합니다.

프로젝트 규칙:

- 여러 DTO에서 반복되는 직렬화 규칙은 전역 Jackson 설정/모듈/커스터마이저로 올린다
- controller 안에서 ad-hoc serializer를 만들지 않는다
- DTO 한두 개만을 위한 국소 예외는 DTO 애노테이션으로 처리할 수 있다

### 10.2 third-party 타입 수정은 mixin을 우선 검토한다

Spring Boot는 @JacksonMixin을 스캔해 auto-configured mapper에 등록할 수 있습니다.

프로젝트 규칙:

- 직접 수정할 수 없는 third-party 타입의 직렬화 변경은 mixin을 우선 검토한다
- 우리 코드의 DTO에까지 mixin을 남발하지 않는다
- mixin은 “타입 소유권이 우리에게 없을 때”의 수단이다

## 11. ObjectMapper 사용 규칙

### 11.1 controller에서 new ObjectMapper()를 만들지 않는다

Spring MVC와 Boot는 이미 message converter와 전역 mapper 구성을 제공한다. controller가 직접 새 mapper를 만들면 전역 규칙, module, naming, inclusion, time 설정을 우회하기 쉽다.

프로젝트 규칙:

- controller/service에서 new ObjectMapper() 금지
- 정말 수동 직렬화가 필요하면 주입된 공용 mapper 또는 전용 serializer 컴포넌트를 사용한다
- “이 endpoint만 예외”를 위해 로컬 mapper를 만들지 않는다

### 11.2 테스트도 production mapper와 같은 규칙을 검증한다

프로젝트 규칙:

- serialization contract가 중요한 DTO는 직렬화/역직렬화 테스트를 둔다
- production과 다른 임시 mapper 설정으로 테스트하지 않는다
- 날짜, null, enum, unknown property, field name 같은 계약 포인트를 테스트한다

## 12. 금지 규칙

다음은 기본 금지다.

- entity를 직접 JSON request/response 모델로 사용
- controller에서 new ObjectMapper() 생성
- @JsonView를 public API versioning/shape 관리 기본 수단으로 사용
- 전역 NON_NULL 같은 omission 정책을 계약 검토 없이 켜기
- unknown property strict/lenient 정책을 DTO마다 제각각 섞기
- 외부 계약 이름 변경 문제를 domain/entity 필드명 변경으로 해결
- 민감 필드 숨김을 @JsonIgnore만으로 땜질
- 숫자 timestamp를 public API 기본값으로 사용

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 JSON 계약은 DTO가 정의하고 있는가?
- entity/domain에 Jackson concern이 새어 나오지 않았는가?
- 날짜/시간이 ISO-8601 + 적절한 java.time 타입으로 표현되는가?
- null omission이 계약 의도를 반영하는가?
- unknown property 정책이 API군 단위로 일관적인가?
- @JsonProperty, @JsonIgnore, @JsonView 사용이 정말 필요한 예외인가?
- 전역 mapper 규칙을 우회하는 로컬 ObjectMapper가 없는가?
