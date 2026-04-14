# Pagination / Sort / Filter 예시

## 좋은 예시

### 예시 1. 일반 목록 조회는 명시적 query DTO + page 응답으로 표현한다

```java
public record UserListRequest(
        @Min(1) int page,
        @Min(1) @Max(100) int size,
        String keyword,
        UserStatus status,
        String sortBy,
        SortDirection direction
) {
}

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        boolean hasNext,
        Long totalCount
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserQueryController {

    private final SearchUsersUseCase searchUsersUseCase;

    @GetMapping
    public ApiResult<PageResponse<UserResponse>> search(
            @Valid @ModelAttribute UserListRequest request
    ) {
        SearchUsersQuery query = SearchUsersQuery.of(
                request.page(),
                request.size(),
                request.keyword(),
                request.status(),
                request.sortBy(),
                request.direction()
        );

        UserPageResult result = searchUsersUseCase.search(query);

        return ApiResult.success(new PageResponse<>(
                result.items().stream()
                        .map(UserResponse::from)
                        .toList(),
                result.page(),
                result.size(),
                result.hasNext(),
                result.totalCount()
        ));
    }
}
```

**좋은 이유:**

- 외부 계약이 명시적이다
- query input과 내부 paging 모델이 분리된다
- 응답도 raw Page가 아니라 API 전용 DTO다

### 예시 2. sort field는 allowlist로 받고, persistence adapter에서 내부 정렬로 변환한다

```java
public enum UserSortField {
    CREATED_AT("createdAt"),
    DISPLAY_NAME("displayName");

    private final String externalName;

    UserSortField(String externalName) {
        this.externalName = externalName;
    }

    public static UserSortField from(String value) {
        return Arrays.stream(values())
                .filter(field -> field.externalName.equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidSortFieldException(value));
    }
}

@Service
public class JpaUserSortMapper {

    public Sort toSort(UserSortField sortField, SortDirection direction) {
        return switch (sortField) {
            case CREATED_AT -> Sort.by(direction.toSpring(), "createdAt", "id");
            case DISPLAY_NAME -> Sort.by(direction.toSpring(), "displayName", "id");
        };
    }
}
```

**좋은 이유:**

- 외부 정렬 키와 내부 컬럼/프로퍼티가 분리된다
- unsupported sort field를 명시적으로 거절할 수 있다
- tie-breaker가 포함되어 정렬이 안정적이다
- Spring Data `Sort` 변환은 persistence adapter 경계에 머문다

### 예시 3. count가 필요 없으면 Slice 스타일 응답으로 줄인다

```java
public record SliceResponse<T>(
        List<T> items,
        int page,
        int size,
        boolean hasNext
) {
}

@Service
@RequiredArgsConstructor
public class SearchAuditLogUseCase {

    private final AuditLogRepository auditLogRepository;

    public AuditLogSliceResult search(SearchAuditLogQuery query) {
        return auditLogRepository.findSliceByCondition(query);
    }
}
```

**좋은 이유:**

- application은 count가 필요 없는 slice 결과를 typed result로 반환한다
- Spring Data `Slice` / `PageRequest`와 response DTO 변환은 바깥 adapter 책임으로 남긴다

### 예시 4. 대용량 피드는 cursor pagination을 쓴다

```java
public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext
) {
}

public record TimelineRequest(
        String cursor,
        @Min(1) @Max(100) int size
) {
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/timeline")
public class TimelineController {

    private final ReadTimelineUseCase readTimelineUseCase;

    @GetMapping
    public ApiResult<CursorPageResponse<TimelineItemResponse>> read(
            @Valid @ModelAttribute TimelineRequest request
    ) {
        TimelineWindowResult result = readTimelineUseCase.read(request.cursor(), request.size());

        return ApiResult.success(new CursorPageResponse<>(
                result.items(),
                result.nextCursor(),
                result.hasNext()
        ));
    }
}
```

**좋은 이유:**

- 대용량/변동이 큰 목록에 더 적합하다
- 외부에는 opaque cursor만 노출한다
- page number 깊이에 따라 성능이 급격히 나빠지는 구조를 피할 수 있다

### 예시 5. 다중 값 필터는 반복 query parameter로 받는다

```java
public record UserSearchRequest(
        List<UserStatus> status,
        String keyword,
        @Min(1) int page,
        @Min(1) @Max(100) int size
) {
}
```

요청 예:

```http
GET /api/v1/users?status=ACTIVE&status=PENDING&keyword=kim&page=1&size=20
```

**좋은 이유:**

- 다중 값 필터가 명확하다
- query parameter 규약이 읽기 쉽다
- Spring 바인딩과도 자연스럽게 맞는다

## 나쁜 예시

### 예시 1. 공개 API controller에 raw Pageable을 그대로 노출한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class BadUserController {

    private final UserRepository userRepository;

    @GetMapping
    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
}
```

**나쁜 이유:**

- 외부 계약이 Spring Data 내부 타입에 종속된다
- entity와 raw Page가 그대로 노출된다
- request/response 계약을 프로젝트가 통제하기 어렵다

### 예시 2. 지원하지 않는 sort field를 조용히 무시한다

```java
public Sort toSort(String sortBy, SortDirection direction) {
    if ("createdAt".equals(sortBy)) {
        return Sort.by(direction.toSpring(), "createdAt");
    }
    return Sort.unsorted();
}
```

**나쁜 이유:**

- 잘못된 요청을 성공처럼 처리한다
- client는 정렬이 적용된 줄 오해할 수 있다
- unsupported sort는 명시적으로 거절해야 한다

### 예시 3. 깊은 페이지까지 offset만 강제한다

```java
@GetMapping("/api/v1/events")
public ApiResult<PageResponse<EventResponse>> getEvents(
        @RequestParam int page,
        @RequestParam int size
) {
    // 수백만 건 로그를 무조건 offset paging으로 조회
}
```

**나쁜 이유:**

- 큰 offset paging은 성능이 급격히 나빠질 수 있다
- 이벤트/로그/피드 계열에는 cursor 전략 검토가 필요하다

### 예시 4. 페이지마다 sort/filter가 달라질 수 있게 한다

```http
GET /api/v1/users?page=1&size=20&sortBy=createdAt&direction=desc
GET /api/v1/users?page=2&size=20&sortBy=displayName&direction=asc
```

**나쁜 이유:**

- 같은 목록의 다음 페이지라는 의미가 깨진다
- 중복/누락/순서 흔들림이 생길 수 있다

### 예시 5. 범용 filter DSL을 기본 공개 API에 도입한다

```http
GET /api/v1/users?filter=(status eq ACTIVE and (createdAt gt 2026-01-01)) or (role in [ADMIN,OWNER])
```

**나쁜 이유:**

- 단순 목록 API치고 계약이 과도하게 복잡하다
- 문서화, 검증, 운영 비용이 커진다
- 기본 공개 API로는 명시적 필터 파라미터가 더 낫다
