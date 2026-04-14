# API Versioning 예시

## 좋은 예시

### 예시 1. path major versioning으로 계약을 명시한다

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserQueryV1Controller {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}")
    public ApiResult<UserV1Response> getUser(@PathVariable String userId) {
        UserResult result = userQueryUseCase.getUser(userId);
        return ApiResult.success(new UserV1Response(
                result.userId(),
                result.email()
        ));
    }
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/users")
public class UserQueryV2Controller {

    private final UserQueryUseCase userQueryUseCase;

    @GetMapping("/{userId}")
    public ApiResult<UserV2Response> getUser(@PathVariable String userId) {
        UserResult result = userQueryUseCase.getUser(userId);
        return ApiResult.success(new UserV2Response(
                result.userId(),
                result.email(),
                result.displayName()
        ));
    }
}
```

**좋은 이유:**

- URL만 보고 major version이 드러난다
- 버전별 계약 차이가 controller와 DTO에서 명확하다
- 내부 use case는 공유하면서 외부 계약은 분리할 수 있다

### 예시 2. Spring 7+ native version mapping을 제한적으로 활용한다

```java
@RestController
@RequestMapping("/accounts/{id}")
public class AccountController {

    @GetMapping
    public ApiResult<AccountLegacyResponse> getDefault(@PathVariable String id) {
        return ApiResult.success(...);
    }

    @GetMapping(version = "1.1")
    public ApiResult<AccountV11Response> getV11(@PathVariable String id) {
        return ApiResult.success(...);
    }

    @GetMapping(version = "1.2+")
    public ApiResult<AccountV12Response> getV12Plus(@PathVariable String id) {
        return ApiResult.success(...);
    }
}
```

**좋은 이유:**

- Spring이 공식 지원하는 version mapping 규칙을 따른다
- fixed version과 baseline version의 의미가 분명하다
- 단, 이 방식은 Spring 7+에 맞는 선택지다.

### 예시 3. deprecated version에 sunset 공지를 준비한다

```java
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionV1Controller {
    // 구버전 유지
}
```

운영 정책 예:

- 문서에 v1 deprecation 공지
- 릴리스 노트에 종료 일정 공지
- 응답 헤더에 deprecation/sunset/link 추가

**좋은 이유:**

- 버전 종료가 갑작스럽지 않다
- 클라이언트가 마이그레이션할 시간을 가진다
- Spring도 deprecation 관련 응답 헤더 전송을 지원한다.

## 나쁜 예시

### 예시 1. 같은 API 군에서 path와 header versioning을 섞는다

```java
@RestController
@RequestMapping("/api/v1/users")
public class MixedVersionController {

    @GetMapping
    public ApiResult<List<UserResponse>> getUsers() {
        return ApiResult.success(...);
    }

    @GetMapping(headers = "API-Version=2")
    public ApiResult<List<UserResponse>> getUsersV2() {
        return ApiResult.success(...);
    }
}
```

**나쁜 이유:**

- 버전 협상 위치가 두 군데다
- client, gateway, 문서, 테스트가 모두 복잡해진다
- 한 API product 안의 일관성을 깨뜨린다

### 예시 2. breaking change인데 version을 올리지 않는다

```java
public record UserResponse(
        String userId,
        String email,
        String displayName,
        String role
) {
}
```

기존에 email만 응답하던 endpoint가 같은 /api/v1/users/{id} 에서

- 기존 필드 삭제
- 필수 필드 의미 변경
- 구조 변경

을 해 버리는 경우

**나쁜 이유:**

- 기존 client를 조용히 깨뜨린다
- versioning 목적 자체를 무력화한다

### 예시 3. minor/patch를 path에 과하게 노출한다

```java
@RequestMapping("/api/v1.0.3/users")
public class UserController {
}
```

**나쁜 이유:**

- 공개 URL이 불필요하게 복잡해진다
- minor/patch 수준 변화까지 client 계약에 노출된다
- 프로젝트의 major-only path 전략과 맞지 않는다

### 예시 4. 버전 누락 시 최신 버전으로 암묵 fallback한다

```java
@GetMapping("/api/users/{userId}")
public ApiResult<UserResponse> getUser(@PathVariable String userId) {
    // 내부적으로 최신 버전 계약으로 응답
}
```

**나쁜 이유:**

- client가 어떤 계약을 호출하는지 불명확하다
- 시간이 지나며 응답 의미가 조용히 바뀔 수 있다
- 명시적 계약 원칙과 맞지 않는다
