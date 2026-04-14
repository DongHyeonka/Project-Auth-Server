# Operation Indicator / Health Check 기준

## 1. 목적

이 문서는 운영 상태 노출과 health check 기준을 정의한다.

이 문서의 목표는 다음과 같다.

- health endpoint를 단순 ping이 아니라 운영 계약으로 다룬다
- liveness, readiness, startup의 의미를 구분한다
- 외부 의존성 포함 여부를 일관되게 결정한다
- Kubernetes probe, Actuator health group, custom health indicator의 역할을 분리한다

## 2. 근거 수준

- Official: Spring Boot / Kubernetes 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 일반적인 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 health check는 “살아 있음”과 “트래픽 수용 가능”을 구분해야 한다

Kubernetes는 probe를 startup, liveness, readiness 세 종류로 구분합니다. liveness는 컨테이너를 재시작해야 하는지 판단하고, readiness는 현재 트래픽을 받을 준비가 되었는지 판단하며, startup은 느린 시작 동안 liveness/readiness 실행을 지연시키는 역할을 합니다. Spring Boot도 ApplicationAvailability를 통해 liveness와 readiness 상태를 별도로 다루고, 이를 health group으로 노출합니다.

프로젝트 규칙:

- health check를 하나의 “UP/DOWN” 개념으로만 보지 않는다
- liveness 와 readiness 를 분리한다
- startup 시간이 긴 서비스는 startup probe 검토를 기본으로 한다

### 3.2 health endpoint는 운영자와 오케스트레이터를 위한 계약이다

Spring Boot Actuator의 health endpoint는 운영과 모니터링을 위한 엔드포인트이며, health group, status severity order, HTTP status mapping, show-details 정책 등을 설정할 수 있습니다.

프로젝트 규칙:

- health endpoint는 애플리케이션 내부 디버그 API가 아니다
- 외부 공개 API처럼 임의 shape를 만들지 않는다
- Actuator health 규약 위에 필요한 최소 custom indicator만 추가한다

### 3.3 health는 “무엇을 자동화할 것인가”를 기준으로 설계한다

Kubernetes는 liveness 실패 시 컨테이너를 재시작하고, readiness 실패 시 그 인스턴스로 트래픽을 보내지 않습니다. 따라서 어떤 검사를 어디에 넣을지는 “실패했을 때 플랫폼이 어떤 행동을 해도 안전한가”를 기준으로 정해야 합니다.

프로젝트 규칙:

- 재시작이 정답인 문제만 liveness에 반영한다
- 일시적 과부하/의존성 문제/초기화 대기는 readiness에 반영할지 검토한다
- 단순 상태 조회와 자동 운영 액션 유발 신호를 혼동하지 않는다

## 4. 엔드포인트 표준

### 4.1 기본 health endpoint는 /actuator/health다

Spring Boot는 기본적으로 health endpoint를 제공하고, 일반적으로 /actuator/health에 매핑합니다.

프로젝트 규칙:

- 기본 health endpoint는 Actuator 기본 경로를 따른다
- 커스텀 /health를 별도로 만드는 것을 기본값으로 두지 않는다
- 외부 노출 정책은 actuator exposure/security 정책과 함께 설계한다

### 4.2 Kubernetes probe는 health group 경로를 사용한다

Spring Boot는 Kubernetes 환경에서 /actuator/health/liveness 와 /actuator/health/readiness 를 별도 HTTP probe로 노출할 수 있고, management.endpoint.health.probes.enabled로 제어할 수 있습니다.

프로젝트 규칙:

Kubernetes probe는 기본적으로

- liveness: /actuator/health/liveness
- readiness: /actuator/health/readiness
- 별도 이유가 없으면 커스텀 probe URL을 새로 만들지 않는다

### 4.3 관리 포트 분리 시 메인 포트 추가 노출을 검토한다

Spring Boot는 actuator가 별도 management port에만 있으면 실제 애플리케이션 연결 상태와 probe 결과가 어긋날 수 있다고 설명하고, management.endpoint.health.probes.add-additional-paths=true로 메인 포트에 /livez, /readyz를 추가할 수 있다고 안내합니다.

프로젝트 규칙:

- management port를 분리한 서비스는 메인 포트에도 probe path를 노출할지 기본 검토한다
- Kubernetes 운영이면 /livez, /readyz 추가 노출을 우선 검토한다
- “actuator만 살아 있고 앱은 실제로 못 받는” false positive를 피한다

## 5. Liveness 표준

### 5.1 liveness는 “재시작이 필요한가”를 판단한다

Kubernetes는 liveness probe를 컨테이너 재시작 판단에 사용하고, Spring Boot는 liveness를 “애플리케이션이 스스로 회복 가능한가, 아니면 broken state라 재시작이 필요한가”의 의미로 설명합니다.

프로젝트 규칙:

- liveness는 프로세스/애플리케이션 내부가 더 이상 정상 진행 불가한가 를 표현한다
- deadlock, 내부 상태 붕괴, 회복 불가능한 오류는 liveness 대상이 될 수 있다
- 일시적 외부 의존성 장애는 기본적으로 liveness 대상이 아니다

### 5.2 liveness는 외부 시스템 의존을 기본 금지한다

Spring Boot는 liveness 상태를 DB, 외부 Web API, 외부 캐시 같은 외부 체크에 기반하면 대규모 재시작과 cascading failure를 유발할 수 있으므로 일반적으로 그렇게 하지 말라고 설명합니다.

프로젝트 규칙:

- DB 연결 상태를 liveness에 넣지 않는다
- 외부 API 상태를 liveness에 넣지 않는다
- Redis, Kafka, S3 같은 외부 의존성도 기본적으로 liveness에 넣지 않는다

## 6. Readiness 표준

### 6.1 readiness는 “현재 트래픽을 받을 준비가 되었는가”를 판단한다

Kubernetes는 readiness probe를 트래픽 수용 가능 여부 판단에 사용하고, 초기 연결 수립, 파일 로딩, 캐시 warming, 일시적 과부하 복구 같은 상황에 유용하다고 설명합니다. Spring Boot도 readiness 상태를 별도 availability state로 다룹니다.

프로젝트 규칙:

- readiness는 현재 인스턴스가 요청을 받아도 되는가 를 표현한다
- 시작 중, 일시적 overload, 내부 준비 미완료는 readiness false 대상이 될 수 있다
- readiness 실패는 기본적으로 “트래픽 제외” 의미다

### 6.2 readiness의 외부 의존성 포함은 신중하게 결정한다

Spring Boot는 readiness probe에 외부 체크를 넣을지 여부는 개발자가 신중히 판단해야 하며, 기본적으로 추가 health check를 넣지 않는다고 설명합니다. 공유된 외부 시스템을 readiness에 넣으면 전체 서비스가 한꺼번에 서비스 제외될 수 있고, 반대로 fallback/circuit breaker가 있는 비필수 의존성은 readiness에 넣지 말아야 한다고도 설명합니다.

프로젝트 규칙:

readiness에 외부 의존성을 넣기 전에 아래를 판단한다

- 이 의존성이 필수 인가
- 장애 시 이 인스턴스만 제외하는 것이 맞는가
- fallback/circuit breaker로 계속 서비스 가능 한가
- 전체 인스턴스가 동시에 readiness false가 될 위험은 없는가
- 비필수 외부 시스템은 readiness에서 제외한다
- fallback 가능한 외부 시스템은 readiness에서 제외하는 쪽을 기본 선호한다

## 7. Startup probe 표준

### 7.1 startup probe는 느린 시작을 보호할 때 사용한다

Kubernetes는 startup probe가 있으면 그것이 성공하기 전까지 liveness/readiness를 실행하지 않는다고 설명합니다. Spring Boot도 애플리케이션이 liveness 기간보다 오래 걸려 시작할 수 있으면 startup probe를 가능한 해결책으로 언급합니다.

프로젝트 규칙:

- 시작 시간이 긴 서비스는 startup probe를 기본 검토한다
- migration, warm-up, 대규모 cache load, 대형 model load 같은 작업이 있으면 startup probe를 우선 검토한다
- startup probe 없이 liveness 초기 threshold만 무작정 늘리는 방식을 기본값으로 두지 않는다

### 7.2 readiness만으로 충분한 경우도 있다

Spring Boot는 일반적으로 readiness probe가 시작 완료 전까지 실패하므로, startup probe가 항상 필요한 것은 아니라고 설명합니다.

프로젝트 규칙:

- startup probe는 필수 기본값 이 아니다
- readiness만으로 충분하면 추가하지 않는다
- startup probe는 “느린 시작 때문에 liveness가 오탐하는 경우”에 우선 도입한다

## 8. Health indicator 표준

### 8.1 indicator는 “운영상 의미 있는 상태”만 검사한다

Spring Boot Actuator는 built-in health endpoint와 health group을 제공하고, health group에 include/exclude를 둘 수 있으며, 존재하지 않는 contributor를 참조하면 기본적으로 startup validation에 실패하도록 할 수 있습니다.

프로젝트 규칙:

- custom health indicator는 운영상 실제 의미가 있을 때만 추가한다
- “검사할 수 있으니 넣는다”를 금지한다
- health indicator가 너무 많아져 endpoint가 내부 진단 덤프가 되지 않게 한다

### 8.2 느린 health indicator는 운영 비용으로 본다

Spring Boot는 느린 health indicator에 대해 warning 로그를 남기는 threshold 설정을 제공하며 기본값은 10초입니다.

프로젝트 규칙:

- health check는 저비용이어야 한다
- 느린 indicator는 원인 분석 대상이다
- health endpoint가 무거운 DB query나 외부 API full round-trip을 기본 수행하지 않게 한다

### 8.3 health group은 의미 단위로 나눈다

Spring Boot는 health endpoint groups를 지원하고, liveness/readiness도 그 위에 구현되어 있습니다.

프로젝트 규칙:

기본 그룹은

- global health
- liveness
- readiness

별도 그룹이 필요하면 운영 목적이 분명할 때만 만든다

그룹 membership은 존재하지 않는 indicator 참조 없이 명시적으로 관리한다

## 9. 응답/노출 규칙

### 9.1 health detail 노출은 최소화한다

Spring Boot는 management.endpoint.health.show-details와 show-components로 상세 노출 범위를 제어할 수 있고, 기본 show-details는 never입니다. 또한 health endpoint 접근과 세부 노출 권한도 제어할 수 있습니다.

프로젝트 규칙:

- 외부/일반 접근에서는 상세 health 정보를 기본 비노출
- 운영자/내부 접근에서만 상세 정보 노출을 검토한다
- health endpoint를 내부 시스템 구조 설명 API처럼 만들지 않는다

### 9.2 health status와 HTTP status mapping은 임의 변경을 지양한다

Spring Boot는 health status를 HTTP status로 매핑하는 설정을 제공하고, 기본 등록된 status는 sensible default로 매핑된다고 설명합니다.

프로젝트 규칙:

- 기본 health status → HTTP status 매핑을 우선 유지한다
- 특별한 운영 이유가 없으면 custom mapping을 남발하지 않는다
- health endpoint HTTP status는 probe와 모니터링이 기대하는 의미를 깨지 않게 한다

## 10. 구현 위치 규칙

### 10.1 probe 경로는 controller가 아니라 Actuator가 담당한다

Spring Boot는 health endpoint, health group, liveness/readiness probe를 actuator로 제공하므로, probe 용 controller를 별도로 만드는 것이 기본 설계가 아닙니다.

프로젝트 규칙:

- /actuator/health* 계열은 actuator에 맡긴다
- @RestController("/health") 같은 수제 구현을 기본 금지한다
- 필요한 확장은 custom HealthIndicator나 health group 설정으로 해결한다

### 10.2 외부 의존성 상태 판단은 integration 경계에서, 최종 반영은 health group에서 한다

프로젝트 규칙:

- 외부 시스템 상태 판단 로직은 관련 integration component가 책임질 수 있다
- 하지만 probe 포함 여부는 readiness/liveness 정책 문맥에서 최종 결정한다
- 연동 로직이 있다고 해서 자동으로 health indicator에 포함하지 않는다

## 11. 금지 규칙

다음은 기본 금지다.

- liveness에 DB/외부 API/캐시 같은 외부 의존성 기본 포함
- readiness에 모든 외부 시스템을 무비판적으로 포함
- 느린/무거운 쿼리를 health indicator에 넣음
- probe 용 endpoint를 controller로 직접 새로 만듦
- management 포트 분리 상태에서 메인 앱 수용성 차이를 무시
- startup이 긴 서비스에 startup probe 검토 없이 liveness 오탐을 방치
- health detail을 외부에 과도하게 노출
- health group에 존재하지 않는 indicator를 느슨하게 참조

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- liveness와 readiness의 의미를 분리했는가?
- liveness가 외부 의존성 상태에 흔들리지 않는가?
- readiness에 포함한 외부 의존성은 정말 필수인가?
- startup이 긴 서비스라면 startup probe를 검토했는가?
- health indicator가 저비용인가?
- management 포트 분리 시 main port 추가 probe 경로를 검토했는가?
- health detail 노출 범위를 최소화했는가?
