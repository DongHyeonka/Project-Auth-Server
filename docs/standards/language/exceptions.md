# exceptions 기준

## 목적

예외는 정상 흐름 제어 도구가 아니라, **예외 상황을 명시적으로 전달하고 경계에서 번역하는 수단**으로 사용한다.

## 공식 의미

- Java 예외는 `Throwable` 계층에 속한다.
- `RuntimeException`과 `Error`가 아닌 예외는 checked exception으로 취급된다.
- 예외는 cause와 suppressed exception을 함께 가질 수 있다.
- `try-with-resources`는 자원 정리를 보장하며, close 중 발생한 예외는 suppressed로 보존될 수 있다.
- Spring transaction은 기본적으로 `RuntimeException`과 `Error`에서 rollback한다. checked exception은 기본 rollback 대상이 아니다.

## 기본 규칙

### 1. 예외는 정상 분기 대신 예외 상황에만 사용
다음 용도로 예외를 쓰지 않는다.

- 일반적인 조건 분기
- 결과 없음 표현
- 루프 탈출
- 검증 실패를 무조건 예외로만 처리하는 패턴

기본:
- 정상적인 “없음”은 Optional/empty collection/명시적 결과 타입
- 예외는 정말 비정상적이거나 계약 위반인 경우

### 2. catch는 번역 / 문맥 추가 / 복구일 때만
예외를 catch하는 목적은 아래 셋 중 하나여야 한다.

- 계층 번역
- 의미 있는 문맥 추가
- 명시적 복구

그 외 목적 없는 catch는 금지한다.

### 3. broad catch 기본 금지
다음 catch는 기본 금지한다.

- `catch (Exception e)`
- `catch (Throwable t)`

예외:
- 최상위 boundary에서 마지막 방어선으로 처리할 때
- framework integration 때문에 정말 필요한 경우
- 테스트 코드에서 의도가 분명한 경우

기본값은 더 구체적인 예외를 잡는 것이다.

### 4. 빈 catch 금지
아무 일도 하지 않는 catch block은 금지한다.

정말 무시해야 하는 경우만:
- 왜 안전하게 무시 가능한지 주석으로 설명
- 가능하면 metrics/logging/상태 기록 중 하나 수행

### 5. `printStackTrace()` 금지
업무 코드에서는 `printStackTrace()`를 사용하지 않는다.

기본:
- logging framework로 기록
- 또는 적절한 예외로 rethrow
- 또는 상위 계층으로 전파

### 6. cause를 버리지 않는다
예외를 번역할 때 원인 예외를 cause로 보존한다.

좋은 예:
- `throw new InfrastructureException(code, message, cause)`

나쁜 예:
- `throw new InfrastructureException(code, "failed")` 만 하고 원인을 버림

### 7. 메시지는 문맥을 더하고, 중복은 줄인다
예외 메시지는 “무엇을 하다가 왜 실패했는지”를 추가한다.
단, 하위 예외 메시지를 그대로 복붙해 중복하지 않는다.

예:
- 좋음: `Failed to sign JWT with Vault transit key`
- 나쁨: `IOException occurred` / `Error happened`

### 8. checked / unchecked 선택은 복구 가능성 기준
기본 방향:
- 호출자가 의미 있게 복구/대응할 수 있는 경우 -> checked exception 검토
- 프로그래밍 오류, 불변식 위반, 계약 위반, 복구 불가 -> unchecked exception 우선

프로젝트 기본은:
- domain/application/infrastructure 내부의 의미 예외는 대체로 unchecked
- 외부 API/IO 경계에서는 원래 checked 예외를 잡고 계층 예외로 번역 가능

### 9. 계층별 번역 책임을 지킨다
- domain: 도메인 의미만 표현
- application: business outcome / error code로 번역
- infrastructure: 기술 실패를 infrastructure exception으로 번역
- presentation/bootstrap: HTTP/security/framework boundary로 번역

예외를 아무 계층에서나 최종 사용자 메시지로 확정하지 않는다.

### 10. try-with-resources 우선
닫아야 하는 자원은 `try-with-resources`를 기본값으로 사용한다.

금지:
- `finally`에서 close 하다가 기존 예외를 덮어쓰는 패턴
- 자원 해제를 수동으로 반복 구현하는 패턴

### 11. `finally`에서 return/throw 금지
`finally`에서 return/throw 하면 try/catch의 결과를 덮어쓸 수 있다.
`finally`는 정리 작업만 수행하고 정상 종료해야 한다.

### 12. InterruptedException은 별도 처리
`InterruptedException` 가능성이 있는 코드를 `Exception`으로 뭉뚱그려 잡지 않는다.

기본 방향:
- 가능하면 별도로 catch
- 현재 스레드 인터럽트 상태 복원 검토 (`Thread.currentThread().interrupt()`)
- interruption 의미를 상위로 전달

### 13. transaction rollback 규칙을 예외 설계에 반영
Spring transaction 경계 안에서는 예외 타입이 rollback에 영향을 준다.

기본:
- unchecked -> 기본 rollback
- checked -> 기본 no rollback
- checked도 rollback해야 하면 `rollbackFor` 등으로 명시

따라서 “checked냐 unchecked냐”를 스타일이 아니라 transaction 의미까지 보고 결정한다.

### 14. 테스트에서 try-catch 남용 금지
예외 기대 테스트는 `assertThrows` 류를 우선 사용한다.
예외를 catch하고 `fail()`만 호출하는 패턴은 지양한다.

### 15. throws 선언은 실제로 던질 수 있는 checked 예외만
checked exception을 실제로 던지지 않는데 시그니처에 선언하지 않는다.
호출자에게 불필요한 처리 부담을 준다.

### 16. 구체 예외를 던진다
`Exception`, `RuntimeException` 같은 너무 넓은 기반 예외를 직접 던지지 않는다.
의미에 맞는 더 구체적인 예외를 사용한다.

## 프로젝트 기준 요약

- 예외는 정상 흐름 제어 수단이 아니다
- catch는 번역 / 문맥 추가 / 복구일 때만
- broad catch, empty catch, printStackTrace 금지
- cause 보존
- try-with-resources 우선
- `finally`에서 return/throw 금지
- `InterruptedException` 별도 처리
- Spring rollback 규칙을 예외 타입 설계에 반영
- 구체 예외 사용
