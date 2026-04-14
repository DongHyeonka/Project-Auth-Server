# @Transactional 위치 기준

## 목적

트랜잭션은 “아무 데나 붙이는 애노테이션”이 아니라,  
**하나의 비즈니스 작업 단위를 원자적으로 끝내야 하는 경계**에 둔다.

이 문서의 핵심은:
- 트랜잭션을 어디에 둘지
- 어디에 두면 안 되는지
- 프록시 기반 동작 때문에 어떤 함정이 있는지
를 명확히 하는 것이다.

## 공식 의미

- Spring의 선언적 트랜잭션은 기본적으로 AOP proxy 기반이다.
- proxy mode에서는 프록시를 통해 들어오는 외부 메서드 호출만 interception 된다.
- self-invocation은 기본적으로 transactional interception을 일으키지 않는다.
- `@Transactional` 기본값은 `PROPAGATION_REQUIRED`, `ISOLATION_DEFAULT`, read-write, 기본 timeout, unchecked exception rollback이다.
- Spring 팀은 인터페이스보다 구체 클래스의 메서드에 `@Transactional`을 두는 것을 권장한다.
- `PROPAGATION_REQUIRED`는 같은 스레드에서 service facade가 여러 repository 호출을 묶는 일반적인 호출 구조에 적절한 기본값이다.
- `REQUIRES_NEW`는 별도 물리 트랜잭션/자원을 더 잡으므로 커넥션 풀 고갈 위험이 있을 수 있다.

## 기본 규칙

### 1. 기본 위치는 application service / use case
트랜잭션 경계의 기본 위치는 application 계층의 service/use case 메서드다.

이유:
- 하나의 비즈니스 작업 단위를 가장 잘 표현한다
- 여러 repository/adapter 호출을 하나의 경계로 묶기 쉽다
- controller / repository / adapter에 흩어지는 것을 막는다

기본 예:
- 회원 가입
- 로그인 완료 처리
- 사용자 상태 변경
- 토큰 발급 + 저장
- 주문 생성
- 재시도 가능한 import 단위

### 2. controller / filter / interceptor / resolver에는 기본 금지
web adapter는 HTTP 경계를 담당한다.  
트랜잭션 경계를 web layer에 두지 않는다.

금지 대상 기본값:
- `@Controller`
- `@RestController`
- `Filter`
- `HandlerInterceptor`
- `HandlerMethodArgumentResolver`
- exception handler

이유:
- HTTP concern과 transaction concern이 섞인다
- web boundary가 persistence 세부를 과도하게 끌어안게 된다
- 요청 전체를 너무 넓은 transaction으로 감쌀 위험이 커진다

### 3. repository / adapter에는 기본 금지
repository나 infrastructure adapter는 “트랜잭션 경계 소유자”가 아니라  
상위 경계 안에서 참여하는 collaborator를 기본값으로 한다.

기본 금지:
- repository 메서드마다 습관적으로 `@Transactional`
- external API client에 `@Transactional`
- mapper/assembler/helper에 `@Transactional`

예외:
- 그 컴포넌트가 독립적인 transaction boundary를 실제로 소유해야 하는 경우
- 프레임워크/기술 통합상 별도 transaction semantics가 정말 필요한 경우

### 4. 읽기 전용 use case는 `readOnly = true` 검토
순수 조회 유스케이스라면 `@Transactional(readOnly = true)`를 우선 검토한다.

적용 후보:
- 상세 조회
- 목록 조회
- 검색
- 통계용 읽기 작업

단, readOnly 안에서 실제 write가 섞이면 안 된다.

### 5. 쓰기 작업은 기본 read-write
상태 변경, 저장, 삭제, 발급, 전이 같은 작업은 기본 read-write transaction으로 둔다.
readOnly를 습관적으로 붙이지 않는다.

### 6. self-invocation을 믿지 않는다
같은 클래스 안에서 `this.someTransactionalMethod()`처럼 호출하면  
proxy mode에서는 transactional interception이 일어나지 않는다.

기본 대응:
- transaction boundary를 public entry method로 둔다
- 필요하면 클래스를 분리한다
- self-invocation을 전제로 설계하지 않는다

### 7. `@PostConstruct` / 초기화 코드에서 transaction에 기대지 않는다
Spring 공식 문서상 proxy가 완전히 준비되기 전에는 기대한 동작이 보장되지 않는다.  
초기화 코드에서 transactional behavior를 전제로 하지 않는다.

### 8. 인터페이스보다 구체 클래스 메서드에 둔다
Spring 팀 권장에 따라 `@Transactional`은 기본적으로 구체 클래스의 메서드에 둔다.

이유:
- AspectJ weaving 등에서 interface annotation이 조용히 무시될 수 있는 함정을 줄인다
- annotation 위치가 더 직접적이고 명확하다

### 9. 클래스 레벨보다 메서드 레벨 우선 검토
클래스 전체가 거의 같은 transaction semantics를 가지면 클래스 레벨 선언을 허용할 수 있다.  
하지만 readOnly/read-write, propagation, timeout이 섞이면 메서드별로 명시한다.

기본:
- 모든 public method가 같은 semantics면 class-level 가능
- 그렇지 않으면 method-level로 명확히 분리

### 10. 트랜잭션은 가능한 한 짧게 유지
트랜잭션은 DB 자원/잠금/연결을 붙잡을 수 있으므로 가능한 짧게 유지한다.

기본 금지:
- 긴 계산
- sleep
- 재시도 루프
- 사용자 입력 대기
- 네트워크 왕복 다수 포함
- 파일 업로드/다운로드 전체를 transaction 안에 유지

### 11. 외부 API 호출을 긴 DB transaction 안에 넣지 않는다
공식 문서가 직접 “외부 API 호출 금지”라고 쓰지는 않지만, transaction 자원과 propagation semantics 설명을 보면  
DB transaction이 열려 있는 동안 원격 네트워크 호출까지 길게 포함시키는 것은 프로젝트 기본값으로 금지하는 것이 안전하다.

기본 방향:
- DB update 전/후로 외부 호출을 분리
- 정말 필요하면 outbox/event/후속 작업 구조 검토
- 원격 호출 때문에 DB connection/lock을 오래 잡지 않는다

### 12. `REQUIRES_NEW`는 예외적으로만
`REQUIRES_NEW`는 기본값이 아니다.

허용 후보:
- 독립 감사 로그 저장
- 실패해도 본 작업과 분리되어야 하는 후속 기록
- 별도 확정 단위가 명확한 경우

주의:
- 별도 물리 트랜잭션/자원을 사용한다
- 커넥션 풀 크기와 deadlock 가능성을 고려해야 한다

### 13. propagation/isolation/timeout은 필요할 때만 명시
기본값은 `REQUIRED`, `ISOLATION_DEFAULT`, 시스템 timeout이다.
즉 의미가 명확할 때만 덮어쓴다.

금지:
- 습관적으로 모든 메서드에 propagation/isolation 지정
- 이유 없는 `REQUIRES_NEW`
- 이유 없는 custom timeout

### 14. rollback 규칙은 예외 설계와 함께 본다
기본적으로 unchecked exception만 rollback된다.
checked exception도 rollback해야 하면 `rollbackFor` 등을 명시한다.

즉:
- 예외 타입 설계
- rollback 정책
- business failure 의미
를 같이 설계한다.

### 15. method visibility와 proxy 제약을 의식
proxy mode에서는 인터페이스 기반 프록시의 transactional 메서드는 public이어야 한다.
class-based proxy에서는 protected/package-visible 메서드도 가능하지만,
프로젝트 기본값은 **외부 진입 public method를 transaction boundary로 두는 것**이다.

## 프로젝트 기준 요약

- 기본 위치는 application service / use case
- controller / filter / resolver / interceptor에는 기본 금지
- repository / external client / mapper에는 기본 금지
- 순수 조회는 `readOnly = true` 검토
- self-invocation 믿지 않음
- `@Transactional`은 구체 클래스 메서드에 우선
- transaction은 짧게 유지
- 외부 API 호출을 긴 DB transaction 안에 넣지 않음
- `REQUIRES_NEW`는 예외적으로만
- rollback 규칙은 예외 타입과 함께 설계
