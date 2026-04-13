# Timeout 기준

## 1. 목적

이 문서는 외부 API 호출 timeout의 의미, 위치, 기본 정책을 정의한다.

이 문서의 목표는 다음과 같다.

- 외부 연동 호출이 무기한 대기하지 않게 한다
- connect/read/response/pool acquire 같은 timeout 종류를 구분한다
- timeout을 retry, fallback, idempotency와 혼동하지 않게 한다
- provider별/operation별 timeout을 일관되게 설계한다

## 2. 근거 수준

- Official: Spring Boot / Spring Framework / Reactor Netty 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 기능 위에 AWS Builders’ Library 같은 실무 운영 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 모든 원격 호출에는 timeout이 있어야 한다

AWS는 원격 호출에는 timeout을 두는 것이 모범 사례이며, 같은 프로세스 내부가 아닌 프로세스 간 호출 전반에 timeout을 두라고 설명합니다. timeout이 없으면 오래 걸리는 요청이 메모리, 스레드, 연결, ephemeral port 같은 자원을 오래 붙잡아 시스템 전체를 악화시킬 수 있습니다. Reactor Netty도 response timeout을 설정하는 것이 좋은 실천이라고 명시합니다.

프로젝트 규칙:

- 외부 HTTP 호출에 무제한 대기 금지
- 모든 외부 API client는 최소한 timeout 정책을 가져야 한다
- timeout이 설정되지 않은 외부 연동 코드는 승인 후보에서 지양한다

### 3.2 timeout은 retry가 아니다

AWS는 timeout, retry, backoff를 별도 도구로 설명하며, timeout은 대기 시간을 제한하고, retry는 부분 실패/일시 실패를 다시 시도하는 메커니즘이라고 설명합니다. timeout이 발생했다고 해서 side effect가 없었다고 보장되지 않으며, side effect가 있는 API는 idempotency가 있어야 안전하게 재시도할 수 있다고도 말합니다.

프로젝트 규칙:

- timeout은 “언제 포기할지”를 정하는 규칙
- retry는 “포기 전에 다시 시도할지”를 정하는 규칙
- timeout 문서에서 retry를 중복 정의하지 않는다
- side effect가 있는 외부 API는 timeout 이후 재시도 전에 idempotency 가능 여부를 반드시 검토한다

### 3.3 timeout은 외부 시스템별, operation별로 달라질 수 있다

AWS는 timeout 값을 고를 때 downstream latency와 허용 가능한 false timeout 비율을 보고 정해야 하며, 인터넷 구간처럼 네트워크 편차가 큰 경우와 p99.9와 p50이 가까운 tight latency 서비스는 같은 방식으로 잡으면 안 된다고 설명합니다.

프로젝트 규칙:

- 외부 시스템마다 timeout이 다를 수 있다
- 같은 외부 시스템 안에서도 operation별 timeout이 다를 수 있다
- “전 서비스 공통 3초” 같은 일괄값을 기본 전략으로 두지 않는다
- timeout은 실제 latency와 business 중요도에 근거해 결정한다

## 4. timeout 종류 구분

### 4.1 최소한 connect timeout과 read/response timeout을 구분한다

Spring Boot는 전역 HTTP client 설정으로 spring.http.clients.connect-timeout와 spring.http.clients.read-timeout를 제공합니다. HTTP Service group도 connection/read timeout을 그룹별로 설정할 수 있습니다. Reactor Netty는 response timeout과 connection timeout을 별도 개념으로 설명합니다.

프로젝트 규칙:

- 외부 HTTP client는 최소한 다음 둘을 구분한다
- connect timeout
- read/response timeout
- 연결이 안 되는 상황과, 연결은 되었지만 응답이 늦는 상황을 같은 timeout 하나로 퉁치지 않는다

### 4.2 reactive client에서는 pool acquire / TLS / DNS도 별도 고려 대상이다

Reactor Netty는 connection pool acquire timeout, SSL/TLS handshake timeout, proxy timeout, DNS query timeout까지 별도 timeout 옵션으로 설명합니다. connection pool의 pendingAcquireTimeout 기본값은 45초이고, SSL handshake timeout 기본값은 10초이며, DNS query timeout 기본값은 5초입니다.

프로젝트 규칙:

WebClient + Reactor Netty를 쓸 때는 단순 response timeout만 볼 것이 아니라 다음도 검토한다

- connection pool acquire timeout
- SSL handshake timeout
- DNS resolution timeout
- 트래픽이 많거나 TLS/프록시/DNS 영향이 큰 환경에서는 이 고급 timeout을 운영 설계에 포함한다

### 4.3 “전체 호출 deadline”과 client-level timeout을 구분한다

Reactor Netty는 specific timeout 옵션을 두는 편이 Reactor timeout 연산자보다 더 목적에 맞는 제어를 준다고 설명합니다. timeout 연산자는 연결부터 응답 수신까지 전체 동작에 걸리는 시간을 통째로 제한하지만, client-specific timeout은 더 세밀합니다.

프로젝트 규칙:

- client-level timeout은 connect/read/pool/TLS 같은 기술적 단계별 timeout
- business/application deadline은 “이 유스케이스가 전체적으로 몇 초 안에 끝나야 하는가”라는 별도 개념
- 둘을 혼동하지 않는다
- reactive 체인 전체에 무턱대고 timeout()만 거는 것을 기본값으로 두지 않는다

## 5. client 종류별 표준

### 5.1 RestClient 기본값은 Boot 전역 설정 + provider별 override다

Spring Boot는 RestClient.Builder를 자동 구성하고, spring.http.clients.connect-timeout / read-timeout 같은 전역 속성을 제공합니다. 또한 HTTP Service client group별로 connection/read timeout을 다르게 둘 수 있습니다.

프로젝트 규칙:

- imperative 외부 HTTP 호출 기본값은 RestClient
- timeout 기본값은 전역 spring.http.clients.*
- provider별/그룹별 차이는 spring.http.serviceclient.<group> 또는 전용 configuration에서 override
- RestClient.create()를 직접 만들어 timeout 구성을 우회하지 않는다

### 5.2 WebClient는 Reactor Netty timeout까지 함께 본다

Spring Boot는 WebClient.Builder를 자동 구성하고, Boot가 제공하는 builder를 주입해서 쓰는 것을 강하게 권장합니다. Spring Framework는 Reactor Netty HttpClient를 미리 구성해 ReactorClientHttpConnector로 WebClient에 붙일 수 있다고 설명합니다. Reactor Netty는 response timeout, connect timeout, pool timeout, TLS timeout, DNS timeout을 별도로 제공합니다.

프로젝트 규칙:

- reactive/non-blocking 외부 호출은 WebClient
- 단순 Boot 전역 read-timeout만 믿지 않고, 필요하면 Reactor Netty HttpClient를 명시적으로 구성한다
- 대기 원인이 connection인지 response인지 pool acquire인지 구분 가능한 구조를 선호한다

### 5.3 HTTP Service Client는 group 속성으로 timeout을 관리한다

Spring Boot는 @ImportHttpServices와 group 개념을 제공하고, spring.http.serviceclient.<group-name> 아래에서 base URL, default headers, redirect, connection/read timeout, SSL bundle 등을 설정할 수 있다고 설명합니다.

프로젝트 규칙:

- 선언형 HTTP interface client를 쓸 때는 timeout도 group 단위로 관리한다
- interface마다 개별 하드코딩하지 않는다
- 같은 provider 아래 여러 인터페이스가 공통 timeout을 공유하게 한다

## 6. timeout 값 선택 기준

### 6.1 timeout은 downstream latency와 허용 가능한 false timeout 비율로 잡는다

AWS는 intra-region 서비스 호출의 경우 허용 가능한 false timeout 비율(예: 0.1%)을 먼저 정하고, downstream latency percentile(예: p99.9)을 참고해 timeout을 고르는 방식을 권장합니다.

프로젝트 규칙:

- timeout 값은 “감”으로 정하지 않는다
- 가능하면 provider/operation latency 기준을 본다
- 기본 질문은 다음과 같다
- 이 호출이 몇 ms/초 이상 걸리면 사실상 실패로 봐야 하는가?
- false timeout을 얼마나 허용할 것인가?
- timeout 이후 retry/fallback이 가능한가?

### 6.2 인터넷 구간과 내부 구간은 같은 값으로 잡지 않는다

AWS는 인터넷처럼 네트워크 편차가 큰 경우에는 downstream percentile만 보고 timeout을 잡으면 안 되고, reasonable worst-case network latency를 추가로 고려해야 한다고 설명합니다.

프로젝트 규칙:

같은 데이터센터/같은 리전에 있는 내부 서비스 호출과
인터넷을 거치는 SaaS/third-party 호출은
timeout 기준을 다르게 둔다

외부 공개 인터넷 API는 더 큰 네트워크 변동성을 감안한다

### 6.3 너무 낮은 timeout은 배포/콜드 커넥션/TLS 구간에서 오탐을 만든다

AWS는 아주 낮은 timeout(예: 20ms)을 썼을 때 배포 직후 새 secure connection 수립 시간이 timeout에 포함되어 오탐이 생긴 사례를 설명하며, 이후 연결을 미리 준비(prewarm)하는 방식으로 개선했다고 말합니다.

프로젝트 규칙:

- timeout을 지나치게 공격적으로 줄이지 않는다
- 새 연결 수립, TLS handshake, DNS lookup이 포함되는지 확인한다
- 낮은 timeout을 쓰려면 connection reuse/prewarm 전략도 함께 검토한다

### 6.4 “tight latency service”에는 padding을 둔다

AWS는 p99.9와 p50이 가까운 서비스에서는 작은 latency 증가에도 timeout이 급증할 수 있으므로 padding을 두라고 설명합니다.

프로젝트 규칙:

- timeout은 percentile 값에 기계적으로 딱 맞추지 않는다
- 급격한 오탐 증가를 막기 위한 안전 여유를 둔다
- 극단적으로 빡빡한 timeout은 특별한 근거가 있을 때만 허용한다

## 7. operation별 기준

### 7.1 사용자 요청 경로의 외부 호출은 더 엄격한 timeout을 가진다

프로젝트 규칙:

- synchronous request path 안의 외부 호출은 사용자 응답 SLA를 고려해 더 엄격한 timeout을 둔다
- 장시간 대기가 UX와 thread/resource 점유를 악화시키는 경우가 많다
- “느리지만 언젠가 오면 된다”는 기준을 기본값으로 두지 않는다

### 7.2 백그라운드/배치 호출은 더 긴 timeout을 가질 수 있다

프로젝트 규칙:

- 배치/백그라운드 호출은 사용자 직접 응답보다 긴 timeout을 가질 수 있다
- 다만 무기한 대기를 허용하지는 않는다
- 작업 단위 SLA와 재시도/보상 전략을 함께 본다

### 7.3 읽기와 쓰기 호출을 구분한다

AWS는 side effect가 있는 API는 timeout 이후 retry가 중복 side effect를 만들 수 있으므로 idempotency가 중요하다고 설명합니다. timeout 자체도 읽기 호출과 쓰기 호출의 의미가 다를 수 있습니다.

프로젝트 규칙:

- read-only 조회는 상대적으로 더 짧은 timeout을 선호할 수 있다
- side effect가 있는 write 호출은 timeout 후 retry 가능성까지 함께 본다
- “timeout 값만” 정하지 말고, 그 timeout 이후 어떤 동작이 이어질지도 함께 문서화한다

## 8. 설정 위치 규칙

### 8.1 전역 기본값은 공통 설정으로 둔다

Spring Boot는 모든 HTTP client에 적용되는 전역 spring.http.clients.* 속성을 제공합니다.

프로젝트 규칙:

- connect/read timeout의 공통 기본값은 전역 설정으로 둔다
- 서비스 전체 기본값을 문서화한다
- 각 adapter가 제각각 timeout을 하드코딩하지 않는다

### 8.2 provider별 차이는 group 또는 전용 configuration으로 override한다

Spring Boot는 HTTP Service group에 대해 base URL, headers, redirect, connect/read timeout, SSL bundle 등을 그룹별로 둘 수 있다고 설명합니다. 또한 RestClient/WebClient는 injected builder에 좁은 범위 customization을 추가하는 방식을 권장합니다.

프로젝트 규칙:

- provider별 timeout 차이는 group 설정 또는 전용 configuration으로 둔다
- adapter 생성자 안 상수 하드코딩을 기본 금지한다
- 왜 override가 필요한지 근거를 남긴다

### 8.3 operation별 차이는 client 내부에서 명시적으로 표현한다

Reactor Netty는 기본 response timeout 외에 request별 response timeout override도 지원합니다.

프로젝트 규칙:

- 같은 provider 안에서도 operation별로 timeout이 다르면 코드에 의도를 드러낸다
- “특정 operation만 더 길다/짧다”를 숨긴 magic number를 금지한다
- operation별 override는 드물고 명시적이어야 한다

## 9. observability 규칙

### 9.1 timeout은 관측 가능해야 한다

Spring Boot는 auto-configured builders를 통해 HTTP client instrumentation을 함께 적용할 수 있다고 설명합니다. timeout이 일어나도 어떤 provider, 어떤 operation, 어느 단계에서 발생했는지 관측 가능해야 운영이 됩니다.

프로젝트 규칙:

timeout 발생 시 최소한 다음 맥락을 로그/메트릭에서 추적 가능하게 한다

- provider 또는 client name
- operation
- method
- uri template
- timeout type(connect/read/response/pool 등)
- “그냥 timed out” 한 줄 로그로 끝내지 않는다

### 9.2 timeout은 retry/fallback과 함께 해석 가능해야 한다

AWS는 timeout, retry, backoff를 함께 설계해야 하고, retry는 부하를 악화시킬 수 있다고 설명합니다.

프로젝트 규칙:

timeout 로그에는 retry/fallback 결과 맥락이 이어져야 한다

- timeout이 났지만 retry 후 성공했는지
- timeout이 최종 실패인지
- fallback으로 복구됐는지

를 운영자가 구분할 수 있어야 한다

## 10. 금지 규칙

다음은 기본 금지다.

- 외부 HTTP 호출에 무제한 timeout
- connect/read/response timeout을 구분하지 않고 하나의 감각적 숫자로 통일
- RestClient.create() / WebClient.builder() 직접 생성으로 공통 timeout 설정 우회
- base URL, timeout을 adapter 코드 안에 상수로 하드코딩
- reactive 체인 전체에 무턱대고 timeout()만 걸어 세부 원인을 잃어버림
- 매우 낮은 timeout을 두고 TLS/DNS/새 연결 비용을 고려하지 않음
- timeout 이후 retry/idempotency 전략 없이 side effect 호출을 재시도
- timeout 발생 로그에 provider/operation 맥락이 없음

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 외부 호출에는 timeout이 명시되어 있는가?
- 최소 connect timeout과 read/response timeout을 구분하고 있는가?
- timeout 값이 downstream latency와 business SLA에 근거하는가?
- 인터넷 구간, TLS handshake, DNS 비용을 고려했는가?
- timeout 기본값과 provider별 override 위치가 일관적인가?
- reactive client라면 pool acquire / TLS / DNS timeout도 필요한지 검토했는가?
- timeout 이후 retry/fallback/idempotency 동작이 함께 설계돼 있는가?
- timeout 발생 시 provider/operation/timeout type이 관측 가능한가?
