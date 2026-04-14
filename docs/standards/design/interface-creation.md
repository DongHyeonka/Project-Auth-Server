# interface 생성 기준

## 목적

인터페이스는 “있으면 좋아 보이는 추상화”가 아니라, **경계와 계약을 안정적으로 표현해야 할 때만** 만든다.  
기본값은 “인터페이스를 무조건 만들지 않는다”이다.

## 공식 의미

- 인터페이스는 구현과 분리된 계약(contract)을 표현하는 타입이다.
- 구현체는 인터페이스가 정한 메서드 계약을 따른다.
- 인터페이스는 추상 메서드, default method, static method, 상수를 가질 수 있다.
- Spring DI는 의존 객체를 생성자/팩토리 메서드/세터를 통해 주입하며, 인터페이스나 추상 베이스 타입에 의존할 때 결합도가 낮아지고 테스트 대역 사용이 쉬워진다.
- Spring AOP는 대상이 인터페이스를 구현하면 JDK dynamic proxy를 기본으로 사용한다.

## 기본 규칙

### 1. 기본값은 “필요할 때만 만든다”
다음 중 하나가 아니라면 인터페이스를 만들지 않는다.

- 모듈/레이어 경계를 표현해야 한다
- 교체 가능한 구현이 실제로 존재하거나 가까운 미래에 예상된다
- 테스트에서 대역(stub/mock/fake)으로 치환하는 가치가 크다
- 프레임워크/프록시/AOP/플러그인 구조상 계약 타입이 분명히 필요하다
- 라이브러리/외부 모듈에 공개할 안정된 API 계약이 필요하다

### 2. “구현체 1개” 자체는 금지 근거가 아니지만, “이유 없는 인터페이스”는 금지
구현체가 1개여도 아래 중 하나면 인터페이스를 둘 수 있다.

- application port
- 외부 연동 client contract
- repository-like boundary
- 인증/토큰/암호화 같은 교체 가능한 정책

반대로 구현체가 1개이고 아래도 아니면 인터페이스를 만들지 않는다.

- 내부 helper/service
- 단순 orchestration class
- 프레임워크가 요구하지 않는 내부 컴포넌트

### 3. 레이어 경계는 인터페이스를 우선 검토
특히 다음 경계는 인터페이스를 우선 검토한다.

- application `port/in`
- application `port/out`
- infrastructure adapter가 구현하는 계약
- 외부 시스템 client contract
- 교체 가능한 정책 객체

즉 “안쪽이 바깥 구현을 모르면 좋은 곳”은 인터페이스 후보가 된다.

### 4. 내부 구현 디테일에는 기본적으로 인터페이스를 만들지 않는다
같은 모듈 내부에서만 쓰이고, 교체 가능성도 낮고, 테스트 seam 가치도 낮은 클래스는 concrete class 그대로 둔다.

금지 예:
- `UserService` + `UserServiceImpl`
- `EmailNormalizer` + `EmailNormalizerImpl`
- `AuthFacade` + `AuthFacadeImpl`

단, 정말 계약 타입이 먼저이고 구현이 뒤따르는 구조면 예외다.

### 5. 인터페이스는 “역할”을 표현해야 한다
인터페이스 이름은 구현 방식이 아니라 역할/능력을 드러내야 한다.

좋은 방향:
- `UserReader`
- `PasswordHasher`
- `JwtSigner`
- `TokenIssuer`
- `UserRepository`
- `OAuthClient`

지양:
- `DefaultUserService`
- `CommonManager`
- `BaseHandler`

### 6. 인터페이스는 작고 응집도 있게 유지
인터페이스는 하나의 역할/계약에 집중해야 한다.

금지:
- unrelated method를 한 인터페이스에 몰아넣기
- “편해서” 여러 책임을 한 계약에 합치기
- consumer마다 일부만 필요한 fat interface

### 7. 인터페이스는 구현 세부보다 호출 계약을 고정
인터페이스는 아래를 고정해야 한다.

- 어떤 입력을 받는가
- 어떤 결과를 돌려주는가
- 어떤 예외/실패 의미가 가능한가
- 어떤 side effect/보장이 있는가

반대로 아래는 인터페이스에 새지 않게 한다.

- HttpClient/WebClient/JPA/Redis/Jackson 등 기술 세부
- 프레임워크 구체 타입
- 구현체 내부 최적화 방식

### 8. default method는 “계약 핵심”보다 “하위 호환/작은 공통 동작”에만 제한
Oracle 문서상 default method는 기존 구현과의 binary compatibility를 유지하면서 기능을 추가할 수 있다.  
프로젝트에서는 아래일 때만 제한적으로 허용한다.

- 라이브러리/공용 계약의 하위 호환이 중요하다
- 매우 작은 convenience 동작이다
- 구현체 대부분에 동일하게 자연스럽다

기본값은 추상 메서드다.  
비즈니스 핵심 로직을 default method로 밀어 넣지 않는다.

### 9. 인터페이스 static method는 그 계약에만 밀접한 helper일 때만
Oracle 문서상 인터페이스는 static method를 가질 수 있다.  
프로젝트에서는 그 helper가 해당 인터페이스 계약과 아주 밀접할 때만 허용한다.

그 외 일반 helper는 별도 타입/유틸로 분리한다.

### 10. 인터페이스 상수 남용 금지
인터페이스는 상수 묶음 용도로 만들지 않는다.  
상수는 계약의 본질이 아닐 경우 별도 적절한 소유 타입에 둔다.

### 11. Spring 프록시/AOP 때문에 인터페이스를 만들 수는 있지만, 그 이유를 과장하지 않는다
Spring은 인터페이스가 있으면 JDK dynamic proxy를 기본으로 사용한다.  
하지만 “프록시 가능”만으로 모든 클래스 앞에 인터페이스를 두지 않는다.

기본 판단 순서:
1. 이 타입이 경계/계약인가?
2. 교체/테스트/AOP 가치가 있는가?
3. concrete class로 두는 것이 더 단순한가?

### 12. 테스트를 위해서만 인터페이스를 남발하지 않는다
Spring DI 문서는 인터페이스/추상 베이스 타입이 테스트 대역 사용을 쉽게 한다고 설명한다.  
하지만 “테스트가 쉬워 보인다”는 이유만으로 의미 없는 인터페이스를 만들지 않는다.

다음도 대안이 될 수 있다.
- package-private concrete class 테스트
- 더 작은 collaborator 분리
- test fixture/fake 구현
- 포트 레벨에서만 seam 만들기

### 13. public API / multi-module contract는 인터페이스 우선 검토
다른 모듈/패키지/팀이 사용할 public contract면 인터페이스를 우선 검토한다.  
이 경우 호출자와 구현체를 느슨하게 분리할 가치가 크다.

### 14. 조기 추상화 금지
겉보기 유사성만 보고 인터페이스를 먼저 만들지 않는다.

다음 질문 중 “예”가 충분히 쌓일 때 만든다.
- 정말 다른 구현이 필요한가?
- 호출자가 구현이 아니라 계약에 의존해야 하는가?
- 이 추상화가 6개월 뒤에도 자연스러운가?
- 이 인터페이스가 테스트/교체/경계 보호에 실제 도움 되는가?

## 프로젝트 기준 요약

- 인터페이스 기본값은 “필요할 때만”
- application port / 외부 경계 / 교체 정책은 인터페이스 우선
- 내부 helper/orchestration에는 기본적으로 인터페이스 금지
- `XService` + `XServiceImpl` 자동 생성 금지
- 역할 중심 이름 사용
- 기술 세부를 계약에 노출하지 않음
- default/static method는 제한적으로만 허용
- 프록시 가능성만으로 인터페이스를 만들지 않음
- 조기 추상화 금지
