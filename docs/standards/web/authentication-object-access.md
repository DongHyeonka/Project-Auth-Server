# Authentication Object Access 기준

## 1. 목적

이 문서는 인증된 사용자 정보, 주체(principal), 인증 컨텍스트를 어디서 어떻게 접근할지 정의한다.

이 문서의 목표는 다음과 같다.

- controller에서 현재 사용자 접근 방식을 일관되게 만든다
- Spring Security 저수준 타입이 application/domain으로 번지는 것을 막는다
- 인증 객체 접근과 권한 검사 책임을 구분한다
- SecurityContextHolder 직접 접근을 최소화한다

## 2. 근거 수준

- Official: Spring Framework / Spring Security 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 확장 지점 위에 일반적인 실무 구조를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 인증 객체 접근은 web boundary concern이다

Spring Security에서 현재 인증 정보는 SecurityContextHolder의 SecurityContext 안 Authentication으로 관리됩니다. Spring MVC는 Principal을 controller method argument로 지원하고, Spring Security는 @AuthenticationPrincipal과 @CurrentSecurityContext로 그 접근을 더 직접적으로 노출합니다. 이 프로젝트에서는 이를 web boundary concern 으로 본다.

프로젝트 규칙:

- 현재 사용자 접근은 기본적으로 controller/web adapter 경계에서 끝낸다
- application/domain은 “현재 인증 컨텍스트를 조회하는 법”을 몰라야 한다
- 내부 로직에는 필요한 최소 actor 정보만 전달한다

### 3.2 기본 선호는 @AuthenticationPrincipal 기반 전용 현재 사용자 객체다

Spring Security 문서는 @AuthenticationPrincipal을 쓰면 MVC 레이어를 SecurityContextHolder 직접 접근에서 분리할 수 있다고 설명하고, 더 나아가 @CurrentUser 같은 메타 애노테이션으로 Spring Security 의존을 한 파일로 격리하는 방식을 권장 예시로 보여 줍니다.

프로젝트 규칙:

- controller의 기본 인증 객체 접근 방식은 @AuthenticationPrincipal 또는 그 위에 올린 프로젝트 전용 애노테이션
- 프로젝트 기본 애노테이션은 @CurrentUser 또는 이에 준하는 이름을 권장
- controller가 매번 SecurityContextHolder를 직접 읽지 않는다

### 3.3 인가 규칙은 인증 객체 접근 방식과 별개로 다룬다

Spring Security 문서는 요청 매칭 기반 보안 규칙을 일찍 적용하고, 동시에 method security를 함께 두는 defense in depth 를 권장합니다. 따라서 인증 객체를 꺼내는 문제와 권한 검사를 어디서 할지는 분리해서 설계해야 한다.

프로젝트 규칙:

- 인증 객체 접근은 “현재 사용자가 누구인가”의 문제
- 인가 규칙은 “이 사용자가 이 동작을 할 수 있는가”의 문제
- controller 안에서 if (role == ...) 식으로 인가를 기본 구현하지 않는다
- 인가는 security config + method security + application/domain 정책으로 나눈다

## 4. 접근 방식별 규칙

### 4.1 Principal

Spring MVC는 java.security.Principal을 controller method argument로 지원하며, 현재 인증된 사용자를 나타냅니다. Spring Security 환경에서는 Authentication이 Principal이므로 HttpServletRequest#getUserPrincipal() 경유로 주입될 수 있습니다.

프로젝트 규칙:

- 단순히 현재 사용자 이름/식별자 정도만 필요하면 Principal 사용 가능
- 하지만 principal 구현 타입 캐스팅을 기대하는 기본 스타일로는 쓰지 않는다
- Principal은 가장 단순한 읽기 전용 접근에만 쓴다

권장 예:

- /me 같은 endpoint에서 현재 username만 필요한 경우

### 4.2 Authentication

Spring Security의 Authentication은 현재 사용자와 권한 정보를 담는 핵심 타입입니다. @CurrentSecurityContext(expression = "authentication")로도 controller 인자로 받을 수 있습니다.

프로젝트 규칙:

- Authentication은 예외적으로만 controller에서 직접 받는다
- 권한 목록, credentials, details 같은 Security framework 세부정보가 정말 필요할 때만 허용
- 일반 비즈니스 endpoint의 기본 시그니처로 사용하지 않는다

즉, Authentication은 가능하지만 기본값은 아니다.

### 4.3 @AuthenticationPrincipal

Spring Security는 AuthenticationPrincipalArgumentResolver를 제공하고, @EnableWebSecurity를 쓰면 이를 MVC에 자동 추가합니다. 이 애노테이션은 Authentication.getPrincipal()을 controller method argument로 직접 받게 해 줍니다.

프로젝트 규칙:

- 현재 사용자 객체 접근의 기본값은 @AuthenticationPrincipal
- 단, controller 시그니처가 Spring Security 애노테이션에 직접 결합되는 것이 싫다면 메타 애노테이션으로 감싼다
- controller는 principal 내부 구조를 깊게 탐색하기보다 필요한 전용 타입을 주입받는다

### 4.4 프로젝트 전용 @CurrentUser 메타 애노테이션

Spring Security 문서는 @AuthenticationPrincipal을 감싼 @CurrentUser 메타 애노테이션 예시를 직접 제공하고, 이렇게 하면 MVC 레이어의 Spring Security 의존을 한 파일로 격리할 수 있다고 설명합니다. 또한 expression을 통해 JWT claim 같은 값만 바로 꺼내는 방식도 예시로 보여 줍니다.

프로젝트 규칙:

- 프로젝트 기본 방식은 @CurrentUser
- @CurrentUser는 @AuthenticationPrincipal의 메타 애노테이션으로 구현
- 필요하면 expression 기반으로 userId, subject, claims['sub'] 같은 값만 주입하는 파생 애노테이션도 허용

권장 방향:

- @CurrentUser AuthenticatedUser currentUser
- 또는 @CurrentUserId String userId

### 4.5 @CurrentSecurityContext

Spring Security는 @CurrentSecurityContext로 SecurityContext 또는 Authentication을 controller method argument로 직접 주입할 수 있게 지원합니다.

프로젝트 규칙:

- @CurrentSecurityContext는 예외적 escape hatch
- 일반 endpoint의 기본 접근 방식으로 사용하지 않는다
- security context 전체가 필요한 framework-adjacent endpoint에서만 제한적으로 허용한다

예:

- 디버그/진단 endpoint
- 보안 관련 내부 운영 endpoint

## 5. 계층별 규칙

### 5.1 Controller

프로젝트 규칙:

- controller는 인증 객체를 전용 현재 사용자 타입 또는 최소 식별자로 받는다
- controller가 SecurityContextHolder를 직접 조회하지 않는다
- controller는 principal에서 필요한 최소 정보만 추출해 application command/use case에 전달한다
- controller가 Authentication, SecurityContext, UserDetails를 그대로 내부로 넘기지 않는다

### 5.2 Application

프로젝트 규칙:

- application service/use case는 Spring Security 타입을 모른다
- 입력으로는 actorId, actorRoleSet, tenantId 같은 의미 있는 값만 받는다
- “현재 로그인 사용자 조회”를 application 내부에서 직접 하지 않는다

즉, application은 현재 사용자가 누구인지가 아니라, 호출 주체가 누구라고 전달받았는지만 다룬다.

### 5.3 Domain

프로젝트 규칙:

- domain은 Spring Security 의존을 가지지 않는다
- domain 객체/도메인 서비스/값 객체가 Authentication, Principal, UserDetails를 참조하지 않는다
- 도메인 규칙이 호출 주체를 필요로 하면 명시적 값(ActorId, ActorType)으로 전달한다

### 5.4 Infrastructure / Security Adapter

프로젝트 규칙:

- Spring Security principal 구성, claim 해석, JWT → 현재 사용자 변환은 infrastructure/security adapter에서 담당한다
- principal 구현체, converter, resolver, 인증 토큰 해석 로직은 이 계층에 모은다
- web/business 계층이 JWT claim 구조를 직접 파싱하지 않는다

## 6. 현재 사용자 타입 규칙

### 6.1 AuthenticatedUser 같은 전용 타입을 둔다

Spring Security는 principal 타입을 자유롭게 둘 수 있고, @AuthenticationPrincipal은 그 principal을 그대로 주입할 수 있습니다. 이 프로젝트에서는 controller용 인증 객체를 프로젝트 전용 타입 으로 두는 방식을 기본 권장한다.

프로젝트 규칙:

- 전용 타입 예: AuthenticatedUser
- 최소 권장 필드 예:
- userId
- authorities 또는 역할 집합
- tenantId(필요 시)
- password, credentials, provider-specific raw claim map을 기본 공개 필드로 두지 않는다

### 6.2 전용 타입은 “비즈니스에 필요한 최소 정보”만 담는다

프로젝트 규칙:

- 현재 사용자 타입은 보안 프레임워크 내부 표현이 아니다
- JWT 전체 claims map, raw token string, authentication details를 무비판적으로 싣지 않는다
- 컨트롤러/유스케이스가 자주 필요로 하는 값만 담는다

## 7. 전달 규칙

### 7.1 application에는 최소 actor 정보만 넘긴다

프로젝트 규칙:

- AuthenticatedUser 전체를 application에 넘기는 것도 기본적으로 지양
- 더 선호하는 것은 command/query 생성 시 필요한 최소 값만 복사하는 방식

예:

- CreateSessionCommand(actorId, email, password)
- ChangePasswordCommand(actorId, currentPassword, newPassword)

### 7.2 principal을 전역 static 접근으로 다시 조회하지 않는다

Spring Security에서 현재 인증은 SecurityContextHolder에 저장되지만, 그 저장소가 있다는 사실이 곧 아무 계층에서나 static 접근으로 꺼내 써도 된다는 뜻은 아닙니다.

프로젝트 규칙:

- service/domain/util에서 SecurityContextHolder.getContext() 직접 호출 금지
- “현재 사용자 필요”는 메서드 인자로 드러나야 한다
- 숨겨진 전역 의존성을 만들지 않는다

## 8. 권한 검사 규칙

### 8.1 권한 검사는 security rule + method security를 우선한다

Spring Security는 요청 매칭 기반 보안과 method security를 함께 두는 defense in depth를 권장합니다.

프로젝트 규칙:

- 역할/권한 검사는 기본적으로 security config 또는 method security에서 처리
- controller 안의 imperative role check를 기본 금지
- application/domain에서 추가 business authorization이 필요하면 명시적 정책으로 구현한다

### 8.2 “현재 사용자와 리소스 소유자 비교”는 business rule일 수 있다

프로젝트 규칙:

- 단순 권한(ROLE_ADMIN 등)은 security rule에 두는 쪽을 우선
- “현재 사용자 ID와 리소스 owner가 같은가” 같은 규칙은 application/domain 정책일 수 있다
- 이 경우에도 현재 사용자 정보는 최소 actor 값으로 전달한다

## 9. 테스트 규칙

### 9.1 controller 테스트는 프로젝트 전용 현재 사용자 접근을 기준으로 짠다

프로젝트 규칙:

- 테스트도 @CurrentUser 또는 프로젝트 principal 타입 기준으로 작성한다
- 테스트 때문에 production code가 raw Authentication에 과도하게 결합되지 않게 한다
- 보안 프레임워크 타입보다 프로젝트의 현재 사용자 계약을 검증한다

## 10. 금지 규칙

다음은 기본 금지다.

- controller에서 SecurityContextHolder 직접 조회
- controller 시그니처에 raw Authentication 남발
- application/domain/service에서 Spring Security 타입 직접 사용
- service/util에서 전역 static 방식으로 현재 사용자 조회
- JWT claims/raw token을 여러 계층에서 직접 파싱
- controller 안에서 if (role == ...) 식 인가 로직 구현
- principal 구현체를 persistence/domain 모델로 겸용 사용

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 현재 사용자 접근이 controller/web 경계에 머무르는가?
- 기본 접근 방식이 @CurrentUser 또는 이에 준하는 전용 애노테이션인가?
- Spring Security 타입이 application/domain으로 번지지 않는가?
- 유스케이스에는 필요한 최소 actor 정보만 전달되는가?
- 권한 검사가 controller imperative code가 아니라 보안 규칙/정책으로 표현되는가?
