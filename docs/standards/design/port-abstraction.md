# port abstraction 기준

## 목적

포트는 “인터페이스를 많이 만들기 위한 수단”이 아니라,  
애플리케이션 코어가 외부 기술과 직접 결합되지 않도록 **경계를 고정하는 계약**이다.

## 공식 의미

- 포트는 애플리케이션과 외부 세계 사이의 purposeful conversation을 식별하는 계약이다.
- 어댑터는 기술별 입력/출력을 포트 계약에 맞게 번역한다.
- 헥사고날 아키텍처에서는 코어가 외부 컴포넌트에 의존하지 않고, 외부 컴포넌트가 코어가 정의한 포트에 맞춘다.
- 포트는 inbound(입력) / outbound(출력) 성격으로 나눠 볼 수 있다.

## 기본 규칙

### 1. 포트는 “경계”에만 만든다
다음 중 하나가 아니면 포트를 만들지 않는다.

- application use case 진입 계약
- persistence / external API / message broker / file system 같은 외부 의존 경계
- 교체 가능한 보안/토큰/암호화/알림 정책 경계
- 테스트에서 fake/stub로 바꿔 끼울 가치가 큰 경계

같은 모듈 내부 helper 호출에는 포트를 만들지 않는다.

### 2. 코어가 소유하는 포트만 허용
포트는 코어(application/domain)가 필요로 하는 계약이어야 한다.

기본:
- inbound port: 코어가 외부 호출자에게 제공하는 use case 계약
- outbound port: 코어가 외부 시스템에 요구하는 계약

금지:
- infrastructure 기술 구조에 맞춘 인터페이스를 코어에 올리기
- adapter 편의를 위해 포트 모양을 결정하기

### 3. 포트는 비즈니스 의미로 말하고, 기술 세부를 숨긴다
포트 메서드는 “무엇을 원하는가”를 표현해야 한다.

좋은 방향:
- `findUserByEmail`
- `saveUser`
- `signToken`
- `publishUserCreated`

지양:
- `callHttp`
- `executeQuery`
- `postJson`
- `sendKafkaRecord`

기술 세부는 adapter 구현으로 내린다.

### 4. 포트 시그니처에 기술 타입을 노출하지 않는다
포트 계약에는 가능하면 아래 타입을 직접 노출하지 않는다.

- `ResponseEntity`
- `HttpClient`, `WebClient`
- `ResultSet`
- `JpaRepository`
- `JsonNode`
- 프레임워크 request/response 타입

포트는 domain/application에 더 가까운 command/result/value type으로 표현한다.

### 5. 포트는 작고 응집도 있게 유지
포트 하나는 하나의 역할/대화에 집중해야 한다.

금지:
- unrelated use case를 한 inbound port에 몰아넣기
- 여러 외부 시스템 책임을 한 outbound port에 섞기
- 너무 범용적인 `CommonPort`, `IntegrationPort`

### 6. inbound port는 use case 단위로 생각
입력 포트는 보통 “사용자가 시스템에 요구하는 행위” 단위로 설계한다.

예:
- 회원 가입
- 로그인
- 토큰 발급
- 공개키 조회

즉 controller나 scheduler가 직접 서비스 구현을 알기보다, use case 계약을 호출하는 방향을 우선 검토한다.

### 7. outbound port는 코어가 정말 필요한 능력만 노출
출력 포트는 adapter가 할 수 있는 모든 기능이 아니라, 코어가 실제로 필요한 능력만 드러낸다.

예:
- `UserReader` / `UserAppender`
- `VaultSigner`
- `TokenPublisher`

금지:
- adapter의 내부 옵션/기술 선택지를 포트에 그대로 노출
- “혹시 나중에 필요할지도” 모드를 미리 넣기

### 8. 포트 이름은 역할 중심으로 짓는다
좋은 방향:
- `UserReader`
- `UserSaver`
- `TokenSigner`
- `AuthLoginUseCase`
- `PublicKeyQuery`

지양:
- `UserPort`
- `CommonPort`
- `InfraPort`
- `ExternalApiPort`

이름만 보고 어떤 대화를 하는지 보여야 한다.

### 9. 포트 반환값은 없음/실패 의미를 명확히 표현
- 단건 조회 없음 -> `Optional<T>` 검토
- 다건 조회 없음 -> empty collection
- 실패는 예외 또는 명시적 result type으로 표현
- null 반환 금지

### 10. 포트는 transaction/transport를 직접 소유하지 않는다
포트 자체는 transaction, HTTP, serialization 정책을 직접 설명하지 않는다.

기본:
- transaction boundary는 application service/use case 쪽에서 결정
- transport 형식은 controller/adapter에서 결정
- serialization은 adapter에서 처리

### 11. adapter는 포트를 “구현”하거나 “호출”하면서 번역 책임을 진다
- inbound adapter: HTTP, scheduler, message consumer, CLI 등에서 입력을 받아 포트 호출
- outbound adapter: DB, external API, queue, cache 등에 맞게 포트를 구현

adapter는 기술 번역을 담당하지만 business meaning을 새로 만들지 않는다.

### 12. 테스트 seam이 실제 가치가 있을 때 포트를 둔다
포트는 테스트를 쉽게 만들 수 있지만, 테스트 때문에 모든 내부 호출을 포트로 만들지는 않는다.

기본:
- 외부 경계 seam은 포트 우선
- 내부 구현 detail seam은 concrete class 유지 가능

### 13. 포트와 adapter는 1:1일 필요가 없다
하나의 포트에 여러 adapter가 붙을 수 있다.
예:
- mock repository / real repository
- REST adapter / batch adapter / test harness

즉 포트는 기술 구현 수가 아니라 “대화 계약” 기준으로 잡는다.

### 14. 포트는 framework proxy/AOP 이유만으로 만들지 않는다
Spring이 인터페이스 기반 프록시를 잘 지원하더라도, 프록시 가능성만으로 포트를 만들지 않는다.
먼저 경계/계약 의미가 있는지 확인한다.

## 프로젝트 기준 요약

- 포트는 경계에만 만든다
- 포트는 코어가 소유한다
- 포트는 비즈니스 의미로 말하고 기술 세부를 숨긴다
- inbound는 use case 중심
- outbound는 코어가 필요한 능력만
- 포트는 작고 응집도 있게
- adapter가 기술 번역을 담당
- 모든 내부 호출을 포트로 만들지 않는다
