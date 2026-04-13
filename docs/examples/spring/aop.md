# AOP 예시

## 좋은 예시 1: 실행 시간 측정

```java
@Aspect
@Component
public class TimingAspect {

    @Pointcut("execution(public * com.project.auth.application..*(..))")
    public void applicationOperation() {}

    @Around("applicationOperation()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsed = System.nanoTime() - start;
            log.info("method={} elapsedNanos={}", joinPoint.getSignature(), elapsed);
        }
    }
}
```

**왜 좋은가:**

- 횡단 관심사인 timing만 다룬다
- 비즈니스 로직을 바꾸지 않는다
- pointcut이 이름 있는 작은 단위다

## 좋은 예시 2: 예외 기록

```java
@Aspect
@Component
public class ExceptionLoggingAspect {

    @Pointcut("execution(public * com.project.auth.application..*(..))")
    public void applicationOperation() {}

    @AfterThrowing(pointcut = "applicationOperation()", throwing = "exception")
    public void logFailure(Exception exception) {
        log.error("application failure", exception);
    }
}
```

**왜 좋은가:**

- 예외를 숨기지 않고 기록만 한다
- 비즈니스 의미를 변경하지 않는다

## 좋은 예시 3: annotation 기반 감사

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
}

@Aspect
@Component
public class AuditAspect {

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        auditLog.record(audited.action(), joinPoint.getSignature().toShortString());
        return result;
    }
}
```

**왜 좋은가:**

- annotation으로 의도를 드러낸다
- 횡단 concern만 수행한다
- service 흐름을 숨기지 않는다

## 좋은 예시 4: named pointcut 조합

```java
@Aspect
@Component
public class CommonPointcuts {

    @Pointcut("execution(public * *(..))")
    public void publicMethod() {}

    @Pointcut("within(com.project.auth.application..*)")
    public void inApplicationLayer() {}

    @Pointcut("publicMethod() && inApplicationLayer()")
    public void applicationPublicOperation() {}
}
```

**왜 좋은가:**

- 작은 pointcut을 조합한다
- 범위를 읽고 설명하기 쉽다

## 나쁜 예시 1: 핵심 비즈니스 로직을 AOP로 이동

```java
@Around("execution(* ..LoginService.login(..))")
public Object issueTokenAndSaveAudit(ProceedingJoinPoint joinPoint) throws Throwable {
    ...
}
```

**문제:**

- 핵심 use case 흐름이 숨는다
- 코드 추적이 어려워진다
- 서비스가 해야 할 결정을 aspect가 가져간다

## 나쁜 예시 2: self-invocation 기대

```java
@Service
public class SampleService {

    public void foo() {
        this.bar(); // aspect 기대
    }

    public void bar() {
        ...
    }
}
```

**문제:**

- proxy를 통과하지 않아 advice가 적용되지 않을 수 있다

## 나쁜 예시 3: 너무 넓은 pointcut

```java
@Before("execution(* *(..))")
public void logEverything() {
    ...
}
```

**문제:**

- 범위가 지나치게 넓다
- 성능/디버깅/예측 가능성 모두 나빠질 수 있다
- 어떤 코드가 영향을 받는지 설명하기 어렵다

## 나쁜 예시 4: @Around로 예외 숨김

```java
@Around("execution(* ..*(..))")
public Object swallow(ProceedingJoinPoint joinPoint) {
    try {
        return joinPoint.proceed();
    } catch (Throwable ex) {
        return null;
    }
}
```

**문제:**

- 실패를 정상값처럼 숨긴다
- 디버깅과 계약을 깨뜨린다
