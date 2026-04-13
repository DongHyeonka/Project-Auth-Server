# Retry 기준

## 1. 목적

이 문서는 외부 API / integration 호출에서 retry를 어떻게 적용할지 정의한다.

이 문서의 목표는 다음과 같다.

- retry 대상을 일시적 실패로 제한한다
- retry가 장애를 증폭시키지 않게 한다
- retry, timeout, idempotency, fallback의 책임을 구분한다
- 외부 HTTP 호출 retry를 adapter 경계 안에서 일관되게 처리한다

## 2. 근거 수준

- Official: Spring Framework / Spring Retry / AWS / Google Cloud 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 retry는 기본값이 아니라 제한적 도구다

Spring은 retry 기능을 제공하지만, Spring Framework resilience 문서 기준 기본 retry는 모든 예외를 대상으로 최대 3회 재시도하며 1초 간격을 둡니다. 즉, 프레임워크 기본값만 믿으면 너무 넓게 재시도할 수 있습니다. AWS도 retry는 유용하지만, 부하가 높은 상황에서는 서버에 더 많은 요청을 보내 상황을 악화시킬 수 있다고 설명합니다.

프로젝트 규칙:

- 외부 API retry는 명시적으로 허용한 경우에만 사용한다
- 기본 retry 정책에 의존하지 않는다
- retry를 “일단 켜 두는 안정화 옵션”으로 쓰지 않는다

### 3.2 retry는 timeout, idempotency, fallback과 분리해서 설계한다

AWS는 timeout, retry, backoff를 별도 도구로 설명하고, side effect가 있는 API는 retry 전에 idempotency 가 중요하다고 말합니다. Google Cloud도 non-idempotent operation retry를 anti-pattern으로 경고합니다.

프로젝트 규칙:

- timeout은 “얼마나 기다릴지”
- retry는 “다시 시도할지”
- idempotency는 “다시 시도해도 안전한지”
- fallback은 “재시도 후에도 실패하면 대체 경로가 있는지”
- 를 각각 따로 판단한다

### 3.3 retry는 외부 연동 경계(adapter)에 둔다

Spring이 RestClient, WebClient, HTTP Service Client 같은 외부 호출 도구를 제공하는 만큼, retry도 외부 연동 경계에서 결정하는 것이 자연스럽습니다. application/domain이 provider-specific HTTP 오류나 retry 대상 예외를 직접 다루는 구조는 피하는 것이 좋습니다.

프로젝트 규칙:

- 외부 HTTP retry는 integration adapter/client 경계에서 처리한다
- application/domain이 SocketTimeoutException, WebClientResponseException, ConnectException 분류를 직접 하지 않는다
- retry 이후 최종 실패만 내부 예외로 번역한다

## 4. 언제 retry하는가

### 4.1 retry 대상은 “일시적 실패”다

AWS는 retry가 부분 실패, 일시적 네트워크 문제, 순간적인 과부하 같은 상황에 유용하다고 설명합니다. Google Cloud도 retry는 response criteria와 idempotency criteria를 동시에 만족 하는 요청에만 적용하라고 안내합니다.

프로젝트 규칙:

다음은 retry 후보가 될 수 있다.

- connection timeout / connection reset
- read timeout / response timeout
- 일시적 5xx
- 일시적 429
- 네트워크 단절/짧은 DNS/TLS 실패
- provider가 transient failure로 명시한 에러

### 4.2 retry하지 않는 대상

Google Cloud는 retrying unretryable errors, non-idempotent operation retry를 anti-pattern으로 명시합니다. Spring 기본 retry는 모든 예외를 재시도할 수 있으므로, 이 프로젝트에서는 반드시 대상을 좁혀야 합니다.

프로젝트 규칙:

다음은 기본적으로 retry하지 않는다.

- 4xx business 오류
- 인증 실패(401/403)
- 잘못된 요청 형식(400)
- provider 계약 위반
- validation/parsing 오류
- deterministic failure
- side effect가 있는데 idempotency가 보장되지 않는 호출

### 4.3 404/409 같은 상태는 provider 계약에 따라 해석한다

Spring의 HTTP client는 status를 읽을 수 있지만, 어떤 status를 transient로 볼지는 provider 계약이 결정합니다. 일부 409/404는 정상적인 부재/중복 의미일 수 있고, 일부 429/503은 transient일 수 있습니다.

프로젝트 규칙:

- retry 가능 여부는 HTTP status 숫자만 으로 결정하지 않는다
- provider 계약 문서와 실제 운영 의미를 함께 본다
- 같은 provider 안에서는 status 해석 기준을 문서화한다

## 5. retry 전제조건

### 5.1 retry 전에는 idempotency 가능성을 확인한다

AWS는 side effect가 있는 API는 timeout/partial failure 이후 재시도 시 중복 side effect가 생길 수 있으므로 idempotent API 설계 가 중요하다고 설명합니다. Google Cloud도 non-idempotent operation retry를 anti-pattern으로 지적합니다.

프로젝트 규칙:

- side effect 없는 read 호출은 retry 허용을 더 쉽게 검토한다
- side effect 있는 write 호출은
- provider idempotency support
- 우리 쪽 idempotency key
- 중복 실행 허용 여부
- 를 먼저 확인한다
- 이 검토 없이 자동 retry를 켜지 않는다

### 5.2 retry는 timeout이 먼저 있어야 의미가 있다

timeout이 없으면 호출이 오래 붙잡힌 채 retry까지 가지 못하고, AWS도 timeout을 원격 호출의 기본 안전장치로 설명합니다. retry는 timeout과 함께 설계되어야 합니다.

프로젝트 규칙:

- retry가 있는 외부 API 호출에는 timeout이 먼저 정의되어 있어야 한다
- timeout 없이 retry만 정의하는 것을 금지한다
- retry 문서는 timeout 문서와 함께 읽는 것을 전제로 한다

## 6. backoff 규칙

### 6.1 즉시 반복 retry를 금지한다

Google Cloud는 retry without backoff 를 대표 anti-pattern으로 지적합니다. AWS도 retry는 backoff와 함께 써야 하고, 그렇지 않으면 부하를 폭증시킬 수 있다고 설명합니다.

프로젝트 규칙:

- 자동 retry는 backoff 없이 즉시 연속 재시도하지 않는다
- 최소한 exponential backoff를 기본으로 한다
- “짧게 여러 번 때리면 되겠지”를 금지한다

### 6.2 jitter를 기본으로 한다

AWS는 retries and backoff with jitter를 공식적으로 설명하고, 동시 재시도로 인한 thundering herd를 줄이기 위해 jitter가 중요하다고 강조합니다. Google Cloud도 exponential backoff with jitter를 일반적으로 권장합니다.

프로젝트 규칙:

- backoff에는 jitter를 기본 포함한다
- 같은 장애 시점에 모든 인스턴스가 같은 간격으로 동시에 재시도하지 않게 한다
- jitter 없는 fixed backoff를 기본값으로 두지 않는다

### 6.3 backoff는 무한히 커지지 않게 상한을 둔다

AWS는 exponential backoff를 설명하면서도 상한을 두고, 전체 retry budget 안에서 움직이게 설계해야 한다고 말합니다.

프로젝트 규칙:

- initial interval
- multiplier
- max interval
- max attempts
- 를 모두 명시한다
- 무한 증가형 backoff를 금지한다

## 7. retry 횟수 규칙

### 7.1 짧고 보수적인 max attempts를 기본으로 한다

Spring Framework 기본 retry는 최대 3회 재시도입니다. Google Cloud는 unnecessarily layering retries를 anti-pattern으로 지적합니다. 이 프로젝트도 외부 HTTP 호출은 짧고 보수적인 횟수를 기본으로 둡니다.

프로젝트 규칙:

- 외부 API 자동 retry 기본값은 짧게
- 일반 권장 시작점:
- 총 시도 2~3회 수준
- “10번까지 해보자” 같은 공격적 retry를 기본 금지한다

### 7.2 상위/하위 레이어 retry 중복을 금지한다

Google Cloud는 unnecessarily layering retries 를 anti-pattern으로 지적합니다. client library, gateway, adapter, application service가 모두 retry하면 실제 요청 수가 폭증할 수 있습니다.

프로젝트 규칙:

- 한 호출 경로에서 대표 retry layer를 정한다
- provider SDK가 이미 retry를 하면 우리 adapter retry를 다시 올리지 않는다
- gateway / SDK / client / application retry가 겹치지 않게 한다

## 8. 기술 선택 규칙

### 8.1 선언적 retry와 프로그래밍식 retry를 구분한다

Spring은 @Retryable 애노테이션과 RetryPolicy/RetryTemplate 계열을 제공하고, Spring Framework 7 resilience 기능도 method-level retry를 지원합니다. @Retryable은 간단하지만 기본적으로 프록시 기반이고, 대상/횟수/backoff를 명시적으로 설정해야 합니다.

프로젝트 규칙:

- 단순한 adapter method 재시도에는 선언적 retry 허용
- 복잡한 분기, 일부 코드 블록만 재시도, 동적 정책은 프로그래밍식 retry 우선
- 어떤 방식을 쓰든 retry 대상 예외와 backoff를 명시한다

### 8.2 새 코드의 기본 선택은 provider/client 구조에 맞춰 명시적으로 한다

Spring 자체는 retry 기능을 제공하지만, Boot의 외부 client 구조와 결합할 때는 “어디에 적용할지”가 더 중요합니다. integration adapter 단위의 retry가 가장 기본이며, Spring Cloud CircuitBreaker/Resilience4j 같은 도구가 있다면 공통 정책화도 가능합니다.

프로젝트 규칙:

- retry는 external client adapter 또는 그 바로 위 integration service에 둔다
- controller/application/domain에 retry 애노테이션을 붙이지 않는다
- 공통 라이브러리를 쓰더라도 source of truth는 프로젝트 문서다

## 9. observability 규칙

### 9.1 retry는 관측 가능해야 한다

AWS는 retry가 장애를 완화할 수도 있지만 반대로 악화시킬 수도 있으므로, retry 동작을 이해할 수 있어야 한다고 설명합니다. Spring/Boot의 HTTP client instrumentation과 함께 retry attempt, provider, operation, 최종 결과를 추적 가능하게 해야 합니다.

프로젝트 규칙:

retry 로그/메트릭에는 최소한 다음을 남긴다

- provider/client name
- operation
- attempt number
- final outcome
- error type
- retry가 있었는지, 몇 번 있었는지, 결국 성공/실패했는지 구분 가능해야 한다

### 9.2 최종 실패만 대표 ERROR로 남긴다

AWS/Google Cloud가 경고하는 retry storm와 중복 부하 문제를 고려하면, 중간 실패를 모두 ERROR로 남기면 운영 신호가 오염됩니다. 최종 실패만 대표 ERROR, 중간 실패는 WARN 또는 DEBUG가 기본입니다.

프로젝트 규칙:

- 중간 retry 실패: WARN 또는 DEBUG
- retry 후 성공: WARN 또는 INFO
- retry 후 최종 실패: 대표 ERROR

## 10. provider 계약과의 관계

### 10.1 Retry-After 같은 provider 신호를 존중한다

HTTP/공급자 문서가 retry 간격이나 throttling 신호를 주는 경우, 그 신호를 우선 고려하는 것이 일반적 운영 원칙입니다. Google Cloud도 response criteria를 보고 retry해야 한다고 설명합니다.

프로젝트 규칙:

- provider가 Retry-After 또는 throttling 가이드를 주면 우선 따른다
- 로컬 backoff 정책이 provider 신호와 충돌하지 않게 한다
- provider rate limit 계약을 무시한 retry를 금지한다

### 10.2 provider별 retryable 오류 목록을 문서화한다

프로젝트 규칙:

각 주요 외부 시스템별로

- retryable status
- retryable exception
- non-retryable business error

를 문서화한다

코드 안 산발적인 if status == 503 식 분기를 줄인다

## 11. 이 문서와 다른 문서의 경계

이 문서는 외부 API retry만 다룬다. 아래 주제의 source of truth는 별도 문서다.

- timeout
- idempotency
- fallback
- exception translation
- serialization/deserialization

이 문서는 위 내용을 반복하지 않고, 외부 연동 retry에서 어디까지 함께 고려해야 하는지만 정의한다.

## 12. 금지 규칙

다음은 기본 금지다.

- 모든 예외 retry
- backoff 없는 즉시 재시도
- jitter 없는 고정 간격 재시도 기본값
- side effect API를 idempotency 검토 없이 자동 retry
- SDK + gateway + adapter + application 중복 retry
- retry 대상 아닌 4xx/business error retry
- retry가 있는데 timeout이 없음
- retry 동작이 관측되지 않음

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 실패는 정말 transient인가?
- 이 호출은 retry해도 안전한가, 특히 idempotent한가?
- timeout이 먼저 정의되어 있는가?
- retry 대상 예외/status가 명시되어 있는가?
- backoff와 jitter가 있는가?
- max attempts가 짧고 보수적인가?
- 상위/하위 레이어 retry 중복이 없는가?
- retry attempt와 최종 결과가 관측 가능한가?
