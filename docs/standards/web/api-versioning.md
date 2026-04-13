# API Versioning 기준

## 1. 목적

이 문서는 API version을 언제 도입하고, 어떤 위치에 두며, 어떤 경우에 올릴지 정의한다.

이 문서의 목표는 다음과 같다.

- API 변경을 안전하게 진화시킨다
- 이미 배포된 클라이언트를 불필요하게 깨뜨리지 않는다
- 버전 전략이 endpoint마다 제각각 달라지는 일을 막는다
- 버전과 deprecation 정책을 문서화 가능하게 만든다

## 2. 근거 수준

- Official: Spring Framework, HTTP/REST 관련 공식 문서/가이드에서 직접 확인되는 내용
- Official + Practice: 공식 제약 위에 일반적인 실무 API 운영 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 API versioning에는 표준 단일 방식이 없다

Spring 공식 문서는 API version을 지정하는 표준 방법은 없다고 설명하며, 버전을 header, query parameter, media type parameter, URL path 중 어디에서 읽을지 애플리케이션이 정해야 한다고 말한다. Spring MVC의 ApiVersionStrategy도 이 여러 전략을 지원한다.

프로젝트 규칙:

- “업계 표준이라 무조건 이 방식”이라는 전제를 두지 않는다
- 한 서비스 안에서는 반드시 하나의 기본 전략을 정한다
- 예외 전략을 허용하더라도 기준과 이유를 남긴다

### 3.2 버전은 “breaking change 관리 수단”이다

Azure REST 가이드도 버저닝의 핵심 요구를 “기존 고객 workload가 깨지지 않아야 하고, 고객이 새 버전을 선택적으로 채택할 수 있어야 한다”는 점으로 설명한다. 즉, 버전은 새 기능 홍보 수단이 아니라 호환성 관리 수단이다.

프로젝트 규칙:

- breaking change가 아니면 새 major version을 만들지 않는다
- additive change, optional field 추가, 하위 호환 가능한 확장은 기존 major 안에서 처리한다
- version은 endpoint 개수 늘리기 수단이 아니다

### 3.3 버전 전략보다 더 중요한 것은 일관성이다

Azure는 query parameter 전략을 강하게 권장하고 path versioning을 금지하지만, Stripe는 header 기반 version pinning을 사용한다. 업계의 실제 운영 방식이 서로 다르다는 뜻이다. 따라서 프로젝트에서 더 중요한 것은 “어느 방식이냐”보다 “같은 API 군에서 전략을 섞지 않느냐”다.

프로젝트 규칙:

- 같은 API product 안에서 path/header/query versioning을 혼용하지 않는다
- version 협상 위치가 달라지면 문서, 테스트, 운영, client SDK가 모두 복잡해진다

## 4. 이 프로젝트의 기본 전략

### 4.1 기본값은 path major versioning이다

프로젝트 기본 전략:

```text
/api/v1/...
/api/v2/...
```

형태의 path major versioning을 기본으로 한다.

이 규칙은 Spring이 path version resolver를 공식 지원한다는 사실 위에, 실무적으로 다음 장점 때문에 선택한 Project Recommendation 이다.

- URL만 봐도 버전이 드러난다
- 로그, 게이트웨이, 캐시, 문서화에서 식별이 쉽다
- client가 명시적으로 어떤 major를 호출하는지 드러난다
- Spring 7 이전/이후 여부와 관계없이 구현이 단순하다

Spring은 path, header, query parameter, media type parameter 모두 지원한다. 따라서 path 전략은 framework 차원에서도 무리 없는 선택이다.

### 4.2 기본 path version은 major만 올린다

프로젝트 규칙:

- URL에는 기본적으로 v1, v2처럼 major 만 노출한다
- v1.1, v1.2, v1.2.3 같은 minor/patch를 path에 올리지 않는다
- minor/patch 수준 진화는 같은 major 안에서 하위 호환으로 처리한다

Spring 7의 native API versioning은 semantic parser를 통해 major/minor/patch까지 다룰 수 있고, 1.2+ 같은 baseline version도 지원한다. 하지만 그것이 곧 공개 URL에 minor/patch를 그대로 드러내야 한다는 뜻은 아니다. 이 프로젝트는 공개 계약 단순성을 위해 path에는 major만 둔다.

### 4.3 header/query/media type versioning은 예외적으로만 사용한다

Spring은 header, query parameter, media type parameter도 공식 지원한다. Azure는 query parameter를, Stripe는 header를 대표적으로 사용한다. 그러나 이 프로젝트에서는 이를 기본값이 아닌 예외 전략으로 둔다.

허용 가능한 예외 예:

- 하나의 URL을 유지해야 하는 강한 외부 계약이 있을 때
- API gateway/product 정책이 이미 header versioning을 강제할 때
- 내부 SDK가 header pinning에 맞춰 설계되어 있을 때

프로젝트 규칙:

- 예외 전략을 도입하면 해당 API 군 전체에서 일관되게 유지한다
- path와 header versioning을 같은 리소스에 동시에 섞지 않는다

## 5. 언제 버전을 올리는가

### 5.1 major version을 올려야 하는 경우

프로젝트 규칙:

다음은 breaking change 로 보고 major version을 올린다.

- 필수 request field 추가
- 기존 field 의미 변경
- response field 삭제
- response field 타입 변경
- status code 의미 변경
- error code 계약 변경
- 인증 방식/권한 요구의 비호환 변경
- pagination/filter/sort 의미의 비호환 변경

이 항목은 Practice + Project Recommendation 이다. 버전은 기존 client를 깨뜨릴 수 있는 변경을 분리하기 위한 수단으로 쓴다. 이 원칙은 Azure의 “기존 workload는 깨지지 않아야 한다”는 요구와도 맞닿아 있다.

### 5.2 major version을 올리지 않아도 되는 경우

프로젝트 규칙:

다음은 원칙적으로 같은 major 안에서 처리한다.

- optional response field 추가
- optional request field 추가
- backward compatible validation 완화
- 새로운 endpoint 추가
- 새로운 enum value 추가 가능성을 미리 허용한 경우
- 기존 의미를 깨지 않는 내부 구현 변경

Azure 가이드는 버전 가능성을 위해 확장 가능한 계약 설계를 강조하고, 새 값이 생길 수 있음을 문서화하라고 권장한다. 이는 불필요한 버전 증가를 줄이는 방향과 맞는다.

## 6. Spring 사용 규칙

### 6.1 Spring 7+를 쓰는 경우 native API versioning을 활용할 수 있다

Spring MVC는 API versioning을 공식 지원하고, request에서 버전을 읽어 @RequestMapping 계열의 version 속성과 매핑할 수 있다. version 속성은 고정 버전("1.2"), baseline 버전("1.2+"), 또는 미지정(any version, lowest priority)을 지원한다. 지원되지 않는 버전이나 누락된 버전은 기본적으로 400으로 처리된다. deprecated version에 대해서는 Deprecation, Sunset, Link 헤더도 보낼 수 있다.

프로젝트 규칙:

- Spring 7+라면 native version mapping은 사용 가능
- 다만 이 프로젝트의 공개 API 기본 전략은 여전히 path major versioning
- native versioning을 쓰더라도 외부 계약 복잡도를 늘리지 않게 사용한다

### 6.2 Spring 6.x 이하 또는 단순 운영이 목표라면 explicit path versioning을 선호한다

Spring 7 이전에는 지금처럼 통합된 first-class version mapping이 없었으므로, 실무에서는 path를 통해 명시적으로 controller를 나누는 방식이 운영상 단순했다. 이 프로젝트도 버전 전략 자체보다 명시성을 우선한다. Spring 7을 쓰지 않더라도 /api/v1/**, /api/v2/** 구조는 그대로 유효하다. 이 항목은 Project Recommendation 이다.

### 6.3 version 누락 정책은 프로젝트에서 명시한다

Spring은 versioning을 활성화하면 기본적으로 version이 필수이고, 누락되면 MissingApiVersionException으로 400이 된다. 다만 optional로 두고 가장 최신 버전을 쓰게 하거나, default version을 둘 수도 있다.

프로젝트 규칙:

- path major versioning을 쓰는 공개 API에서는 버전 명시를 필수로 한다
- “버전 없으면 최신 버전 사용” 같은 암묵 규칙을 두지 않는다
- 클라이언트가 어떤 계약을 호출하는지 URL에서 명확해야 한다

## 7. deprecation / sunset 규칙

### 7.1 deprecated version은 공지와 함께 단계적으로 종료한다

Spring의 built-in deprecation handler는 deprecated version에 대해 Deprecation, Sunset, Link 헤더를 보낼 수 있다. Azure 가이드도 deprecating behavior를 응답 헤더로 공지하라고 권장한다.

프로젝트 규칙:

- deprecated version은 문서, 릴리스 노트, 응답 헤더 중 최소 2개 이상으로 공지한다
- sunset 일정은 명확한 날짜와 마이그레이션 경로를 함께 제공한다
- 구버전을 숨겨서 갑자기 끊지 않는다

### 7.2 구버전과 신버전은 일정 기간 병행 운영할 수 있다

프로젝트 규칙:

- breaking change가 있는 경우 신버전 출시 후 일정 기간 구버전을 병행 운영한다
- 병행 운영 기간과 종료 시점은 문서화한다
- 병행 중에는 error code, 인증, 주요 리소스 의미가 버전별로 뒤섞이지 않게 관리한다

## 8. URL / 버전 구조 규칙

### 8.1 권장 구조

프로젝트 권장 구조:

```text
/api/v1/sessions
/api/v1/users/{userId}
/api/v2/users/{userId}
```

프로젝트 규칙:

- api prefix 아래에 version segment를 둔다
- version은 리소스 path 앞쪽에서 빠르게 식별 가능해야 한다
- resource naming 규칙은 버전과 별도로 일관되게 유지한다

### 8.2 버전과 리소스 의미를 함께 바꾸지 않는다

프로젝트 규칙:

- version을 올릴 때 path naming convention 자체까지 불필요하게 같이 바꾸지 않는다
- 버전 차이는 “계약 변화”를 표현하는 데 집중한다
- v1/users에서 v2/members처럼 naming까지 동시에 바꾸는 것은 진짜 의미 변화가 있을 때만 허용한다

## 9. 구현 규칙

### 9.1 controller/package 분리는 버전 경계를 드러내야 한다

프로젝트 규칙:

- 버전별 controller는 package 또는 클래스 구조에서 분리한다
- v1, v2 endpoint가 뒤섞여서 읽히지 않게 한다
- shared application/domain 로직은 재사용하되, transport contract는 버전별로 분리한다

### 9.2 DTO와 응답 형식도 버전 경계를 존중한다

프로젝트 규칙:

- v1 request/response DTO와 v2 DTO는 필요하면 분리한다
- 버전이 다르면 같은 이름의 DTO를 무리하게 재사용하지 않는다
- ApiResult 같은 envelope는 major 간에도 최대한 유지하되, payload contract는 버전별로 독립적으로 관리한다

## 10. 금지 규칙

다음은 기본 금지다.

- 같은 API 군에서 path/header/query versioning 혼용
- breaking change인데 version을 올리지 않음
- minor/patch를 공개 URL에 무분별하게 노출
- 버전 미지정 시 최신 버전으로 암묵 fallback
- deprecated version 종료 일정 없이 장기 방치
- 신버전 도입과 동시에 resource naming/convention까지 불필요하게 전면 변경
- controller 하나에서 여러 major 계약을 뒤섞어 처리

## 11. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 변경은 정말 breaking change인가?
- 새 major가 필요한 이유를 설명할 수 있는가?
- version 전략이 이 API 군 전체에서 일관적인가?
- 버전 위치가 client, gateway, 로그, 문서에서 쉽게 드러나는가?
- deprecated/sunset 계획이 있는가?
- DTO와 controller 구조가 버전 경계를 드러내는가?
