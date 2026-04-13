# Async / Scheduler / Retry 기준

## 1. 목적

이 문서는 Spring의 비동기 실행(@Async), 스케줄 실행(@Scheduled), 재시도(@Retryable / RetryTemplate)를 프로젝트에서 언제, 어디에, 어떤 방식으로 사용할지 정의한다.

이 문서의 목적은 다음과 같다.

- 실행 경계를 명확히 한다.
- 프록시 기반 동작의 함정을 피한다.
- 스레드 풀/스케줄러를 암묵적 기본값에 맡기지 않는다.
- 재시도를 “일시적 실패”에만 제한한다.
- 핵심 비즈니스 로직이 비동기/스케줄/재시도 애노테이션 뒤에 숨지 않게 한다.

## 2. 근거 수준

- Official: Spring Framework / Spring Boot / Spring Retry 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 제약 위에 일반적인 실무 운영 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 동기 실행을 기본값으로 둔다

비동기, 스케줄, 재시도는 기본 선택지가 아니라 명시적 필요가 있을 때만 도입한다.

- @Async는 호출자가 즉시 반환되어도 되는 후속 작업에만 사용한다.
- @Scheduled는 요청-응답 흐름이 아닌 주기성/지연 실행 작업에만 사용한다.
- retry는 외부 시스템 호출 등 일시적 실패 가능성이 있는 작업에만 사용한다.
- 검증 오류, 도메인 규칙 위반, 매핑 오류, 프로그래밍 오류에 retry를 걸지 않는다.

Spring Retry 공식 문서도 “항상 같은 예외가 발생하는 결정적 실패는 재시도해도 도움이 되지 않으며, 모든 예외 타입에 대해 재시도하지 말라”고 설명한다.

### 3.2 애노테이션은 도메인 규칙이 아니라 실행 메커니즘이다

@Async, @Scheduled, @Retryable은 모두 실행 방식에 대한 인프라 성격의 도구다. 따라서 이 프로젝트에서는 다음을 기본으로 한다.

- domain 레이어에는 사용하지 않는다.
- 주 사용 위치는 application / infrastructure 레이어로 제한한다.
- controller, entity, value object, mapper에 실행 메커니즘 애노테이션을 붙여 책임을 섞지 않는다.

이 규칙은 프로젝트 아키텍처 정렬을 위한 Project Recommendation이다.

## 4. @Async 표준

### 4.1 사용 기준

@Async는 호출 즉시 반환해도 되는 작업에만 사용한다.

허용 예:

- 알림 전송
- 감사 로그 전송
- 비핵심 후속 연산
- 핵심 트랜잭션 완료 뒤의 독립 작업

비허용 예:

- 핵심 비즈니스 결과를 결정하는 로직
- 호출자가 반드시 성공/실패를 알아야 하는 로직
- 트랜잭션 경계를 우회하려는 용도
- 초기화 콜백(@PostConstruct)에 직접 붙이는 방식

Spring 공식 문서상 @Async는 호출 시 작업을 TaskExecutor에 제출해 비동기로 실행하며, @PostConstruct 같은 lifecycle callback과 함께 사용할 수 없다.

### 4.2 프록시 경계 규칙

@Async는 기본적으로 proxy mode로 처리되므로 같은 클래스 내부 호출(self-invocation) 에는 적용되지 않는다. 따라서:

- this.someAsyncMethod() 형태를 금지한다.
- @Async가 필요한 로직은 별도 bean 으로 분리한다.
- 프록시 경계가 드러나게 설계한다.

Spring 공식 문서도 @Async의 기본 advice mode는 proxy이며 같은 클래스 내부 로컬 호출은 가로채지 못한다고 명시한다.

### 4.3 메서드 시그니처 규칙

Spring 공식 기준에서 @Async 메서드는 파라미터는 자유롭지만 반환형은 void 또는 Future 계열이어야 하며, CompletableFuture 사용이 가능하다. 또한 @Configuration 클래스 내부 메서드에는 지원되지 않는다.

프로젝트 규칙은 다음과 같다.

- 호출자가 결과를 관찰해야 하면 CompletableFuture<T>를 사용한다.
- 단순 fire-and-forget이면 void를 사용할 수 있다.
- 새 코드에서 구식 Future는 특별한 호환 요구가 없으면 사용하지 않는다.
- @Configuration 내부 메서드에 @Async를 사용하지 않는다.

### 4.4 예외 처리 규칙

void 반환 @Async 메서드의 예외는 호출자에게 전달되지 않고, 기본적으로는 로깅만 된다. 호출자가 실패를 알아야 하는 경우 CompletableFuture를 사용하고, void를 쓰는 경우에는 AsyncUncaughtExceptionHandler 또는 메서드 내부 명시적 예외 처리 전략을 둔다.

프로젝트 규칙:

- void @Async는 실패를 호출자에게 전달할 필요가 없는 작업에만 사용한다.
- void @Async를 도입하면 예외 처리/로그/모니터링 전략을 같이 정의한다.
- 실패가 비즈니스적으로 중요하면 @Async로 숨기지 않는다.

## 5. Executor / Scheduler 구성 표준

### 5.1 기본 자동 구성을 무심코 공유하지 않는다

Spring Boot는 AsyncTaskExecutor를 자동 구성하고, 그 실행기는 @EnableAsync뿐 아니라 MVC 비동기 요청 처리, WebFlux blocking 지원, GraphQL, JPA bootstrap, background initialization 등 여러 통합 지점에서 사용될 수 있다. 또한 스케줄러도 자동 구성되며, 가상 스레드를 쓰지 않을 때 기본 ThreadPoolTaskScheduler는 기본 스레드 수 1로 동작한다.

따라서 프로젝트 규칙은 다음과 같다.

- @Async용 executor와 @Scheduled용 scheduler를 개념적으로 분리한다.
- 무거운 업무성 비동기 작업을 web 요청 처리와 우연히 같은 executor에 태우지 않는다.
- 스레드 이름 prefix를 명시한다.
- pool size / queue capacity / rejection 정책을 의도적으로 설정한다.
- 운영에서 식별 가능한 bean 이름을 사용한다.

### 5.2 가상 스레드 사용 시 주의

Spring Framework는 SimpleAsyncTaskScheduler가 가상 스레드 정렬 옵션으로 동작할 수 있지만, fixed-delay 작업은 단일 scheduler thread에서 동작하므로 이 경우 fixed-rate나 cron을 권장한다고 설명한다. Spring Boot도 가상 스레드 활성화 시 scheduler가 SimpleAsyncTaskScheduler가 되며 pooling 관련 설정을 무시한다고 설명한다.

프로젝트 규칙:

- 가상 스레드를 켠다고 해서 scheduler 설계를 생략하지 않는다.
- fixed-delay 중심 작업이 많다면 가상 스레드 scheduler를 무비판적으로 선택하지 않는다.

## 6. @Scheduled 표준

### 6.1 사용 기준

@Scheduled는 주기 작업/지연 작업의 진입점으로만 사용한다.

Spring 공식 기준에서:

- 주기 작업에는 cron, fixedDelay, fixedRate 중 정확히 하나를 지정해야 한다.
- initialDelay는 선택 사항이다.
- 메서드는 인자를 받을 수 없다.
- 반환값은 일반적으로 무시된다.
- 같은 메서드에 여러 스케줄 선언을 둘 수 있으며, 이 경우 서로 독립적으로 실행되어 겹칠 수 있다.

프로젝트 규칙:

- 이 프로젝트의 @Scheduled 메서드는 반드시 void 로 작성한다.
- @Scheduled 메서드는 얇은 트리거(thin trigger) 로 유지하고 실제 업무는 application service/use case로 위임한다.
- 하나의 메서드에 여러 @Scheduled를 붙이지 않는다.
- 각 스케줄 작업은 재실행 가능(idempotent) 하고 중복 실행/겹침에 안전해야 한다.

### 6.2 fixedDelay와 fixedRate 선택 기준

Spring 공식 문서 기준:

- fixedDelay는 이전 실행 완료 시점 기준
- fixedRate는 이전 실행 시작 시점 기준 으로 간격이 계산된다.

프로젝트 규칙:

- 이전 실행이 끝난 뒤 다음 실행을 시작해야 하면 fixedDelay
- 일정 간격 기준으로 계속 트리거되어도 괜찮으면 fixedRate
- 업무 시간이 분명한 배치성 작업은 cron
- timezone 의미가 중요한 cron은 zone을 명시하는 방향을 우선 검토한다

### 6.3 스케줄러 풀 크기 규칙

Spring Boot의 기본 scheduler는 단일 스레드일 수 있다. 따라서 둘 이상의 작업이 있거나, 하나라도 오래 걸리는 작업이 있으면 pool size를 명시적으로 설계한다.

프로젝트 규칙:

- scheduler bean 또는 spring.task.scheduling.* 설정을 명시한다.
- “기본값 1개 스레드”에 의존한 채 운영에 올리지 않는다.
- 긴 작업과 짧은 작업이 섞이면 분리 가능성도 검토한다.

## 7. Retry 표준

### 7.1 retry를 사용할 수 있는 위치

Retry는 다음 같은 경우에만 사용한다.

- 외부 HTTP/gRPC/API 호출
- 메시지 브로커 일시 실패
- 네트워크 일시 장애
- 잠깐 후 재시도하면 회복될 수 있는 외부 의존성 오류

Spring Retry는 @EnableRetry로 @Retryable bean에 프록시를 만들며, RetryTemplate 기반의 프로그래밍 방식도 제공한다.

프로젝트 규칙:

- retry는 외부 경계(adapter/client/gateway) 에 가깝게 둔다.
- controller / domain / entity / mapper / validation 로직에는 두지 않는다.
- “왜 재시도 가능한가?”를 설명할 수 없는 경우 retry를 두지 않는다.

### 7.2 기본값을 그대로 쓰지 않는다

Spring Retry의 현재 API 기준:

- maxAttempts 기본값은 3
- retryFor와 noRetryFor를 비워두면 기본적으로 모든 예외가 재시도 대상이 될 수 있다
- backoff는 지정 가능하며 기본은 단순 Backoff 사양이다
- noRetryFor, notRecoverable 같은 세밀한 제어가 가능하다.

프로젝트 규칙:

- @Retryable에는 반드시 retryFor를 명시한다.
- 필요하면 noRetryFor 또는 notRecoverable도 함께 명시한다.
- maxAttempts를 명시한다.
- backoff 전략을 명시한다.
- “기본적으로 모든 예외 재시도” 형태를 금지한다.

### 7.3 @Recover 사용 기준

Spring Retry 공식 문서상 recovery method는:

- @Retryable 메서드와 같은 클래스 에 있어야 하고
- @Recover 로 표시해야 하며
- 반환형이 @Retryable 메서드와 맞아야 한다.

프로젝트 규칙:

- 재시도 소진 뒤 대체 경로가 의미 있을 때만 @Recover를 둔다.
- recover는 “실패를 조용히 삼키는 메서드”가 아니라, 대체 동작 또는 명시적 실패 변환 역할이어야 한다.
- recover가 있어도 관측 가능성(log/metric/alert)을 잃지 않는다.

### 7.4 @Retryable vs RetryTemplate

프로젝트 규칙:

애노테이션 기반이 더 읽기 쉬운 경우: @Retryable

다음 경우에는 RetryTemplate을 우선 검토:

- 한 메서드 전체가 아니라 일부 코드 블록만 재시도해야 하는 경우
- 루프 내부 각 항목마다 다른 retry 문맥이 필요한 경우
- 정책을 동적으로 조합해야 하는 경우
- 테스트에서 retry 경계를 더 명시적으로 다루고 싶은 경우

이 구분은 @Retryable이 bean method 경계의 선언적 방식이고, RetryTemplate은 임의 코드 블록의 프로그래밍 방식이라는 공식 구조에 맞춘 Official + Practice 규칙이다.

## 8. 조합 규칙

### 8.1 한 메서드에 다 몰아넣지 않는다

프로젝트 기본 규칙:

- @Scheduled + @Async + @Retryable를 같은 메서드에 겹쳐 붙이는 것을 기본 금지한다.
- 트리거, 업무 오케스트레이션, 외부 재시도, 후속 비동기 작업은 서로 다른 bean / 메서드 경계 로 나눈다.

이 규칙의 이유는 @Async와 @Retryable 모두 프록시 기반이며, 경계가 흐려질수록 self-invocation·예외 전파·관측 가능성 문제가 커지기 때문이다.

권장 구조:

- @Scheduled → application use case
- application use case → retry가 붙은 external gateway/client
- 비핵심 후속 작업 → 별도 async bean 또는 event listener

### 8.2 트랜잭션과의 결합

프로젝트 규칙:

- 핵심 트랜잭션 오케스트레이션은 application service에 둔다.
- retry 대상 외부 호출과 async 후속 처리까지 같은 메서드에 한꺼번에 섞지 않는다.
- 프록시 경계가 필요한 경우 메서드 분리가 아니라 bean 분리를 우선한다.

이 항목은 기존 transaction 문서와 맞물리는 Project Recommendation이다.

## 9. 체크리스트

다음 질문에 “예”로 답할 수 있어야 도입 가능하다.

@Async:

- 호출자가 즉시 반환되어도 되는가?
- 실패가 호출자에게 반드시 전달될 필요가 없는가, 또는 CompletableFuture로 전달되는가?
- 같은 클래스 내부 호출이 아닌가?
- 전용 executor와 예외 처리 전략이 있는가?

@Scheduled:

- 요청 흐름이 아닌 주기 작업인가?
- 메서드가 얇은 트리거인가?
- 중복 실행/겹침에 안전한가?
- scheduler pool 설정이 의도적으로 잡혀 있는가?

Retry:

- 실패가 일시적이라는 근거가 있는가?
- retryFor, maxAttempts, backoff가 명시되어 있는가?
- validation/domain/programming 오류는 제외되어 있는가?
- recover 또는 최종 실패 경로가 분명한가?
