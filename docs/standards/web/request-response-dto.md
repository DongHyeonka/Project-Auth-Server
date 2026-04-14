# Request / Response DTO 기준

## 1. 목적

이 문서는 API 요청/응답에 사용하는 DTO의 역할, 위치, 설계 규칙을 정의한다.

이 문서의 목표는 다음과 같다.

- request DTO와 response DTO를 명확히 분리한다.
- web transport model과 domain/application model을 섞지 않는다.
- controller 바인딩 모델의 보안과 변경 가능성을 통제한다.
- 응답 포맷을 domain/entity 구조가 아니라 API 계약 중심으로 설계한다.

## 2. 근거 수준

- Official: Spring Framework 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 문서의 확장 지점 위에 일반적인 실무 API 설계 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 DTO는 transport model이다

Spring MVC는 @RequestBody와 @ModelAttribute를 통해 웹 입력을 객체로 바인딩하고, @ResponseBody/ResponseEntity를 통해 객체를 응답으로 직렬화한다. 이 프로젝트에서 DTO는 그 바운더리에서만 쓰는 transport model로 정의한다. DTO는 HTTP 요청/응답 계약을 표현하는 객체이지, domain/entity/application command 자체가 아니다.

프로젝트 규칙:

- request DTO는 HTTP 입력 계약을 표현한다
- response DTO는 HTTP 출력 계약을 표현한다
- DTO는 domain/entity/persistence model을 그대로 노출하는 수단이 아니다

### 3.2 request DTO와 response DTO를 분리한다

Spring 공식 문서는 @ModelAttribute 대상에 대해 웹 바인딩 전용 객체 사용을 권장한다. 이 원칙을 request/response 전체로 확장하면, 읽기 모델과 쓰기 모델을 분리하는 것이 안전하고 유지보수성이 높다.

프로젝트 규칙:

- 하나의 DTO를 request와 response에 동시에 재사용하지 않는다
- create/update 입력 DTO와 조회 응답 DTO를 분리한다
- “필드가 비슷하니까 같은 DTO”를 기본값으로 두지 않는다

### 3.3 entity와 DTO를 섞지 않는다

Spring은 @ModelAttribute 바인딩 모델 설계 시 보안을 고려해 전용 모델 객체 또는 constructor binding only를 권장한다. 이는 웹 바인딩 대상을 domain/entity와 분리하라는 실무 방향과 맞닿아 있다.

프로젝트 규칙:

- entity를 @RequestBody 대상으로 쓰지 않는다
- entity를 @ModelAttribute 대상으로 쓰지 않는다
- entity를 API response body로 직접 반환하지 않는다
- domain object를 외부 계약 모델로 직접 노출하지 않는다

## 4. Request DTO 표준

### 4.1 JSON body는 전용 request DTO로 받는다

Spring 공식 문서 기준으로 @RequestBody는 요청 본문을 HttpMessageConverter로 객체에 역직렬화한다. 따라서 JSON API 입력은 전용 request DTO에 받는 것이 기본이다.

프로젝트 규칙:

- JSON body는 전용 request DTO에 받는다
- request DTO는 controller boundary에서만 사용한다
- request DTO를 그대로 domain/service 내부에 전파하지 않는다
- controller 또는 mapper에서 application/domain 입력으로 변환한다

### 4.2 form/query/path 기반 입력도 web 전용 모델로 받는다

Spring 공식 문서 기준으로 @ModelAttribute는 request parameters, path variables, headers를 모델 객체에 바인딩할 수 있다. Spring은 이 모델을 웹 바인딩 전용으로 설계하거나 constructor binding only를 권장한다.

프로젝트 규칙:

- query/form/multipart 조합 입력도 전용 request model 사용을 우선 검토한다
- @ModelAttribute 대상은 web binding 전용 객체로 제한한다
- setter/property binding을 허용해야 한다면 바인딩 가능한 필드를 의식적으로 통제한다

### 4.3 request DTO는 입력 계약만 표현한다

프로젝트 규칙:

- request DTO에는 HTTP 입력 필드만 둔다
- 서버 내부 계산값, 조회 결과, 인증 결과, 저장 전용 필드를 넣지 않는다
- “나중에 응답에도 쓸 수 있으니 미리 넣어두기”를 금지한다

이 규칙은 Practice + Project Recommendation 이다.

### 4.4 request DTO는 validation 경계를 드러낼 수 있어야 한다

Spring MVC는 @RequestBody/@ModelAttribute 입력에 @Valid/@Validated 검증을 적용할 수 있다. 따라서 request DTO는 request shape validation이 걸릴 자리라는 점이 명확해야 한다.

프로젝트 규칙:

- request DTO에는 입력 형식 검증에 필요한 constraint를 둘 수 있다
- business rule을 request DTO constraint에 과도하게 숨기지 않는다
- request DTO validation 통과를 domain correctness의 보장으로 간주하지 않는다

### 4.5 request DTO는 API 변경에 안전해야 한다

Spring이 전용 바인딩 객체와 constructor binding을 권장하는 이유는 바인딩 범위를 명시적으로 통제하기 위함이다. 이 프로젝트에서는 이를 API 변경 안전성 원칙으로 확장한다.

프로젝트 규칙:

- request DTO는 허용된 입력만 명시적으로 받는다
- 무분별한 setter/property 확장을 지양한다
- 클라이언트가 보내면 안 되는 필드가 우연히 열리지 않도록 설계한다

## 5. Response DTO 표준

### 5.1 응답은 response DTO 또는 표준 envelope로 반환한다

Spring MVC는 @ResponseBody와 ResponseEntity를 통해 객체를 응답으로 직렬화한다. 이 프로젝트에서는 응답 body를 domain/entity가 아니라 response DTO 또는 ApiResult<ResponseDto> 같은 표준 envelope로 반환한다.

프로젝트 규칙:

- 응답 본문은 response DTO 또는 ApiResult<ResponseDto>로 표현한다
- entity, aggregate, persistence projection을 그대로 반환하지 않는다
- Map<String, Object> 기반 임시 응답은 승인 후보 코드에서 지양한다

### 5.2 response DTO는 외부 계약 중심으로 설계한다

프로젝트 규칙:

- response DTO는 클라이언트가 실제로 필요한 필드만 포함한다
- 내부 식별자, 상태값, 구현 세부사항을 무분별하게 노출하지 않는다
- domain 모델 필드 구조를 그대로 따라가는 것을 기본값으로 두지 않는다

이 항목은 Practice + Project Recommendation 이다.

### 5.3 request DTO와 response DTO를 상호 재사용하지 않는다

프로젝트 규칙:

- 생성 요청 DTO를 조회 응답 DTO로 재사용하지 않는다
- 응답에서만 필요한 필드와 요청에서만 필요한 필드를 분리한다
- “대칭 구조”를 위해 무의미한 필드를 추가하지 않는다

## 6. 매핑 규칙

### 6.1 DTO ↔ domain/application 변환은 명시적으로 한다

Spring의 바인딩/직렬화는 controller boundary까지의 편의를 제공하지만, DTO를 domain/application 모델로 자동 동일시하지는 않는다. 따라서 DTO ↔ 내부 모델 변환은 명시적으로 관리한다. 이 규칙은 Spring의 boundary-oriented programming model에 대한 Project Recommendation 이다.

프로젝트 규칙:

- request DTO → application input 변환은 controller 또는 전용 mapper가 담당한다
- domain/application result → response DTO 변환도 명시적으로 수행한다
- mapper는 transport concern과 domain concern을 구분해서 작성한다

### 6.2 controller가 DTO를 내부 모델처럼 들고 다니지 않는다

프로젝트 규칙:

- use case 메서드 시그니처에 request DTO를 그대로 넘기지 않는다
- application/domain이 response DTO를 직접 생성하지 않는다
- DTO는 presentation 경계 안에서 생성/소비를 끝내는 쪽을 기본값으로 둔다

## 7. 바인딩 보안 규칙

### 7.1 @ModelAttribute 대상은 특히 더 조심한다

Spring 공식 문서는 @ModelAttribute에서 전용 웹 바인딩 객체 또는 constructor binding only를 권장하고, property binding이 필요하면 allowedFields로 제한하라고 권장한다.

프로젝트 규칙:

- @ModelAttribute 대상은 전용 DTO로 둔다
- mutable property binding을 열어야 한다면 바인딩 가능한 필드를 통제한다
- 도메인 객체나 영속 객체를 @ModelAttribute로 받지 않는다

### 7.2 request DTO에 서버 소유 필드를 두지 않는다

프로젝트 규칙:

- userId, role, status, createdAt, updatedAt 같은 서버 소유 값은 request DTO에 기본적으로 두지 않는다
- 필요한 경우에도 클라이언트 입력값과 서버 결정값의 출처를 명확히 분리한다

이 항목은 Practice + Project Recommendation 이다.

## 8. 반환/응답 제어 규칙

### 8.1 ResponseEntity는 전체 응답 제어가 필요할 때 사용한다

Spring 공식 문서 기준으로 ResponseEntity는 status, headers, body를 함께 지정하는 전체 응답 표현이다. 따라서 단순 성공 200 응답에는 항상 필요하지 않다.

프로젝트 규칙:

- 기본 성공 응답은 DTO 또는 ApiResult 반환으로 충분하면 그렇게 한다
- 다음 경우에는 ResponseEntity를 사용한다
- 201 Created + Location
- 204 No Content
- custom header
- 캐시/조건부 응답
- 파일 다운로드/streaming 등 HTTP 제어가 중요한 경우

### 8.2 공통 envelope는 DTO 설계와 충돌하지 않게 한다

Spring의 ResponseBodyAdvice는 응답 body 공통 가공 지점이다. 따라서 ApiResult 같은 공통 응답 envelope를 쓴다면 response DTO 설계와 중복/충돌이 없도록 해야 한다.

프로젝트 규칙:

- response DTO는 business payload를 표현한다
- ApiResult는 공통 wrapper 역할에 집중한다
- response DTO 안에 다시 status/code/message 같은 공통 wrapper 성격 필드를 중복으로 넣지 않는다

## 9. 금지 규칙

다음은 기본 금지다.

- entity를 request DTO로 사용
- entity를 response DTO로 직접 노출
- request/response DTO를 하나로 합치기
- request DTO를 application/domain 메서드 시그니처에 그대로 넘기기
- response DTO 생성을 domain/application 안에서 직접 수행하기
- 임시 Map<String, Object> 응답 남발
- @ModelAttribute 대상에 무분별한 property binding 열기
- DTO에 서버 소유 필드를 무심코 포함시키기

## 10. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 객체는 request용인가, response용인가가 명확한가?
- web transport model과 domain/entity가 분리되어 있는가?
- JSON body라면 @RequestBody 전용 DTO인가?
- form/query binding이라면 @ModelAttribute 전용 DTO인가?
- response DTO가 외부 계약 중심으로 설계되어 있는가?
- DTO가 서버 내부 정책/엔티티 구조를 그대로 노출하지 않는가?
- DTO ↔ 내부 모델 변환 위치가 명확한가?
