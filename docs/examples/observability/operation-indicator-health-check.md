# Operation Indicator / Health Check 예시

## 좋은 예시

### 예시 1. 기본 actuator health와 probe 경로를 그대로 사용한다

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
```

**좋은 이유:**

- Spring Boot 기본 probe group을 그대로 사용한다
- Kubernetes가 기대하는 liveness/readiness 의미와 맞다.

### 예시 2. management 포트 분리 시 main port에도 /livez, /readyz를 노출한다

```yaml
management:
  server:
    port: 8081
  endpoint:
    health:
      probes:
        add-additional-paths: true
```

**좋은 이유:**

- actuator 전용 포트만 살아 있고 실제 애플리케이션 포트는 문제인 상황을 줄일 수 있다
- Spring Boot도 이 구성을 좋은 아이디어로 안내한다.

### 예시 3. readiness에만 필수 내부 준비 상태를 추가한다

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: "readinessState,customCheck"
```

**좋은 이유:**

- readiness에 필요한 추가 체크만 명시적으로 포함한다
- liveness와 readiness를 구분해서 설계한다.

### 예시 4. startup이 긴 서비스에는 startup probe를 둔다

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  failureThreshold: 30
  periodSeconds: 10
```

**좋은 이유:**

- 느린 시작 중 liveness 오탐을 줄일 수 있다
- startup probe는 성공 전까지 liveness/readiness 실행을 지연시킨다.

## 나쁜 예시

### 예시 1. liveness에 DB 상태를 직접 넣는다

```java
@Component
public class BadDatabaseLivenessIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return databaseClient.ping() ? Health.up().build() : Health.down().build();
    }
}
```

**나쁜 이유:**

- 외부 DB 장애가 모든 인스턴스 재시작으로 이어질 수 있다
- Spring Boot는 liveness를 외부 체크 기반으로 두지 말라고 권고한다.

### 예시 2. 모든 외부 시스템을 readiness에 무조건 포함한다

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: "readinessState,db,redis,kafka,s3,externalApiA,externalApiB"
```

**나쁜 이유:**

- 공유 외부 시스템 장애 시 전체 인스턴스가 동시에 ready=false가 될 수 있다
- fallback 가능한 비필수 시스템도 서비스 제외 원인이 된다.

### 예시 3. probe 용 controller를 별도로 만든다

```java
@RestController
public class BadHealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }
}
```

**나쁜 이유:**

- actuator가 이미 제공하는 운영 계약과 분리된다
- liveness/readiness/group 정책과 연계되지 않는다
- health semantics를 임의 JSON으로 약화시킨다.

### 예시 4. health indicator에서 무거운 쿼리를 수행한다

```java
@Component
public class BadSlowHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        analyticsRepository.runExpensiveAggregation();
        return Health.up().build();
    }
}
```

**나쁜 이유:**

- health endpoint 자체가 느려진다
- Spring Boot도 느린 indicator를 warning 대상으로 본다.
