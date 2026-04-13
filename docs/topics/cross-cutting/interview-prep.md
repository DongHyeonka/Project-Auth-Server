# Interview Prep

## Q1. 왜 Clean Architecture를 적용했나요?

도메인 규칙, HTTP 응답 계약, 외부 기술 구현이 섞이는 것을 막기 위해 적용했습니다.  
특히 인증 서버는 보안 설정과 persistence 구현이 빠르게 커지기 때문에, `domain`, `application`, `presentation`, `infrastructure`, `bootstrap`의 역할을 분리하고 ArchUnit으로 규칙을 검증했습니다.

## Q2. 왜 `bootstrap`을 별도 조립 계층으로 뒀나요?

Spring Boot 설정과 빈 조립 책임을 가장 바깥으로 밀어내기 위해서입니다.  
이렇게 하면 핵심 유스케이스와 도메인 코드는 프레임워크 세부사항과 직접 엮이지 않습니다.

## Q3. Spring Security를 어떻게 사용했나요?

`SecurityFilterChain` 기반으로 공개 라우트와 인증 필요 라우트를 분리했고, OAuth2 로그인 진입점을 Keycloak broker 흐름으로 연결했습니다.  
성공/실패 후처리는 별도 핸들러로 분리해 프로토콜 처리와 애플리케이션 후속 동작을 나눴습니다.

## Q4. Keycloak을 직접 provider 대신 broker로 둔 이유는 무엇인가요?

Google, GitHub 같은 외부 provider별 분기를 애플리케이션 안에 늘리지 않기 위해서입니다.  
애플리케이션은 Keycloak client만 신경 쓰고, provider 등록과 통합은 Keycloak 경계 안으로 밀어 넣는 방향이 유지보수에 유리합니다.

## Q5. Flyway migration을 왜 별도 애플리케이션으로 분리했나요?

migration은 웹 요청 처리와 다른 실패 특성을 가지기 때문입니다.  
`MigrationApplication`을 별도 진입점으로 두면 웹 컨텍스트를 띄우지 않고도 migration만 실행할 수 있어 운영 절차가 명확해집니다.

## Q6. GitOps 구조는 어떻게 설명하실 건가요?

앱 저장소는 소스 코드와 이미지 빌드까지 담당하고, 운영 선언은 `Project-Auth-GitOps`가 source of truth를 가집니다.  
CI가 GHCR에 이미지를 발행한 뒤 GitOps 저장소로 이벤트를 보내고, Argo CD가 원하는 상태를 클러스터에 반영하는 구조입니다.

## Q7. Vault는 왜 붙였나요?

JWT 서명 키를 애플리케이션 내부에 고정하지 않고 Vault Transit으로 분리하려는 목적입니다.  
키 관리 책임을 외부 서비스로 넘기는 대신, 운영 복잡도와 네트워크 의존성을 감수하는 트레이드오프가 있습니다.
