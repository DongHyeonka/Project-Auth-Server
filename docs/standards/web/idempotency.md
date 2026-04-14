# Idempotency 기준

## 1. 목적

이 문서는 API에서 멱등성(idempotency)을 어떻게 정의하고, 어디에 적용하며, 어떤 방식으로 구현할지 정한다.

이 문서의 목표는 다음과 같다.

- HTTP 메서드 자체의 멱등성과 애플리케이션 수준 멱등성을 구분한다.
- 네트워크 타임아웃, 응답 유실, 재시도 상황에서 중복 생성/중복 실행을 막는다.
- Idempotency-Key 기반 중복 방지 정책을 프로젝트 단위로 통일한다.
- controller, service, storage, 응답 규약에서 멱등성 책임을 분명히 한다.

## 2. 근거 수준

- Official: RFC 9110, IETF HTTPAPI draft, 공개 API 가이드/공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 의미 위에 Stripe/PayPal 같은 실무 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 HTTP 메서드의 멱등성과 애플리케이션 멱등성은 다르다

RFC 9110 기준으로 GET, HEAD, OPTIONS, TRACE는 safe 이고, PUT, DELETE, 그리고 safe 메서드들은 idempotent 입니다. 같은 요청을 여러 번 보내도 서버에 의도된 효과는 한 번과 같아야 합니다. 반면 POST는 기본적으로 idempotent가 아니고, 클라이언트는 특별한 근거가 없으면 자동 재시도하면 안 됩니다.

프로젝트 규칙:

- HTTP 멱등성은 메서드 의미에 관한 규칙이다.
- 애플리케이션 멱등성은 중복 요청 방지와 재시도 안전성에 관한 규칙이다.
- PUT/DELETE가 HTTP 차원에서 idempotent라고 해서, 모든 business side effect까지 자동으로 안전하다고 가정하지 않는다.
- POST/PATCH가 기본적으로 비멱등이므로, 재시도 안전성이 필요하면 별도 설계를 둔다.

### 3.2 이 프로젝트의 멱등성 기본 전략은 Idempotency-Key다

IETF 초안은 Idempotency-Key 요청 헤더를 사용해 POST·PATCH 같은 비멱등 메서드를 fault-tolerant 하게 만드는 방향을 제시하고 있고, 서버는 키의 유일성·만료 정책·중복 처리 방식을 문서화해야 한다고 설명합니다. Stripe와 PayPal도 같은 취지로 client-generated key/header를 사용합니다.

프로젝트 규칙:

- 비멱등 command endpoint의 기본 멱등성 수단은 Idempotency-Key 요청 헤더
- 공개 API에서 별도 사유가 없으면 proprietary header보다 Idempotency-Key를 우선 사용
- 외부 third-party 연동에서 상대방이 다른 이름의 헤더를 요구하면 adapter에서 변환한다

### 3.3 멱등성의 목적은 “같은 의도”의 안전한 재시도다

IETF 초안은 같은 key가 같은 요청의 재시도를 식별하기 위한 것이라고 설명하고, Stripe도 동일 key에 대해 첫 결과를 재사용한다고 설명합니다. 즉, 멱등 키는 “대충 중복 방지용 문자열”이 아니라 같은 요청 의도에 대한 재시도 식별자입니다.

프로젝트 규칙:

- 멱등 키는 “같은 요청을 다시 보내는 경우”에만 재사용한다
- 요청 의도가 바뀌면 새 키를 생성한다
- 멱등 키를 “세션 ID”나 “사용자 식별자”처럼 장기 재사용 식별자로 쓰지 않는다

## 4. 적용 대상

### 4.1 기본적으로 적용해야 하는 endpoint

프로젝트 규칙:

다음처럼 중복 실행 위험이 큰 비멱등 요청에는 멱등성을 기본 검토 대상으로 둔다.

- 리소스 생성 POST
- 상태 변경 command POST/PATCH
- 외부 결제/인증/발급/전송과 연결된 요청
- 타임아웃 후 client 재시도가 현실적으로 자주 일어날 수 있는 요청
- “한 번만 수행돼야 하는” business command

예:

- 회원 가입
- 세션/토큰 발급
- 비밀번호 변경
- 이메일 인증 발송
- 환불/정산/결제 확정

### 4.2 기본적으로 적용하지 않는 endpoint

RFC 9110 기준으로 GET/HEAD/OPTIONS/TRACE는 safe이고, PUT/DELETE는 idempotent입니다. Stripe도 GET/DELETE에 idempotency key를 보내도 효과가 없다고 안내합니다.

프로젝트 규칙:

- GET/HEAD/OPTIONS/TRACE에는 Idempotency-Key를 기본적으로 사용하지 않는다
- PUT/DELETE는 HTTP 의미상 이미 idempotent이므로, 별도 애플리케이션 멱등 키는 기본값이 아니다
- 다만 PUT/DELETE가 추가 외부 side effect를 동반하는 특수 endpoint면 별도 검토할 수 있다

## 5. 키 규칙

### 5.1 키는 클라이언트가 생성한다

IETF 초안은 key를 client가 생성한 고유 값으로 설명하고, UUID 같은 random identifier 사용을 권장합니다. Stripe도 V4 UUID 또는 충분한 entropy를 가진 랜덤 문자열을 권장합니다.

프로젝트 규칙:

- Idempotency-Key는 클라이언트 생성
- 서버가 멱등 키를 대신 생성해서 응답으로 내려주고 다음 요청에서 재사용하게 하는 방식을 기본으로 두지 않는다
- 권장 형식은 UUID v4 또는 이에 준하는 고엔트로피 opaque string

### 5.2 키에는 민감정보를 넣지 않는다

Stripe는 idempotency key에 이메일 주소나 개인 식별자 같은 민감정보를 넣지 말라고 권장합니다.

프로젝트 규칙:

- 키에는 이메일, 전화번호, 주민번호, 사용자명 같은 의미 있는 개인정보를 넣지 않는다
- 키는 opaque value 로 취급한다
- 로그에도 원문 전체를 무분별하게 남기지 않는다

### 5.3 키의 유효 범위(scope)를 정의한다

IETF 초안은 key의 유일성 기준은 resource owner가 정의해야 한다고 설명합니다. 즉, “어디까지 같은 key로 보느냐”는 서버 정책입니다.

프로젝트 기본 규칙:

멱등 키 scope는 최소한 다음을 포함해 판단한다

- HTTP method
- 정규화된 operation/resource
- 호출 주체(actor/client)
- idempotency key
- 같은 key라도 다른 operation 이면 충돌로 보지 않는다
- 같은 key라도 다른 사용자/클라이언트 면 같은 요청으로 취급하지 않는다

권장 예:

```text
(actorId, operationName, idempotencyKey)
```

## 6. fingerprint 규칙

### 6.1 키만 보지 말고 fingerprint도 비교한다

IETF 초안은 서버가 request payload로부터 idempotency fingerprint 를 생성할 수 있고, checksum·선택 필드 비교·request digest 등으로 요청 동일성을 판단할 수 있다고 설명합니다.

프로젝트 규칙:

- 서버는 key만 저장하지 말고 request fingerprint 도 함께 관리한다
- fingerprint는 다음 요소를 기반으로 구성한다
- method
- operation/resource
- actor/client
- request body의 canonical form 또는 의미 필드
- fingerprint 비교 없이 key만 믿고 중복 처리하지 않는다

### 6.2 fingerprint는 “의미적으로 같은 요청” 기준으로 만든다

프로젝트 규칙:

- 단순 raw JSON 문자열 비교보다 의미 필드 기준 비교를 우선 검토한다
- 필드 순서 차이, 불필요한 공백 차이, 서버가 무시하는 필드 차이 때문에 다른 요청으로 오판하지 않게 한다
- 반대로 실제 business 의미가 다른 요청은 반드시 다른 fingerprint가 되게 한다

## 7. 저장/처리 규칙

### 7.1 첫 완료 결과를 저장하고 같은 결과를 재생한다

IETF 초안은 중복 요청이 원래 요청 완료 후 재시도된 경우, 서버가 이전에 완료된 작업의 결과를 다시 응답해야 한다고 설명합니다. Stripe도 같은 key에 대해 첫 요청의 status code와 body를 재사용하고, 성공뿐 아니라 실패 결과도 재사용한다고 명시합니다.

프로젝트 규칙:

같은 key + 같은 fingerprint + 이미 완료된 요청이면

- 같은 status
- 같은 body
- 필요하면 같은 핵심 header(Location 등)

를 재응답한다.
중복 요청이라고 해서 새 business execution을 다시 시작하지 않는다.

### 7.2 요청이 아직 처리 중이면 409를 기본으로 한다

IETF 초안은 원 요청이 아직 처리 중인 상태에서 같은 key로 재시도되면 409 Conflict 를 권장합니다.

프로젝트 규칙:

- 같은 key + 같은 fingerprint인데 원 요청이 in progress 면 기본 응답은 409 Conflict
- body code는 예:
- IDEMPOTENCY_REQUEST_IN_PROGRESS
- 이 경우 client는 잠시 후 같은 key로 다시 재시도할 수 있다

### 7.3 같은 키를 다른 요청에 재사용하면 거절한다

IETF 초안은 같은 key를 다른 payload 로 재사용하면 422 Unprocessable Content 를 권장합니다.

프로젝트 규칙:

- 같은 key + 다른 fingerprint는 기본적으로 422 Unprocessable Content
- body code는 예:
- IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
- 서버는 조용히 새 요청으로 처리하지 않는다

### 7.4 키가 필요한 endpoint에서 키가 없으면 400을 기본으로 한다

IETF 초안은 멱등 키가 문서상 필수인 operation에서 헤더가 없으면 400 Bad Request 를 권장합니다.

프로젝트 규칙:

- 멱등 키 필수 endpoint에서 헤더 누락 시 400 Bad Request
- body code는 예:
- IDEMPOTENCY_KEY_REQUIRED

## 8. 만료(TTL) 규칙

### 8.1 TTL은 반드시 문서화한다

IETF 초안은 서버가 key의 expiration policy를 문서화해야 한다고 설명합니다. Stripe는 key를 최소 24시간 이후 자동 제거 가능 하다고 말하고, PayPal은 일부 POST API에서 PayPal-Request-Id를 최대 45일 예시로 보여 줍니다. 즉, TTL에는 업계 단일 정답이 없습니다.

프로젝트 규칙:

- 멱등 키 TTL은 endpoint 문서에 명시한다
- 프로젝트 기본 최소 TTL 권장값은 24시간
- 금융/정산/고비용 side effect는 더 긴 TTL을 검토한다
- TTL이 지나면 같은 key는 새 요청으로 처리될 수 있음을 문서화한다

### 8.2 TTL은 business 위험에 따라 다르게 줄 수 있다

프로젝트 규칙:

- 단순 생성/변경: 24시간 전후
- 고비용 외부 side effect: 더 긴 TTL 가능
- 너무 긴 TTL은 key storage 비용과 오탐 가능성을 높이므로 무작정 늘리지 않는다

## 9. 구현 규칙

### 9.1 멱등성 저장소는 다중 인스턴스 환경에서도 일관돼야 한다

프로젝트 규칙:

- production에서는 프로세스 메모리만으로 멱등성 보장 금지
- 다중 인스턴스에서 공유되는 저장소를 사용한다
- RDB
- Redis
- 기타 내구성 있는 shared store
- “한 서버에만 있는 ConcurrentHashMap” 으로 끝내지 않는다

### 9.2 business write와 멱등성 기록은 원자성 경계를 검토한다

프로젝트 규칙:

- “실제 side effect는 일어났는데 idempotency record는 안 남는” 상태를 최대한 줄인다
- 가능하면 business state write와 idempotency completion 기록의 원자성/정합성을 맞춘다
- 외부 시스템까지 걸친 완전 원자성은 어렵더라도, 적어도 중복 실행을 줄이는 방향 으로 설계한다

### 9.3 controller보다 application/service 경계에 두는 것을 기본으로 한다

프로젝트 규칙:

- controller는 Idempotency-Key를 읽어 application command로 전달
- 실제 중복 방지 판정, fingerprint 비교, 결과 재생은 application/service 전용 구성요소가 담당
- controller에서 직접 storage를 만지며 멱등성 로직을 구현하지 않는다

## 10. 응답 규칙

### 10.1 멱등성 오류도 일반 실패 응답 규약을 따른다

프로젝트 규칙:

- 멱등성 관련 오류도 ApiResult.fail(ErrorCode...) 형식을 따른다

예:

- IDEMPOTENCY_KEY_REQUIRED
- IDEMPOTENCY_REQUEST_IN_PROGRESS
- IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
- 멱등성 오류라고 해서 별도 임시 JSON 구조를 만들지 않는다

### 10.2 재생된 응답임을 알려야 할지 여부는 API군 단위로 정한다

프로젝트 규칙:

- 필요하면 X-Idempotent-Replay: true 같은 응답 헤더를 둘 수 있다
- 하지만 body 계약을 바꿔서 “재생 응답” 전용 구조를 만들지는 않는다
- 헤더 사용 여부는 API군 단위로 일관되게 정한다

## 11. 금지 규칙

다음은 기본 금지다.

- GET/HEAD에 멱등 키를 기본 요구
- 같은 key를 다른 요청 의도에 재사용
- 민감정보를 key에 포함
- controller 안에서 멱등성 저장/판정을 직접 구현
- 다중 인스턴스 환경에서 로컬 메모리만으로 멱등성 보장
- 같은 key + 다른 payload를 조용히 새 요청으로 처리
- 실패 응답을 무조건 200 OK로 보내고 body만 실패로 표시
- TTL/적용 대상/재시도 정책 문서 없이 운영

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 endpoint는 비멱등 요청이며 재시도 안전성이 필요한가?
- Idempotency-Key 적용 여부가 문서화돼 있는가?
- key scope와 fingerprint 기준이 정의돼 있는가?
- 같은 key + 같은 fingerprint 재시도 시 같은 결과를 재생하는가?
- 같은 key + 다른 fingerprint 재사용을 거절하는가?
- in-flight duplicate를 409로 처리하는가?
- TTL과 저장소 전략이 production 환경에 맞는가?
