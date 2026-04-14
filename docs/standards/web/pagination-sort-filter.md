# Pagination / Sort / Filter 기준

## 1. 목적

이 문서는 목록 조회 API의 pagination, sort, filter 규약을 정의한다.

이 문서의 목표는 다음과 같다.

- 목록 조회 endpoint의 query contract를 일관되게 만든다
- page 기반과 cursor 기반 pagination의 사용 기준을 구분한다
- 정렬과 필터의 허용 범위를 명확히 한다
- Spring Data의 편의 기능과 공개 API 계약을 분리한다

## 2. 근거 수준

- Official: Spring Framework / Spring Data / 공개 API 가이드에서 직접 확인되는 내용
- Official + Practice: 공식 문서의 확장 지점 위에 일반적인 실무 API 설계 원칙을 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

## 3. 기본 원칙

### 3.1 목록 조회의 입력은 query parameter를 기본으로 한다

Spring MVC에서 @RequestParam은 query parameter나 form data를 controller method argument에 바인딩하는 공식 방법이며, 같은 이름의 파라미터를 여러 번 보내는 경우 리스트나 배열로도 받을 수 있습니다. Azure 가이드도 pagination과 filtering을 query parameter로 제공하라고 권장합니다.

프로젝트 규칙:

- 목록 조회 입력은 기본적으로 query parameter로 받는다
- pagination, sort, filter는 request body가 아니라 query contract로 노출한다
- GET 목록 조회에 body filtering을 기본 전략으로 쓰지 않는다

### 3.2 Spring의 Pageable/Sort 지원은 “프레임워크 편의”이지 “공개 API 계약”은 아니다

Spring Data web support는 controller method argument로 Pageable과 Sort를 바로 받을 수 있게 해 주고, 기본 페이지 해석도 제공한다. 하지만 그것은 Spring 애플리케이션 내부 편의 기능이지, 외부 클라이언트에 그대로 노출해야 하는 계약이라는 뜻은 아닙니다.

프로젝트 규칙:

- 공개 API controller는 raw Pageable/Sort를 기본 시그니처로 사용하지 않는다
- 외부 계약은 명시적 request DTO 또는 명시적 query parameter 규약으로 드러낸다
- repository/application 내부에서는 필요 시 Pageable/Sort를 사용할 수 있다

### 3.3 pagination, sort, filter는 함께 설계한다

Azure 가이드는 sorting이 filtering과 조합되어야 하며, paginated list에서는 모든 페이지에서 같은 filtering options와 sort order를 유지하라고 권장합니다. GitHub도 실제 paginated endpoint에서 다음 페이지 URL을 Link header로 주며, page/cursor 계열 query parameter를 계속 이어서 사용합니다.

프로젝트 규칙:

- pagination, sort, filter는 서로 독립 기능처럼 보이더라도 하나의 목록 조회 계약으로 설계한다
- 다음 페이지를 조회할 때 filter/sort가 바뀌지 않게 설계한다
- 페이지 이동 중 계약이 흔들리면 안 된다

## 4. Pagination 표준

### 4.1 기본값은 page-based pagination이다

Azure는 일반적인 데이터 조회에서 limit/offset 형태의 pagination을 예시로 제시하고, GitHub는 page 및 cursor 계열 파라미터를 실제로 사용합니다. Spring Data도 Pageable 기반 paging을 폭넓게 지원합니다.

프로젝트 기본 규칙:

- 일반 목록 조회 endpoint의 기본 pagination은 page-based
- 외부 계약 기본 파라미터는 page, size
- 공개 API에서는 외부 page는 1-based 로 둔다
- 내부 Spring Data 변환 시 필요하면 0-based PageRequest로 변환한다

### 4.2 공개 API는 Spring 내부의 0-based 페이지 번호를 그대로 노출하지 않는다

Spring Data의 Pageable은 첫 페이지를 0으로 다루는 API를 제공하고, Pageable.ofSize(...)도 첫 페이지를 page number 0으로 생성합니다. 하지만 공개 API가 반드시 그 내부 표현을 따라야 하는 것은 아닙니다.

프로젝트 규칙:

- 외부 API는 page=1부터 시작한다
- controller 또는 mapper에서 내부 0-based Pageable로 변환한다
- Spring 내부 표현을 외부 계약에 그대로 새어 나오게 하지 않는다

### 4.3 page size는 기본값과 최대값을 반드시 둔다

Azure는 pagination 예시에서 limit와 offset에 의미 있는 기본값을 둘 것을 권장합니다. GitHub도 per_page를 통해 페이지 크기를 조절하지만, endpoint별 한도가 존재합니다.

프로젝트 규칙:

- size는 기본값과 최대값을 가진다
- 기본값 예: 20
- 최대값 예: 100
- 최대값을 넘는 요청은 보정하거나 400으로 거절하는 정책을 API군 단위로 일관되게 정한다

### 4.4 Page가 항상 정답은 아니다

Spring Data에서 Page는 전체 개수와 전체 페이지 수를 알기 위해 추가 count query를 수행할 수 있고, 그 비용이 비쌀 수 있습니다. Slice는 다음 페이지 존재 여부만 알고, List는 count metadata를 만들지 않습니다. 또한 큰 offset 기반 조회는 비효율적일 수 있습니다.

프로젝트 규칙:

- totalCount가 반드시 필요한 목록만 Page 스타일 메타데이터를 제공한다
- 다음 페이지 존재만 알면 충분한 목록은 Slice 스타일 응답을 선호한다
- count query 비용이 큰 도메인에서는 무조건 total count를 주지 않는다

### 4.5 대용량/변동이 큰 목록은 cursor pagination을 우선 검토한다

Spring Data의 scrolling/keyset filtering은 stable sort order를 전제로 다음 구간을 더 효율적으로 가져올 수 있고, 큰 offset을 건너뛰는 비용을 줄이도록 설계되어 있습니다. GitHub도 실제 API에서 before/after/since 같은 cursor 계열 파라미터를 사용합니다.

프로젝트 규칙:

다음 조건이면 cursor pagination을 우선 검토한다

- 데이터가 매우 크다
- 최신순 피드/로그/이벤트처럼 계속 변한다
- 큰 offset 페이지를 자주 조회한다
- cursor는 opaque string 으로 노출한다
- 내부 keyset 구조를 외부에 직접 노출하지 않는다

### 4.6 cursor pagination은 stable sort가 필수다

Spring Data keyset filtering은 stable sorting order를 전제로 하고, sort 필드와 primary key를 함께 사용해 다음 위치를 계산합니다. 또한 keyset 필드는 non-nullable이어야 하며, 적절한 인덱스가 있을 때 가장 잘 동작합니다.

프로젝트 규칙:

- cursor pagination에는 안정적인 정렬 기준이 반드시 있어야 한다
- 기본 정렬 필드만으로 충돌 가능성이 있으면 tie-breaker로 id 같은 고유 키를 추가한다
- nullable field를 cursor 핵심 정렬 키로 쓰는 것은 지양한다

## 5. Sort 표준

### 5.1 정렬은 명시적 허용 목록 기반으로 제공한다

Azure 가이드는 정렬에 사용할 수 없는 필드를 요청하면 에러를 반환하라고 권장하고, 값의 inherent order를 따르라고 안내합니다. Spring Data는 Sort와 Pageable로 정렬을 처리할 수 있지만, 어떤 필드를 정렬 가능하게 열 것인지는 애플리케이션이 결정해야 합니다.

프로젝트 규칙:

- 정렬 가능 필드는 allowlist로 관리한다
- 지원하지 않는 필드 정렬 요청은 무시하지 말고 400 으로 응답한다
- DB 컬럼명이나 내부 경로를 그대로 외부 sort key로 노출하지 않는다

### 5.2 기본 정렬을 반드시 둔다

Spring Data keyset scrolling도 stable order를 전제로 하고, pagination이 있는 목록은 정렬 기준이 흐리면 페이지 이동 중 결과가 흔들릴 수 있습니다. Azure도 sorting과 pagination의 일관성을 강조합니다.

프로젝트 규칙:

- 목록 endpoint는 기본 정렬을 가진다
- 권장 기본 정렬 예:
- 생성일 내림차순
- 수정일 내림차순
- 이름 오름차순
- tie-breaker가 필요하면 id를 마지막 정렬 키로 고정한다

### 5.3 외부 정렬 파라미터 형식은 하나로 통일한다

Spring Data는 query parameter로 Sort를 해석하는 지원을 제공하지만, 공개 API는 더 읽기 쉬운 별도 규약을 가질 수 있습니다. Azure 예시는 orderby=name desc,hireDate 같은 문법도 보여 줍니다. 업계 관행은 다양하므로 프로젝트가 하나를 고정하는 것이 더 중요합니다.

프로젝트 기본 규칙:

- 기본 형식은 sortBy + direction
- 다중 정렬이 정말 필요한 API군에서만 반복 sort 같은 확장 형식을 허용
- 같은 API product 안에서 sortBy/direction, orderby, 반복 sort를 혼용하지 않는다

### 5.4 정렬은 의미 단위로 노출한다

Azure는 필드 타입의 inherent order를 따르라고 권장합니다. 즉, 날짜는 시간순, 숫자는 숫자순으로 정렬되어야 합니다.

프로젝트 규칙:

- 날짜는 시간순 의미로 정렬한다
- 문자열 표시명과 내부 저장 키가 다르면 외부 의미 기준의 정렬 키를 정의한다
- “보여지는 값”과 “정렬되는 값”의 의미가 다르면 문서화한다

## 6. Filter 표준

### 6.1 필터는 명시적 query parameter를 기본으로 한다

Spring MVC @RequestParam은 query parameter 바인딩의 기본 도구이고, 반복 파라미터도 리스트로 받을 수 있습니다. Azure도 filtering을 query 기반으로 제공하라고 권장합니다.

프로젝트 규칙:

기본 필터는 명시적 named query parameter로 노출한다

예:

- status=ACTIVE
- role=ADMIN
- keyword=alice
- createdFrom=...
- createdTo=...
- 외부 계약은 읽기 쉬운 이름을 사용한다

### 6.2 다중 값 필터는 반복 query parameter를 우선한다

Spring은 같은 이름의 request parameter를 여러 번 보내면 배열/리스트로 받을 수 있습니다.

프로젝트 규칙:

다중 선택 필터 기본 형식은 반복 parameter

예:

```text
status=ACTIVE&status=PENDING
```

- comma-separated 형식은 API군 전체 합의가 있을 때만 허용
- 같은 API군에서 두 방식을 혼용하지 않는다

### 6.3 범위 필터는 의미가 드러나는 이름을 쓴다

이 항목은 주로 Practice + Project Recommendation 이다.

프로젝트 규칙:

- 시간 범위: createdFrom, createdTo
- 수치 범위: minPrice, maxPrice
- 불리언 필터: includeInactive=true
- 비교 연산자를 query string DSL로 억지로 숨기기보다 의미가 드러나는 파라미터명을 우선한다

### 6.4 generic filter DSL은 기본 금지다

Azure는 query-based filtering을 권장하지만, 모든 API가 OData 수준의 범용 filter 문법을 가져야 한다고 요구하지는 않습니다. 실제 실무에서도 공개 API는 명시적 필터 파라미터를 더 많이 사용합니다. GitHub 역시 endpoint별로 filter, state, sort 등 명시적 파라미터를 사용합니다.

프로젝트 규칙:

- 기본 공개 API에서는 범용 문자열 DSL filter를 도입하지 않는다
- 정말 복잡한 검색이 필요하면 별도 search endpoint 또는 명시적 검색 모델을 설계한다
- 단순 목록 API를 mini query language로 만들지 않는다

## 7. 응답 형식 규칙

### 7.1 page-based 응답은 커스텀 page DTO를 사용한다

Spring Data는 Page, Slice, Window 같은 내부 추상화를 제공하지만, 공개 API 응답 계약은 그것과 분리하는 편이 안정적입니다. 또한 Page는 count metadata 비용이 있을 수 있습니다.

프로젝트 규칙:

- 공개 응답은 raw Page<T>를 그대로 노출하지 않는다

기본 응답 형식 예:

```text
ApiResult<PageResponse<T>>
```

PageResponse<T> 권장 필드:

- items
- page
- size
- hasNext
- totalCount (필요 시만)

### 7.2 cursor 응답은 opaque cursor를 반환한다

GitHub는 paginated response에서 다음 페이지 URL 또는 cursor 계열 parameter를 계속 사용하게 하고, Spring Data scrolling은 ScrollPosition을 통해 다음 위치를 이어 갑니다.

프로젝트 규칙:

cursor 응답 기본 형식 예:

```text
ApiResult<CursorPageResponse<T>>
```

CursorPageResponse<T> 권장 필드:

- items
- nextCursor
- hasNext
- cursor는 내부 정렬 키 원본을 그대로 노출하지 않고 인코딩/추상화한다

### 7.3 페이지 응답은 현재 조회 조건을 바꾸지 않게 설계한다

Azure는 모든 페이지에서 같은 filtering options와 sort order를 유지하라고 권장합니다.

프로젝트 규칙:

- 다음 페이지 요청은 같은 filter/sort를 유지해야 한다
- cursor 기반이면 cursor 자체에 정렬 문맥이 포함되거나 서버가 이를 안전하게 검증해야 한다
- page 기반이면 client가 같은 필터/정렬을 재전송하도록 문서화한다

## 8. Spring 사용 규칙

### 8.1 controller는 명시적 request DTO를 우선한다

Spring Data는 controller argument로 Pageable과 Sort를 직접 받을 수 있게 해 줍니다. 하지만 이 프로젝트는 공개 API 가독성과 계약 안정성을 위해 명시적 request DTO를 우선합니다.

프로젝트 규칙:

공개 API controller:

- ListUsersRequest, SearchSessionsRequest 같은 명시적 query DTO 우선

내부 관리용/운영용 endpoint:

- 필요하면 Pageable/Sort 직접 사용 가능

외부 계약은 Spring Data parameter naming에 종속되지 않게 한다

### 8.2 repository/application 내부에서는 Pageable/Slice/Window를 사용할 수 있다

Spring Data는 Page, Slice, Sort, Pageable, Window/scrolling을 모두 지원합니다. 각각은 비용과 의미가 다릅니다.

프로젝트 규칙:

repository 레이어:

- 일반 paging: Pageable
- count 불필요: Slice
- 대규모 scrolling/keyset: Window/scroll position 검토
- 공개 응답은 커스텀 DTO로 변환한다

## 9. 금지 규칙

다음은 기본 금지다.

- 공개 API controller에 raw Pageable/Sort를 무비판적으로 노출
- page 기반 응답에서 무조건 totalCount 계산
- 큰 목록에 깊은 offset paging을 기본 전략으로 고정
- 지원하지 않는 sort field를 조용히 무시
- 정렬 기준 없이 cursor pagination 구현
- filter/sort가 페이지마다 달라질 수 있게 설계
- generic filter DSL을 기본 공개 API에 도입
- raw Page<Entity> 또는 Slice<Entity>를 외부 응답으로 직접 반환

## 10. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 목록 API에 page 방식과 cursor 방식 중 어떤 것이 더 맞는가?
- 외부 query contract가 Spring 내부 타입에 종속되지 않는가?
- page size 기본값과 최대값이 있는가?
- 지원 가능한 sort field가 명시되어 있는가?
- unsupported sort 요청을 400으로 처리하는가?
- filter/sort가 페이지 이동 중에도 일관되게 유지되는가?
- count query 비용이 큰데도 무조건 total count를 계산하고 있지 않은가?
- cursor를 쓴다면 stable sort와 tie-breaker가 보장되는가?
