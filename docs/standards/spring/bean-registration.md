# bean registration 기준

## 목적

Spring bean 등록은 “컨테이너가 생명주기와 의존성을 관리해야 하는 객체”에만 사용한다.  
아무 객체나 bean으로 올리지 않고, stereotype scanning과 `@Configuration` + `@Bean`을 역할에 따라 구분한다.

## 공식 의미

- Spring IoC container는 configuration metadata를 읽어 bean definition을 만들고 객체를 관리한다.
- 설정 메타데이터는 주로 annotation-based component class, `@Configuration` + `@Bean`, 또는 외부 설정으로 표현할 수 있다.
- `@Component`와 그 특수화(`@Repository`, `@Service`, `@Controller`)는 classpath scanning 대상이다.
- `@Bean`은 객체를 생성·설정·초기화하는 factory method를 bean definition으로 등록한다.
- `@Bean`은 `@Configuration` 클래스에서 사용하는 것이 기본 권장 방식이다.
- bean overriding은 일반적으로 권장되지 않으며 설정 가독성을 해친다.
- bean/runtime registration은 초기 단계가 아니라 live access 중에는 공식적으로 지원되지 않는다.
- 일반적으로 fine-grained domain object는 Spring container에 등록하지 않는다.

## 기본 규칙

### 1. bean은 “컨테이너 관리 가치”가 있는 객체만 등록
다음은 bean 등록 후보이다.

- application service / use case entry object
- repository adapter
- external API client
- configuration / security / filter / interceptor
- shared infrastructure object
- framework integration object

다음은 기본적으로 bean 등록하지 않는다.

- domain entity
- value object
- request / response DTO
- command / result DTO
- 단순 임시 helper object
- 매 요청/매 호출마다 새로 만들어도 되는 순수 data object

### 2. 애플리케이션 주 컴포넌트는 stereotype annotation 우선
다음은 stereotype을 우선 검토한다.

- `@Service`
- `@Repository`
- `@Controller` / `@RestController`
- generic component면 `@Component`

즉 프로젝트 코드 안의 “주된 역할 객체”는 scanning 기반 등록을 기본값으로 한다.

### 3. `@Bean`은 명시적 조립이 필요할 때 사용
다음은 `@Configuration` + `@Bean`을 우선 검토한다.

- 외부 라이브러리 타입 등록
- 생성자가 복잡하거나 factory method가 필요한 경우
- 조건부 조립이 필요한 경우
- 여러 collaborator를 엮어 명시적으로 wiring해야 하는 경우
- infrastructure object / client / encoder / formatter / strategy bean 생성
- 같은 config 안에서 inter-bean wiring을 명시적으로 보여주고 싶은 경우

### 4. `@Bean`은 기본적으로 `@Configuration` 안에서만
`@Bean` method는 기본적으로 `@Configuration` 클래스 안에 둔다.

이유:
- full configuration mode가 inter-bean dependency를 더 안전하게 다룬다
- lite mode의 subtle bug 가능성을 줄일 수 있다

기본 금지:
- 일반 `@Component` 안에 습관적으로 `@Bean` method 두기

예외:
- 아주 제한된 factory-style component가 필요하고, inter-bean dependency 호출을 하지 않는 경우

### 5. bean 등록 이유가 이름만 보고 드러나야 한다
- scanning bean이면 stereotype이 역할을 드러내야 한다
- `@Configuration` 클래스는 조립 목적이 이름에 드러나야 한다

좋은 방향:
- `SecurityConfiguration`
- `WebConfiguration`
- `VaultClientConfiguration`
- `OAuth2SecurityConfiguration`

지양:
- `CommonConfig`
- `AppBeans`
- `GeneralConfiguration`

### 6. domain object를 bean으로 등록하지 않는다
Spring 공식 문서도 fine-grained domain object는 보통 container가 아니라 repository/business logic이 만들고 로드한다고 설명한다.

기본 금지:
- `User`, `Money`, `UserEmail`, `CreateUserCommand`를 bean으로 등록
- domain 생성 책임을 container로 넘기기

### 7. bean 이름은 기본 규칙을 따르고, 명시적 이름은 정말 필요할 때만
Spring은 scanning bean의 이름을 일반적으로 decapitalize된 simple class name으로 만든다.

기본:
- 이름 충돌이 없으면 기본 이름 사용
- qualifier/alias/explicit name은 실제 필요가 있을 때만 사용

무분별한 명시적 이름 지정 지양:
- `"mySpecialUserServiceBean"`
- `"appMainPrimaryService"`

### 8. bean overriding 기본 금지
같은 이름의 bean을 덮어쓰는 방식으로 조립하지 않는다.

이유:
- 설정 가독성이 나빠진다
- 어떤 bean이 실제로 쓰이는지 추적이 어려워진다

테스트에서만 예외적으로 필요하면 별도 테스트 설정/지원 메커니즘을 사용한다.

### 9. 런타임 동적 bean 등록 금지
애플리케이션 실행 중 live container에 새 bean을 동적으로 등록하는 방식은 기본 금지한다.

기본:
- bean definition은 startup 시점에 확정
- 동적 확장이 필요하면 registry/plugin/factory 전략을 따로 설계

### 10. bean은 역할 단위로 등록하고, 잡동사니 helper를 bean으로 올리지 않는다
container가 관리할 필요가 없는 순수 helper는 bean 대신:
- static utility
- package-private helper
- mapper instance
- plain object 생성
을 우선 검토한다.

### 11. configuration class는 “조립”만 하고 business logic은 넣지 않는다
`@Configuration` 클래스는 bean wiring과 설정 소유만 담당한다.

금지:
- 비즈니스 흐름
- 상태 전이
- 외부 호출 오케스트레이션
- 의미 있는 계산 로직

### 12. stereotype는 의미에 맞게 쓴다
- persistence adapter면 `@Repository`
- use case/application service면 `@Service`
- web adapter면 `@Controller` / `@RestController`
- 그 외 일반 Spring-managed component면 `@Component`

의미 없는 전부 `@Component` 관성 사용은 지양한다.

### 13. public API 역할이 없는 내부 구현은 과도한 bean 분해를 피한다
container bean 수를 늘리는 것이 곧 좋은 설계는 아니다.

질문:
- lifecycle 관리가 필요한가?
- 외부에서 주입받아야 하는가?
- 테스트 seam 가치가 있는가?
- 명시적 wiring으로 읽기 쉬워지는가?

아니면 plain object가 더 낫다.

## 프로젝트 기준 요약

- bean은 컨테이너 관리 가치가 있는 객체만 등록
- application 주 컴포넌트는 stereotype scanning 우선
- 외부 라이브러리/명시적 조립은 `@Configuration` + `@Bean`
- `@Bean`은 기본적으로 `@Configuration` 안에서만
- domain object / DTO / value object는 bean 등록 금지
- bean overriding, 런타임 동적 등록 기본 금지
- configuration class는 조립만 담당
