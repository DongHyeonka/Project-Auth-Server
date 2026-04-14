# Fallback 기준

## 1. 목적

이 문서는 외부 API / integration 호출 실패 시 fallback을 어떻게 적용할지 정의한다.

이 문서의 목표는 다음과 같다.

- fallback을 retry와 구분한다
- 어떤 외부 의존성을 soft dependency로 바꿀 수 있는지 판단 기준을 만든다
- fallback 결과가 비즈니스 의미를 왜곡하지 않게 한다
- 캐시, 정적 기본값, 제한된 기능, 비동기 전환 같은 대체 전략을 일관되게 사용한다

## 2. 근거 수준

- Official: Spring Cloud CircuitBreaker, Resilience4j, AWS Well-Architected 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 graceful degradation 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 fallback은 retry의 연장이 아니라 별도 결과 전략이다

Spring Cloud CircuitBreaker는 원래 실행 코드와 별도로 fallback function을 받으며, 실패 시 그 fallback이 실행된다고 설명합니다. Resilience4j도 fallback method를 try/catch와 유사한 대체 경로로 설명합니다. 즉 fallback은 “한 번 더 시도”가 아니라 “다른 결과 경로”입니다.

프로젝트 규칙:

- retry는 원래 호출을 다시 시도하는 것
- fallback은 원래 호출을 포기하고 다른 결과를 내는 것
- 두 개념을 한 문서/한 코드 블록에서 섞어 흐리게 만들지 않는다

### 3.2 fallback은 hard dependency를 soft dependency로 바꿀 수 있을 때만 사용한다

AWS Well-Architected는 graceful degradation을 통해 적용 가능한 hard dependency를 soft dependency로 바꾸라고 권장합니다. 의존성이 unhealthy해도 컴포넌트가 degraded mode로 계속 동작할 수 있어야 한다는 뜻입니다.

프로젝트 규칙:

- fallback은 “이 의존성이 없어도 핵심 기능이 여전히 의미 있게 동작하는가?”를 먼저 묻는다
- 핵심 정합성/보안/결제 확정처럼 정답이 아니면 안 되는 기능에는 fallback을 기본 금지한다
- 보조 기능, 부가 정보, 랭킹, 추천, 프로필 부가 데이터, 캐시 가능한 조회는 fallback 후보가 될 수 있다

### 3.3 fallback은 실패를 숨기지 말고 degraded mode를 명시해야 한다

Spring Cloud CircuitBreaker의 fallback은 예외를 받아 대체 결과를 만들 수 있습니다. AWS도 degraded response를 반환하되, 그것이 대체 응답이라는 사실을 이해하고 설계해야 한다고 보는 맥락입니다. 즉 fallback은 “조용히 다른 값을 넣기”가 아니라 품질 저하 상태를 의도적으로 선택하는 것입니다.

프로젝트 규칙:

- fallback 결과는 내부적으로 추적 가능해야 한다
- 운영 로그/메트릭에서 fallback 발생 여부를 구분할 수 있어야 한다
- 호출자가 알아야 하는 degraded semantics를 숨기지 않는다

## 4. 언제 fallback을 허용하는가

### 4.1 허용 가능한 대표 경우

프로젝트 규칙:

다음은 fallback 후보가 될 수 있다.

- 외부 추천 시스템 실패 시 빈 추천 목록 반환
- 외부 프로필 부가 정보 실패 시 핵심 프로필만 반환
- 외부 공개키/JWK 조회 실패 시 짧은 TTL 캐시값 사용
- 외부 feature flag 조회 실패 시 안전한 기본값 사용
- 외부 알림 발송 실패 시 outbox 적재 후 비동기 재시도 전환
- 외부 검색/랭킹 실패 시 기본 정렬 결과 반환

이 방향은 AWS가 말하는 graceful degradation, 즉 정적 응답·사전 결정된 대체 응답으로 hard dependency를 soft dependency로 바꾸는 사고와 맞습니다.

### 4.2 기본적으로 허용하지 않는 경우

프로젝트 규칙:

다음은 기본적으로 fallback을 금지한다.

- 결제 승인/캡처/환불 확정
- 인증/인가의 핵심 판정
- 비밀번호 변경/토큰 발급/보안 민감 작업
- 재고 차감/정산 반영/회원 상태 확정
- 법적/감사적 정합성이 필요한 기록 확정
- “성공처럼 보이면 안 되는” 핵심 command

이 경우는 graceful degradation보다 명시적 실패가 더 안전하다. AWS의 graceful degradation도 모든 dependency를 soft dependency로 바꾸라는 뜻은 아니며, “applicable hard dependencies”에 한정합니다.

## 5. fallback 종류

### 5.1 정적 기본값 fallback

AWS는 predetermined static response를 fallback 예시로 듭니다.

프로젝트 규칙:

- 추천 없음 → 빈 리스트
- 부가 배지 없음 → 빈 값
- 외부 설명문 없음 → 기본 문구

처럼 명백히 안전한 기본값만 허용

핵심 비즈니스 의미를 바꾸는 가짜 성공값은 금지

### 5.2 캐시 기반 fallback

프로젝트 규칙:

- 최근 성공 응답을 짧은 TTL로 캐시해 두고 외부 장애 시 사용 가능
- 단, stale 허용 범위가 문서화돼야 한다
- 캐시 fallback은 조회성 데이터에 우선 적용한다
- 오래된 데이터를 최신 사실처럼 보이게 하면 안 된다

### 5.3 기능 축소(degraded mode) fallback

프로젝트 규칙:

- 외부 부가 서비스가 죽으면 핵심 기능만 제공
- 예:
- “프로필 상세 + 외부 배지” → “프로필 상세만”
- “개인화 추천 + 일반 목록” → “일반 목록만”
- 기능 축소 후에도 결과 의미가 일관돼야 한다

### 5.4 비동기 전환 fallback

프로젝트 규칙:

- 외부 동기 호출 실패 시 즉시 실패 대신 outbox/queue 적재 후 비동기 처리로 전환할 수 있다
- 예:
- 이메일 전송 요청 → “접수됨” 응답 후 비동기 발송
- 단, 이 경우 API 의미가 “즉시 완료”가 아니라 “접수”로 바뀌므로 계약이 명확해야 한다

### 5.5 fallback 없이 명시적 실패

프로젝트 규칙:

- fallback이 어색하거나 의미를 왜곡하면 실패가 정답이다
- “fallback이 없으면 덜 우아해 보인다”는 이유로 억지 fallback을 두지 않는다
- 실패가 더 정직한 경우에는 실패를 택한다

## 6. 설계 규칙

### 6.1 fallback 결과는 원래 결과와 같은 의미를 가장하지 않는다

Spring Cloud CircuitBreaker fallback은 예외를 받아 대체 결과를 리턴할 수 있지만, 그 결과가 원래 외부 호출 성공과 동일한 의미를 가진다고 보장하지는 않습니다.

프로젝트 규칙:

- fallback 응답을 “정상 외부 응답”처럼 위장하지 않는다
- 내부 result 모델에서 degraded 여부를 표현할 수 있으면 표현한다
- API 바깥으로 드러나야 하는 경우에는 metadata/flag로 구분 가능하게 한다

### 6.2 fallback은 provider-specific 예외보다 내부 의미로 판단한다

프로젝트 규칙:

fallback 조건은 SocketTimeoutException, WebClientResponseException 같은 저수준 타입 그 자체보다

- ExternalProfileTemporaryFailure
- RecommendationProviderUnavailable

같은 내부 번역 예외 기준으로 두는 편을 선호한다

adapter가 provider 예외를 먼저 번역하고, 상위 integration service가 fallback 여부를 판단할 수 있다

### 6.3 fallback은 조용한 데이터 오염을 만들면 안 된다

프로젝트 규칙:

- 캐시 fallback은 stale 가능성을 고려한다
- 기본값 fallback은 진짜 부재와 fallback 결과를 혼동시키지 않는다
- 외부 검증 실패를 내부 성공으로 바꾸는 fallback을 금지한다

## 7. 위치 규칙

### 7.1 fallback은 integration adapter 바로 위 또는 integration service 경계에 둔다

Spring Cloud CircuitBreaker fallback은 wrapped supplier를 대체하는 함수로 붙습니다. 실무적으로도 fallback은 외부 호출 의미를 가장 잘 아는 integration 경계에 두는 것이 맞습니다.

프로젝트 규칙:

- fallback은 external client adapter 바로 위의 integration service에서 우선 검토
- controller/application/domain에 provider-aware fallback 로직을 두지 않는다
- domain이 fallback 존재를 알아야 하는 구조를 기본 금지한다

### 7.2 controller에서 fallback 결과를 직접 조립하지 않는다

프로젝트 규칙:

- controller는 fallback 여부를 판단하는 위치가 아니다
- 외부 의존성 실패와 대체 전략은 integration 경계에서 끝낸다
- controller는 최종 내부 result만 받아 응답으로 번역한다

## 8. Circuit Breaker와의 관계

### 8.1 fallback은 circuit breaker와 함께 쓰일 수 있다

Spring Cloud CircuitBreaker는 fallback function을 공식 지원하고, OpenFeign + CircuitBreaker 문서도 fallback class를 둘 수 있다고 설명합니다.

프로젝트 규칙:

- 회로 차단기와 fallback을 함께 사용하는 것은 허용
- 단, circuit open 상태라고 항상 fallback이 정답인 것은 아니다
- “실패를 빠르게 차단”과 “대체 결과 제공”은 별도 결정으로 본다

### 8.2 circuit open fallback과 단일 호출 실패 fallback을 구분한다

프로젝트 규칙:

- 일시적 단일 실패에서의 fallback
- circuit open 상태에서의 fallback
- 은 운영 의미가 다를 수 있다
- observability에서는 이 둘을 구분할 수 있어야 한다

## 9. cache/staleness 규칙

### 9.1 캐시 fallback은 staleness budget이 있어야 한다

프로젝트 규칙:

- 캐시 fallback은 “얼마나 오래된 값을 허용할지”가 먼저 정해져야 한다
- 무기한 stale fallback 금지
- provider 데이터 성격에 따라 허용 TTL을 문서화한다

### 9.2 stale 데이터는 최신 사실처럼 취급하지 않는다

프로젝트 규칙:

- 외부 프로필, 환율, 추천, 재고성 정보는 stale일 수 있다
- stale 허용이 어려운 정보에는 캐시 fallback을 두지 않는다
- stale 사용 사실이 내부적으로 추적 가능해야 한다

## 10. observability 규칙

### 10.1 fallback 발생은 반드시 관측 가능해야 한다

AWS의 graceful degradation은 장애 시 soft dependency로 동작을 바꾸는 것이므로, 운영자는 fallback이 언제 얼마나 발생하는지 알아야 합니다. Spring Cloud CircuitBreaker fallback도 Throwable을 인자로 받아 원인과 함께 처리할 수 있습니다.

프로젝트 규칙:

fallback 발생 로그/메트릭을 남긴다

최소한 다음을 구분 가능해야 한다

- provider
- operation
- fallback type(정적/캐시/비동기 전환 등)
- 원인 예외
- 최종 결과(success degraded / fail)

### 10.2 fallback 후 성공은 ERROR로 기록하지 않는다

프로젝트 규칙:

- fallback이 적용돼 요청이 의미 있게 처리됐다면 기본 WARN 또는 INFO
- 최종 실패만 대표 ERROR
- fallback 성공을 장애처럼 과장하지 않는다
- 다만 fallback 비율이 높아지면 경고 신호로 본다

### 10.3 fallback 비율은 SLO/품질 지표로 본다

프로젝트 규칙:

- 성공률만 보지 않고 fallback rate도 본다
- “서비스는 성공했지만 품질은 저하된 상태”를 따로 추적한다
- fallback이 많으면 upstream 문제 또는 timeout/retry 설정 문제를 의심한다

## 11. 예외와 응답 규칙

### 11.1 fallback 결과가 있으면 예외를 그대로 밖으로 던지지 않는다

Resilience4j fallback도 실패를 대체 결과로 바꾸는 구조입니다.

프로젝트 규칙:

- fallback이 최종 결과를 제공하면 외부 예외를 그대로 상위 계층에 올리지 않는다
- 대신 degraded result를 반환한다
- 원인 예외는 observability에 남긴다

### 11.2 fallback이 불가능하면 실패를 번역해 올린다

프로젝트 규칙:

- fallback이 적용되지 않거나 의미가 없으면 integration exception으로 번역해 올린다
- “fallback도 실패했는데 기본값으로 성공처럼 처리”를 금지한다

## 12. 다른 문서와의 경계

이 문서는 fallback만 다룬다.
아래 주제의 source of truth는 별도 문서다.

- timeout
- retry
- outbound idempotency
- serialization/deserialization
- exception translation

이 문서는 위 내용을 반복하지 않고, 외부 의존성 실패 시 어떤 대체 결과를 허용할지만 정의한다.

## 13. 금지 규칙

다음은 기본 금지다.

- 핵심 정합성/보안/결제 확정에 억지 fallback 적용
- fallback 결과를 원래 정상 결과처럼 위장
- stale 데이터 무기한 사용
- controller에서 provider-aware fallback 구현
- fallback 발생을 관측하지 않음
- fallback으로 실패를 전부 숨김
- provider 저수준 예외 타입에 강하게 결합된 fallback 분기
- 캐시/정적 기본값이 비즈니스 의미를 왜곡하는데도 사용

## 14. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 외부 의존성은 soft dependency로 바꿔도 되는가?
- fallback 결과가 비즈니스 의미를 왜곡하지 않는가?
- fallback 종류(정적/캐시/기능 축소/비동기 전환)가 명확한가?
- stale 허용 범위가 문서화돼 있는가?
- fallback 발생이 로그/메트릭에서 관측 가능한가?
- fallback 성공을 실패처럼 ERROR로 과장하지 않는가?
- controller/application/domain이 아니라 integration 경계에 fallback이 있는가?
