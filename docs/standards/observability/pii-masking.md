# PII Masking 기준

## 1. 목적

이 문서는 로그, 트레이스, 운영 이벤트 기록에서 개인정보(PII)와 민감정보를 어떻게 마스킹하거나 제거할지 정의한다.

이 문서의 목표는 다음과 같다.

- 운영에 필요한 관측 가능성을 유지하면서 민감정보 노출을 막는다
- 로그 레벨과 무관하게 보호해야 하는 데이터를 명확히 한다
- 마스킹 책임을 개별 개발자 습관이 아니라 공통 규칙으로 만든다
- request/response, exception, external API payload, structured logging 모두에 같은 기준을 적용한다

## 2. 근거 수준

- Official: OWASP, Spring Boot 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 가이드 위에 일반적인 운영/보안 관행을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 민감정보 보호는 예외가 아니라 기본값이다

OWASP는 세션 식별값, 액세스 토큰, 민감한 개인정보, 비밀번호, DB 연결 문자열, 암호화 키, 결제 데이터 등은 로그에 직접 기록하지 말고 제거·마스킹·정제·해시·암호화하라고 권고합니다. 따라서 이 프로젝트에서는 “필요할 때만 숨긴다”가 아니라 기본적으로 숨기고, 꼭 필요한 최소 정보만 남긴다를 원칙으로 둔다.

프로젝트 규칙:

- 민감정보는 기본 비기록(non-log) 이 원칙
- 필요하면 원문 대신 마스킹/해시/요약값을 남긴다
- “디버깅 중이라서”, “DEBUG니까”, “내부망이라서” 같은 이유로 예외를 만들지 않는다

### 3.2 로그 레벨이 낮다고 민감정보를 찍어도 되는 것은 아니다

OWASP는 민감정보 자체를 직접 기록하지 말라고 하며, 이는 특정 로그 레벨에 한정된 예외를 두지 않습니다. 이 프로젝트도 DEBUG/TRACE를 보안 예외 구간으로 취급하지 않는다.

프로젝트 규칙:

- INFO, DEBUG, TRACE 모두 같은 마스킹 규칙을 따른다
- 낮은 레벨은 더 자세할 수 있을 뿐, 더 위험한 데이터를 허용하는 레벨이 아니다

### 3.3 민감정보 마스킹은 로깅 호출부와 공통 로깅 파이프라인이 함께 책임진다

Spring Boot는 MDC를 로그 패턴과 structured logging JSON에 포함할 수 있고, JSON 필드의 include/exclude/rename/add와 customizer도 지원합니다. 따라서 민감정보 보호는 “개발자가 매번 조심해서 안 찍는 것”만으로 끝내지 않고, 공통 로깅 구성을 통한 2차 방어선도 둘 수 있다.

프로젝트 규칙:

- 1차 방어: 애플리케이션 코드에서 원문을 로그 인자로 넘기지 않는다
- 2차 방어: 공통 로깅 설정/structured logging customizer/appender 등에서 추가 마스킹을 검토한다
- 둘 중 하나만 믿지 않는다

## 4. 데이터 분류 규칙

### 4.1 절대 원문 기록 금지 데이터

OWASP 기준으로 직접 기록하지 말아야 할 대표 데이터는 다음과 같습니다. 세션 식별값, 액세스 토큰, 민감한 개인정보와 일부 PII, 인증 비밀번호, DB 연결 문자열, 암호화 키 및 주요 비밀값, 은행 계좌/카드 정보 등이 여기에 해당합니다.

프로젝트 규칙:

다음은 원문 로그 금지 다.

- 비밀번호
- access token / refresh token / bearer token
- session id / cookie session value / JWT raw token
- DB connection string
- encryption key / signing key / secret key / API secret
- 카드번호 / 계좌번호 / 결제 식별정보 원문
- 주민등록번호/여권번호/정부 식별번호류
- 건강정보, 법적으로 민감한 개인정보
- 외부 시스템 인증 헤더 원문
- source code, stack dump 속 비밀 설정값

### 4.2 기본적으로 직접 기록하지 않는 데이터

OWASP는 민감한 개인정보 외에도 personal names, telephone numbers, email addresses, internal network names/addresses, file paths 등은 특별한 취급이 필요할 수 있다고 설명합니다. 이 프로젝트에서는 다음 데이터도 원문 기록을 기본값으로 두지 않는다.

프로젝트 규칙:

다음은 원문 비권장 이며, 필요 시 축약/부분 마스킹/내부 식별자 대체를 우선한다.

- 이메일 전체값
- 전화번호 전체값
- 개인 이름 전체값
- 내부 IP/호스트명/내부 네트워크 이름
- 로컬 파일 경로
- 상세 주소
- 주민등록번호 일부를 유추할 수 있는 조합값

### 4.3 운영에 필요한 식별자는 내부 식별자를 우선한다

OWASP는 direct/indirect identifier에 대해 de-identification을 검토하라고 권고합니다. 이 프로젝트에서는 사람이 직접 식별되는 값보다 내부 식별자를 우선 남기는 방향을 기본으로 한다.

프로젝트 규칙:

- email 대신 userId
- accountNumber 대신 accountId
- phoneNumber 대신 customerId
- 꼭 외부 식별자가 필요하면 일부만 남긴다

## 5. 처리 방식 규칙

### 5.1 제거, 마스킹, 해시, 암호화의 기본 선택

OWASP는 민감값을 제거, 마스킹, 정제, 해시, 암호화 중 적절한 방식으로 처리하라고 설명합니다. 이 프로젝트는 다음 우선순위를 권장한다.

프로젝트 규칙:

- 가장 먼저 검토: 아예 기록하지 않기
- 운영 식별이 필요: 부분 마스킹
- 동일 값 상관관계만 필요: 해시/토큰화
- 정말 복구 가능한 보관이 필요: 별도 보호 저장소를 검토하고 일반 애플리케이션 로그에는 두지 않는다

### 5.2 부분 마스킹 규칙

프로젝트 권장 예:

- 이메일: do***@example.com
- 전화번호: 010-****-1234
- 카드번호: ************1234
- 계좌번호: ******7890
- 주민번호류: 기본 로그 금지, 부분 마스킹도 최소화
- 토큰/세션 ID: 원문 금지, 필요하면 앞/뒤 일부 + 해시 일부

### 5.3 해시 사용 규칙

프로젝트 규칙:

- 같은 값의 반복 여부만 추적하면 될 때 해시 사용 가능
- salt/secret이 필요한 경우 보안 정책을 따른다
- 해시값도 장기 식별자로 과도하게 남용하지 않는다
- 원문 복구가 필요한 요구를 해시로 해결하려 하지 않는다

## 6. 위치별 규칙

### 6.1 request logging

OWASP는 HTTP request body, response body, headers 같은 확장 상세 정보는 민감할 수 있으므로 특별한 주의가 필요하다고 설명합니다. 이 프로젝트에서는 request logging에서 다음을 기본 금지한다.

프로젝트 규칙:

- request body 전문 로그 금지
- Authorization 헤더 원문 금지
- Cookie 헤더 원문 금지
- query string 전체 로그 금지
- multipart 파일명/본문 원문 금지

허용 가능한 예:

- requestPath
- method
- contentType
- payloadBytes
- allowlist된 소수의 비민감 query field

### 6.2 response logging

프로젝트 규칙:

- response body 전문 로그 금지
- token 발급 응답, 인증 응답, 결제 응답 원문 금지
- 상태코드, duration, 외부 시스템 상태, 응답 크기 같은 메타데이터만 우선 기록한다

### 6.3 exception logging

OWASP는 stack trace, system error messages, debug information, request/response body 등이 민감해질 수 있다고 설명합니다. 따라서 예외 로그는 stack trace를 남기더라도 민감 데이터를 포함한 message/context를 같이 남기지 않도록 조심해야 한다.

프로젝트 규칙:

- 예외 메시지에 포함된 민감정보 원문을 그대로 로그 제목에 사용하지 않는다
- log.error(ex.getMessage(), ex)를 기본값으로 두지 않는다
- 사건 설명 + 안전한 식별자 + 예외 객체 순으로 기록한다
- stack trace에 비밀값이 포함될 위험이 있는 경로는 별도 검토한다

### 6.4 external API / integration logging

프로젝트 규칙:

- 외부 요청/응답 payload 전문 로그 금지
- 외부 API key, bearer token, cookie 원문 금지
- provider 이름, endpoint path, status, duration, request id 같은 메타데이터만 우선 기록
- third-party error body도 민감정보를 포함할 수 있으므로 원문 그대로 남기지 않는다

### 6.5 audit/security logging

OWASP는 보안/감사 로그도 중요하지만, 그렇다고 민감값을 그대로 기록하라는 뜻은 아니라고 설명합니다. “무엇이 일어났는지”는 남기되, 값 원문은 보호해야 한다.

프로젝트 규칙:

- “비밀번호 변경 시도”, “토큰 재발급 요청”, “민감 데이터 조회” 같은 사건 자체는 기록
- 하지만 실제 비밀번호, 토큰, 데이터 원문은 기록하지 않는다
- audit log가 필요하면 일반 운영 로그와 목적을 구분한다

## 7. structured logging / MDC 규칙

### 7.1 MDC에 넣는 값도 같은 기준으로 마스킹한다

Spring Boot는 MDC 값을 로그 패턴과 structured JSON에 포함할 수 있고, ECS/GELF/Logstash 포맷에서도 MDC key-value가 JSON에 들어갑니다. 따라서 MDC는 안전한 공통 필드만 넣는 용도로 써야 한다.

프로젝트 규칙:

- MDC에는 traceId, requestPath, actorId, operation, clientIp(필요 시) 정도의 안전한 메타데이터만 둔다
- MDC에 token, email 전체값, session id, raw principal object를 넣지 않는다
- “MDC니까 괜찮다”는 예외를 두지 않는다

### 7.2 structured logging JSON 필드도 마스킹 정책 대상이다

Spring Boot는 structured JSON에서 include/exclude/rename/add, customizer를 지원합니다. 이는 JSON 로그가 plain text보다 안전하다는 뜻이 아니라, 같은 보호 규칙을 중앙에서 적용할 수 있다는 뜻에 가깝다.

프로젝트 규칙:

- structured logging을 쓰면 JSON field 수준 마스킹/제외 규칙을 검토한다
- 수집 시스템에 맞춘 필드 rename은 허용하지만, 민감 필드 유입 자체를 방지하는 것이 우선이다
- ingestion 단계에서 추가 마스킹이 가능해도 애플리케이션 단계의 원문 출력 금지를 대체하지 않는다

## 8. 로그 인젝션 방지 규칙

### 8.1 외부 입력은 로깅 전에 sanitization을 고려한다

OWASP는 다른 trust zone에서 들어오는 이벤트 데이터는 형식 검증을 하고, CR/LF 및 delimiter 같은 문자를 정제해 로그 인젝션을 막으라고 권고합니다.

프로젝트 규칙:

- 사용자 입력, 외부 시스템 응답, 헤더, query parameter를 로그에 넣기 전 길이/형식 검토
- CR/LF, 탭, 구분자, 제어문자 정제
- multi-line injection이 가능한 원문을 그대로 로그 메시지에 넣지 않는다

### 8.2 예외 메시지와 외부 오류 메시지도 신뢰하지 않는다

프로젝트 규칙:

- 외부 시스템의 에러 메시지를 그대로 로그 제목에 사용하지 않는다
- 예외 메시지를 한 줄 요약/정제 후 보조 정보로만 사용한다
- 로그 제목은 애플리케이션이 통제하는 문장으로 쓴다

## 9. 구현 규칙

### 9.1 공통 마스킹 유틸/컴포넌트를 둔다

프로젝트 규칙:

- 이메일, 전화번호, 토큰, 계좌/카드 마스킹은 공통 유틸이나 formatter로 제공한다
- 서비스마다 제각각 다른 마스킹 패턴을 쓰지 않는다
- 로그 메시지 안에서 직접 substring으로 잘라 쓰는 임시 구현을 줄인다

### 9.2 logger 호출 전에 안전한 값으로 변환한다

프로젝트 규칙:

- log.info("...", rawToken) 금지
- log.info("...", maskedToken(rawToken)) 형태로만 허용
- 가능하면 값을 마스킹한 뒤 변수에 담아 의미 있는 이름으로 사용한다

### 9.3 가능한 경우 중앙 로깅 구성에서 2차 필터링을 둔다

Spring Boot는 structured logging JSON customizer와 MDC 기반 필드 구성을 지원합니다. 이를 이용해 민감 필드가 특정 이름으로 유입될 경우 제거/대체하는 2차 방어선을 둘 수 있다.

프로젝트 규칙:

- authorization, accessToken, password, sessionId 같은 공통 금지 키는 중앙 필터링을 검토한다
- 다만 중앙 필터링이 있으니 코드에서 원문을 찍어도 된다는 뜻은 아니다

## 10. 테스트/검증 규칙

### 10.1 로그에 금지 데이터가 실제로 남지 않는지 검증한다

OWASP는 로깅 메커니즘의 설계/구현/검증을 강조합니다. 이 프로젝트도 마스킹 정책을 문서만 두지 않고 테스트/리뷰 대상으로 본다.

프로젝트 규칙:

- 주요 인증/결제/개인정보 흐름은 로그 검증 테스트를 둘 수 있다
- 보안 리뷰 체크리스트에 “민감정보 로그 노출 여부”를 포함한다
- 샘플 로그/운영 로그 점검으로 정책 위반을 찾아낸다

### 10.2 규칙 위반은 기능 버그가 아니라 보안 버그로 취급한다

프로젝트 규칙:

- 토큰/비밀번호/세션 ID/PII 원문 노출은 보안 결함으로 분류
- “로그일 뿐”이라고 축소하지 않는다
- 수정 우선순위를 높게 둔다

## 11. 금지 규칙

다음은 기본 금지다.

- 비밀번호 원문 로그
- access token / refresh token / session id / cookie 값 원문 로그
- Authorization 헤더 원문 로그
- request/response body 전문 로그
- 이메일/전화번호/이름 등 개인식별자 전체값 무비판적 로그
- 외부 API secret, DB connection string, key material 로그
- DEBUG/TRACE라는 이유로 민감정보 예외 허용
- MDC/structured JSON에 민감정보 적재
- CR/LF 등 제어문자 포함 원문을 그대로 로그에 기록

## 12. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 값은 정말 로그에 남겨야 하는가?
- 원문 대신 내부 식별자/마스킹/해시로 충분하지 않은가?
- 토큰, 세션 ID, 비밀번호, key material, 결제정보가 원문으로 남지 않는가?
- request/response 전문을 기본 로그에 넣고 있지 않은가?
- MDC와 structured logging 필드에도 같은 보호 규칙이 적용되는가?
- 외부 입력을 로그에 넣기 전에 sanitization을 검토했는가?
- 공통 마스킹 유틸/구성과 코드 레벨 금지 규칙이 함께 적용되는가?
