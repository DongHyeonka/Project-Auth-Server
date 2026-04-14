# Integration Idempotency 기준

## 1. 목적

이 문서는 외부 API / integration 호출에서 outbound idempotency 를 어떻게 다룰지 정의한다.

이 문서의 목표는 다음과 같다.

- 외부 provider가 제공하는 idempotency 기능을 안전하게 사용한다
- timeout, partial failure, 응답 유실 상황에서 중복 side effect 를 막는다
- 우리 내부 idempotency key와 provider idempotency key의 관계를 명확히 한다
- 외부 API 재시도 시 어떤 조건에서 같은 key를 재사용해야 하는지 정한다

## 2. 근거 수준

- Official: IETF HTTPAPI draft, Stripe, PayPal 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 규약 위에 일반적인 연동 운영 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 이 문서는 inbound가 아니라 outbound idempotency를 다룬다

IETF 초안은 Idempotency-Key를 클라이언트가 서버에 보내는 중복 방지 키로 설명한다. 우리 서비스가 provider를 호출할 때는, 우리가 그 provider 입장에서 “클라이언트”가 된다. 따라서 이 문서는 “사용자가 우리 API를 다시 호출하는 상황”이 아니라, “우리가 외부 provider에 같은 요청을 다시 보내는 상황”을 다룬다.

프로젝트 규칙:

- inbound idempotency와 outbound idempotency를 같은 문서로 뒤섞지 않는다
- 이 문서는 provider 호출용 키 생성/재사용/저장/오류 처리 규칙만 정의한다

### 3.2 outbound idempotency의 핵심 목적은 “같은 외부 side effect를 한 번만 일으키는 것”이다

Stripe는 생성/수정 요청에 idempotency key를 사용하면 연결 오류나 응답 유실이 있어도 같은 요청을 안전하게 반복할 수 있다고 설명한다. PayPal도 POST 호출에서 PayPal-Request-Id를 사용하면 서버가 중복 생성/처리를 피할 수 있다고 설명한다. 즉 outbound idempotency는 “같은 외부 요청 의도”를 다시 보내더라도 provider 쪽에서 한 번만 처리되게 만드는 장치다.

프로젝트 규칙:

- 외부 생성/확정/발급/결제/전송 같은 side effect 호출에는 outbound idempotency를 기본 검토한다
- “응답을 못 받았으니 다시 보내자” 상황에서 중복 side effect가 나지 않아야 한다

### 3.3 retry와 outbound idempotency는 함께 설계한다

Stripe는 네트워크 오류가 나더라도 같은 idempotency key를 써서 다시 보내면 중복 생성 위험을 줄일 수 있다고 설명한다. 반대로 key 없이 같은 POST를 다시 보내면 중복 호출이 될 수 있다. PayPal도 PayPal-Request-Id를 생략하면 요청이 중복될 수 있다고 설명한다. 따라서 retry는 outbound idempotency와 분리해서 설계할 수 없다.

프로젝트 규칙:

- side effect가 있는 외부 API retry는 outbound idempotency 검토 없이 자동화하지 않는다
- timeout 이후 retry 전략은 반드시 provider idempotency 지원 여부와 함께 본다

## 4. 언제 필요한가

### 4.1 기본 검토 대상

프로젝트 규칙:

다음은 outbound idempotency 기본 검토 대상이다.

- 결제 승인/확정/캡처
- 토큰/세션/쿠폰/번호 발급
- 이메일/SMS/웹훅 발송 요청
- 외부 시스템에 리소스를 생성하는 POST
- 외부 상태를 irreversible 하게 바꾸는 요청
- timeout 이후 retry 가능성이 높은 provider 호출

Stripe와 PayPal의 공식 idempotency 문서도 이런 POST 중심 side effect 요청을 주된 대상으로 설명한다.

### 4.2 기본 검토 대상이 아닌 경우

프로젝트 규칙:

다음은 outbound idempotency header를 기본값으로 요구하지 않는다.

- 단순 GET 조회
- provider가 이미 HTTP 의미상 idempotent한 PUT/DELETE만 제공하는 경우
- 읽기 전용 상태 확인 API
- side effect가 없는 health/ping/check API

Stripe도 GET/DELETE에는 idempotency key를 보내도 의미가 없다고 안내한다.

## 5. provider key와 내부 key의 관계

### 5.1 provider가 공식 idempotency key를 지원하면 그 계약을 우선 따른다

Stripe는 Idempotency-Key 헤더를, PayPal은 PayPal-Request-Id 헤더를 공식 지원한다. PayPal은 API call type마다 고유해야 한다고도 설명한다. 따라서 provider가 지원하는 공식 키 규약이 있으면 그 규약을 먼저 따른다.

프로젝트 규칙:

- provider 공식 idempotency header가 있으면 그 이름과 제약을 그대로 따른다
- 우리 내부 표준 헤더 이름을 provider에 억지로 강요하지 않는다
- adapter가 provider별 차이를 캡슐화한다

### 5.2 내부 idempotency key와 provider idempotency key는 같을 수도, 다를 수도 있다

IETF 초안과 Stripe 문서는 key를 클라이언트가 생성하는 고유 값으로 설명하지만, 실제 운영에서는 우리 내부 command id 와 provider 전송용 key 를 같은 값으로 쓸지 별도 매핑할지 설계 선택이 있다. PayPal은 API call type 단위 고유성을 요구하므로, 단순히 “사용자 요청 ID 하나”를 모든 provider operation에 그대로 쓰는 방식은 맞지 않을 수 있다.

프로젝트 규칙:

- 내부 command id와 provider key를 1:1로 매핑할 수는 있다
- 하지만 provider가 operation scope를 다르게 요구하면 별도 provider key를 만든다
- 내부 키와 provider 키를 무조건 동일시하지 않는다

권장 예:

- 내부 키: outboundCommandId
- provider 키: (provider, operation, outboundCommandId) 기반 생성

### 5.3 provider key scope는 provider 계약을 따른다

PayPal은 PayPal-Request-Id가 “요청마다 그리고 API call type마다” 고유해야 한다고 설명한다. Stripe도 endpoint와 파라미터가 다르면 idempotency error가 난다고 설명한다. 즉 key scope는 provider마다 다를 수 있다.

프로젝트 규칙:

- 같은 key를 다른 provider operation에 재사용하지 않는다
- 같은 provider라도 다른 endpoint/call type에 key 재사용 여부를 provider 계약 기준으로 판단한다
- scope는 최소한 provider + operation + key 수준으로 본다

## 6. 키 생성 규칙

### 6.1 키는 우리가 생성한다

Stripe는 V4 UUID 또는 충분한 entropy를 가진 랜덤 문자열을 권장하고, 민감정보를 key로 쓰지 말라고 말한다. PayPal도 UUID 사용을 권장한다.

프로젝트 규칙:

- provider key는 우리 서비스가 생성한다
- 권장 형식은 UUID v4 또는 이에 준하는 opaque random string
- 이메일, 전화번호, 사용자명, 주문번호 같은 의미 있는 PII를 key에 넣지 않는다

### 6.2 키는 “같은 외부 요청 의도”에만 재사용한다

Stripe는 동일 key에 대해 원래 요청과 들어온 파라미터를 비교하고, 다르면 에러를 반환한다고 설명한다. 따라서 key는 장기 식별자가 아니라 같은 요청의 재전송용 식별자 여야 한다.

프로젝트 규칙:

- 같은 provider 호출을 다시 보낼 때만 같은 key를 재사용한다
- 요청 의미가 달라지면 새 key를 생성한다
- key를 “사용자별 고정 키”처럼 쓰지 않는다

## 7. 저장 규칙

### 7.1 outbound provider 호출에도 내부적으로 key 매핑 기록을 남긴다

Stripe와 PayPal은 provider 측 idempotency를 제공하지만, 우리 서비스가 timeout/partial failure를 겪었을 때 “이 key로 이미 보냈는가, 응답을 받았는가, 재전송해야 하는가”를 판단하려면 내부 기록이 필요하다. 공식 문서들도 provider가 이전 요청의 결과나 최신 상태를 반환한다고 설명하므로, 우리 쪽에서도 그 연관관계를 추적해야 운영이 가능하다.

프로젝트 규칙:

내부적으로 다음을 기록할 수 있어야 한다

- provider
- operation
- provider idempotency key
- 내부 command id
- request fingerprint
- provider request status(시도 중/완료/최종 실패)
- provider response reference
- provider가 idempotency를 제공해도 우리 내부 기록을 완전히 생략하지 않는다

### 7.2 request fingerprint를 함께 저장한다

Stripe는 같은 key 재사용 시 들어온 파라미터를 원래 요청과 비교해 다르면 에러를 낸다고 설명한다. 우리도 내부적으로 같은 key가 다른 요청 의미로 재사용되지 않았는지 확인할 수 있어야 한다.

프로젝트 규칙:

- 내부 저장소에는 key뿐 아니라 request fingerprint도 함께 둔다
- fingerprint는 provider operation 의미를 기준으로 계산한다
- 같은 key + 다른 fingerprint는 버그 또는 오용으로 본다

## 8. 재전송 규칙

### 8.1 timeout/응답 유실 시에는 같은 key로 재전송한다

Stripe는 네트워크 연결 오류로 응답을 못 받아도 같은 key로 재시도하면 안전하다고 설명한다. PayPal도 동일한 PayPal-Request-Id를 다시 보내면 이전 요청의 최신 상태를 반환한다고 설명한다.

프로젝트 규칙:

- provider에 요청을 보냈지만 응답을 못 받았으면 같은 key 재전송을 기본 검토한다
- 새 key로 다시 보내는 것을 기본값으로 두지 않는다
- 이 판단은 retry/timeout 정책과 함께 묶어서 설계한다

### 8.2 provider가 “실행이 시작되지 않았다”고 말한 경우는 새 시도로 볼 수 있다

Stripe는 validation 실패나 concurrent conflict처럼 endpoint 실행이 시작되지 않은 경우에는 결과를 저장하지 않으며, 이런 경우는 다시 시도할 수 있다고 설명한다.

프로젝트 규칙:

- provider가 execution not started에 해당하는 오류를 명시하면 같은 key 재시도 가능성을 검토한다
- validation 자체가 잘못된 요청이라면 재시도보다 요청 수정이 우선이다
- “실행이 시작되지 않았음”과 “응답만 못 받음”을 구분한다

### 8.3 동시 중복 송신을 피한다

PayPal은 같은 PayPal-Request-Id로 동시에 두 요청을 보내면 첫 번째를 처리하고 두 번째는 실패할 수 있다고 설명한다.

프로젝트 규칙:

- 같은 provider key를 가진 outbound 호출은 동시에 두 개 이상 송신하지 않는다
- 내부적으로 키 단위 동시성 제어를 검토한다
- 같은 command를 여러 worker가 동시에 처리하는 구조라면 key-level dedup/lock을 둔다

## 9. provider 응답 해석 규칙

### 9.1 replay 응답은 새 성공과 같은 의미로 취급하되, 출처는 구분 가능해야 한다

Stripe는 같은 key에 대해 첫 결과의 status와 body를 재사용한다고 설명하고, PayPal은 이전 요청의 최신 상태를 반환한다고 설명한다. 즉, provider가 반환한 응답이 “새로 실행된 결과”인지 “기존 실행의 재생/현재 상태”인지 내부적으로는 구분할 수 있는 편이 좋다.

프로젝트 규칙:

- provider replay 응답도 비즈니스적으로는 성공/실패 결과로 받아들인다
- 다만 내부 observability에는
- new execution
- replayed result
- latest known status
- 를 구분할 수 있게 한다
- 외부 API 응답 body를 우리 내부 의미로 무조건 “새로 생성됨”으로 번역하지 않는다

### 9.2 provider의 “latest status”와 “original result” 차이를 이해한다

PayPal은 이전 요청의 “원래 응답”이 아니라 “현재 시점의 최신 상태”를 반환한다고 설명한다. Stripe는 첫 실행 결과를 재사용하는 쪽에 더 가깝다. provider마다 의미가 다르므로, outbound adapter는 이 차이를 내부로 올바르게 번역해야 한다.

프로젝트 규칙:

- provider replay semantics를 문서화한다
- “같은 key면 항상 동일 body 재생”이라고 일반화하지 않는다
- provider별로
- original response replay
- latest status lookup
- concurrent duplicate failure
- 를 구분한다

## 10. TTL 규칙

### 10.1 provider TTL을 존중한다

Stripe는 키를 최소 24시간 이후 정리할 수 있다고 설명한다. PayPal은 일부 API에서 PayPal-Request-Id 보관 기간이 정해져 있고, 그동안 재시도 가능하다고 설명한다.

프로젝트 규칙:

- provider key TTL은 provider 공식 문서 기준을 따른다
- TTL 내 재전송은 같은 key 사용
- TTL 이후는 새 요청으로 처리될 수 있음을 전제로 한다

### 10.2 내부 기록 TTL은 provider TTL보다 짧게 두지 않는다

프로젝트 규칙:

- 내부 key 매핑 기록 TTL은 provider TTL 이상을 기본 검토한다
- provider는 아직 기억하는데 우리는 잊어버리는 상태를 만들지 않는다
- 최소한 “왜 같은 key가 다시 쓰였는지” 추적 가능한 기간을 확보한다

## 11. observability 규칙

### 11.1 outbound idempotency key는 로그에 원문 전체를 남기지 않는다

Stripe는 key에 민감정보를 넣지 말라고 하지만, 그렇다고 로그에 원문 전체를 항상 남겨도 된다는 뜻은 아니다. 외부 키도 운영 식별자일 뿐 민감도 없는 공개값으로 취급하지 않는다.

프로젝트 규칙:

- provider key 원문 전체 로그를 기본 금지
- 필요하면 prefix 또는 내부 correlation id만 남긴다
- 로그에는
- provider
- operation
- outboundCommandId
- providerRequestId
- 정도의 내부 식별자를 우선 사용한다

### 11.2 replay / duplicate / key mismatch는 관측 가능해야 한다

프로젝트 규칙:

outbound idempotency 관련 운영 이벤트는 최소한 다음을 구분 가능해야 한다

- 새 호출
- 같은 key 재전송
- provider replay 응답
- 같은 key 다른 fingerprint 충돌
- 동시 중복 송신 차단
- retry와 idempotency를 함께 분석할 수 있어야 한다

## 12. 다른 문서와의 경계

이 문서는 outbound idempotency만 다룬다.
아래 주제의 source of truth는 별도 문서다.

- retry
- timeout
- fallback
- serialization/deserialization
- exception translation

이 문서는 위 주제들을 다시 반복하지 않고, 외부 provider idempotency를 어떻게 써야 하는지만 정의한다.

## 13. 금지 규칙

다음은 기본 금지다.

- provider 공식 idempotency key 지원이 있는데 무시하고 새 요청처럼 재전송
- 같은 key를 다른 provider operation에 재사용
- 같은 key를 다른 fingerprint 요청에 재사용
- timeout 후 새 key로 같은 side effect 요청 재전송
- provider key와 내부 command 추적 관계를 저장하지 않음
- 같은 key의 동시 중복 송신 허용
- key에 이메일/전화번호/주문자명 같은 의미 있는 PII 사용
- replay semantics가 다른 provider를 같은 규칙으로 단순화

## 14. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 outbound 호출은 side effect가 있어 idempotency가 필요한가?
- provider가 공식 idempotency key/header를 지원하는가?
- 내부 key와 provider key의 scope가 명확한가?
- timeout/응답 유실 시 같은 key 재전송 전략이 정의되어 있는가?
- 같은 key의 fingerprint 충돌을 감지할 수 있는가?
- provider TTL과 내부 저장 TTL이 정렬되어 있는가?
- replay/new/latest-status semantics를 provider별로 구분하고 있는가?
- retry와 idempotency가 함께 관측 가능한가?
