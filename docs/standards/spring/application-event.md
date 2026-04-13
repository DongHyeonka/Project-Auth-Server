# ApplicationEvent 사용 기준

## 목적

Application Event는 같은 애플리케이션 내부에서 **느슨하게 결합된 후속 반응**을 분리하기 위해 사용한다.  
핵심 비즈니스 오케스트레이션을 숨기는 수단으로 사용하지 않는다.

## 공식 의미

- `ApplicationEventPublisher`는 이벤트 발행 기능을 제공한다.
- `publishEvent(Object)`는 일반 객체도 이벤트로 발행할 수 있으며, 필요 시 `PayloadApplicationEvent`로 감싸진다.
- 이벤트 발행은 multicaster로의 hand-off일 뿐, 그 자체로 synchronous/asynchronous 또는 immediate execution을 보장하지 않는다.
- 리스너는 가능한 한 효율적이어야 하며, 오래 걸리거나 blocking 가능한 작업은 개별적으로 비동기 실행을 고려한다.
- 트랜잭션 결과와 묶어 처리해야 하면 `@TransactionalEventListener`를 사용한다.
- `@TransactionalEventListener`의 기본 phase는 `AFTER_COMMIT`이다.
- 트랜잭션 밖에서 발행된 이벤트는 기본적으로 discard되며, `fallbackExecution = true`일 때만 예외적으로 처리된다.

## 기본 규칙

### 1. Application Event는 “후속 반응”에만 사용
다음은 이벤트 후보가 된다.

- 감사 로그 기록
- 메트릭/알림 발행
- 후속 캐시 정리
- 읽기 모델 갱신
- 부가적인 notification
- core use case 이후의 느슨한 반응

다음은 이벤트로 풀지 않는다.

- 핵심 비즈니스 흐름 자체
- 반드시 순서대로 수행되어야 하는 오케스트레이션
- 즉시 실패/성공 여부가 핵심인 주 경로
- 도메인 규칙 판정
- controller/service가 직접 보여줘야 하는 결과 계산

### 2. 이벤트는 같은 애플리케이션 내부 경계로 본다
기본적으로 Spring Application Event는 in-process 이벤트다.

즉:
- 다른 시스템과의 통합 이벤트 브로커 대체제가 아니다
- Kafka/RabbitMQ 같은 외부 메시징과 같은 의미로 쓰지 않는다
- 프로세스 내부의 느슨한 반응 분리에 한정한다

### 3. 발행은 “hand-off”일 뿐, 실행 모델을 가정하지 않는다
`publishEvent(...)`를 호출했다고 해서 아래를 가정하지 않는다.

- 반드시 동기적으로 끝난다
- 반드시 즉시 실행된다
- 반드시 같은 스레드에서 다 처리된다

즉 발행자(publisher)는 listener의 실행 방식에 의존하지 않는다.

### 4. listener는 짧고 효율적으로 유지
공식 문서 취지대로 listener는 가능한 한 짧고 효율적으로 유지한다.

기본 금지:
- 긴 블로킹 작업
- 대규모 외부 API 호출
- 무거운 batch 처리
- 여러 단계 오케스트레이션

정말 오래 걸리면 별도 비동기/후속 처리 구조를 검토한다.

### 5. 트랜잭션 결과가 중요하면 `@TransactionalEventListener`
다음은 `@TransactionalEventListener`를 우선 검토한다.

- DB commit 성공 후에만 실행되어야 하는 후속 처리
- rollback되면 수행하면 안 되는 반응
- 저장 완료 이후에만 의미가 있는 알림/감사/후속 처리

기본 phase:
- 특별한 이유가 없으면 `AFTER_COMMIT`

### 6. `fallbackExecution = true`는 예외적으로만
트랜잭션이 없을 때도 listener를 실행해야 하는 경우가 정말 명확할 때만 사용한다.

기본값:
- 트랜잭션 경계가 없는 발행은 discard되어도 괜찮다고 본다

### 7. 이벤트 payload는 처리에 필요한 상태를 포함
공식 문서상 reactive/async hand-off에서는 thread-local 상태를 기대하면 안 된다.  
따라서 이벤트 객체에는 listener가 처리하는 데 필요한 최소 상태를 자체적으로 담는다.

금지:
- listener가 `SecurityContext`, MDC, thread-local만 믿고 동작
- payload 없이 “가서 다시 다 조회해라” 식으로 과도하게 빈약한 이벤트

기본:
- 식별자
- 필요한 시점 정보
- 필요한 타입/상태
를 명시적으로 포함

### 8. payload는 작고 안정적으로
이벤트는 무거운 객체 그래프 전체보다, listener가 필요한 최소 데이터만 담는다.

기본:
- entity 전체보다 id/필수 상태 우선
- JPA lazy proxy를 payload로 넘기지 않음
- 직렬화/로그에 취약한 대형 객체를 그대로 넘기지 않음

### 9. 이벤트 이름은 business fact 또는 completed action으로 짓는다
좋은 방향:
- `UserRegisteredEvent`
- `LoginSucceededEvent`
- `PublicKeyRotatedEvent`

지양:
- `DoSomethingEvent`
- `CommonEvent`
- `UserProcessEvent`

이름만 보고 무슨 일이 일어났는지 보여야 한다.

### 10. 발행자는 listener 존재를 몰라야 한다
publisher는 listener가 몇 개인지, 누가 듣는지, 어떤 순서인지에 기대지 않는다.

금지:
- “이 이벤트를 쏘면 저 listener가 반드시 먼저 실행된다”는 설계
- 이벤트 발행으로 핵심 결과를 암묵적으로 완성하는 구조

### 11. listener 순서 의존 최소화
`@Order`를 줄 수는 있지만, 가능하면 listener 간 순서 의존을 설계하지 않는다.

정말 필요할 때만:
- 같은 phase 안에서 우선순위 조정
- 매우 명확한 부가 처리 순서

기본은 서로 독립적으로 동작해야 한다.

### 12. listener 안에서 핵심 business decision 금지
listener는 후속 반응을 수행해야 한다.

금지:
- 핵심 상태 전이 결정
- 메인 use case 성공/실패를 뒤집는 판단
- 여러 하위 흐름을 연결한 복잡한 오케스트레이션

### 13. listener 예외는 의도를 분명히
listener에서 예외가 나면 어떤 영향을 기대하는지 명확해야 한다.

기본:
- 주 흐름과 강결합이면 이벤트보다 명시적 호출이 더 적합
- 후속 반응이면 실패 처리/재시도/로그 정책을 분리해서 설계
- 예외를 조용히 삼키지 않는다

### 14. 이벤트는 남발하지 않는다
“느슨하게 연결하고 싶다”는 이유만으로 이벤트를 남발하지 않는다.

다음 질문 중 여러 개가 “예”일 때만 검토한다.
- 발행자와 반응자를 분리할 가치가 큰가?
- 반응자가 하나가 아닐 수 있는가?
- 후속 반응이 핵심 흐름이 아닌가?
- 트랜잭션 완료 후 처리로 분리하는 이점이 큰가?

### 15. 테스트에서 이벤트를 검증할 수 있어야 한다
Spring 테스트는 `ApplicationEvents`를 기록하고 검증할 수 있다.

기본:
- 이벤트를 발행하는 use case는 발행 여부를 테스트 가능하게 설계
- listener 동작도 별도 테스트 가능하게 유지
- “이벤트가 어딘가에서 되겠지”를 금지

## 프로젝트 기준 요약

- Application Event는 내부 후속 반응 분리 수단
- 핵심 오케스트레이션에는 기본 금지
- 발행은 hand-off일 뿐 실행 모델을 가정하지 않음
- listener는 짧고 효율적으로
- commit 결과가 중요하면 `@TransactionalEventListener`
- payload는 작고 필요한 상태를 명시적으로 포함
- publisher는 listener 순서/존재를 몰라야 함
- 이벤트 남발 금지
