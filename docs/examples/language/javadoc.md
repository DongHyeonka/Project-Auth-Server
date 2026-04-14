# Javadoc 예시

## 좋은 예시 1: 반환 계약과 예외 조건이 드러나는 메서드

```java
/**
 * Returns the active user for the given email.
 *
 * @param email normalized user email, never {@code null}
 * @return the matching active user
 * @throws UserNotFoundException if no user exists for the given email
 * @throws InactiveUserException if the user exists but is inactive
 */
public User getActiveUserByEmail(String email) {
    ...
}
```

왜 좋은가:

- 호출자가 믿을 수 있는 계약이 보인다
- null 허용 여부와 실패 조건이 드러난다
- 구현 세부가 아니라 API 의미를 설명한다

## 좋은 예시 2: value object 생성 제약 문서화

```java
/**
 * Value object representing a normalized email address.
 *
 * <p>The value is always lowercase and trimmed.
 */
public record UserEmail(String value) {
    ...
}
```

왜 좋은가:

- 타입의 핵심 invariant를 문서화한다
- typical reader가 놓치기 쉬운 제약을 설명한다

## 좋은 예시 3: override는 문서 상속 활용

```java
@Override
public String getName() {
    return name;
}
```

왜 좋은가:

- 상위 계약이 충분하면 중복 문서를 쓰지 않는다
- 불필요한 복붙 Javadoc을 줄인다

## 좋은 예시 4: package/class 수준에서 구조 설명

```java
/**
 * HTTP request/response contracts and exception translation for the auth API.
 *
 * <p>This package owns controllers, request/response DTOs, and client-facing
 * error handling. It must not depend directly on infrastructure implementations.
 */
package com.project.auth.presentation;
```

왜 좋은가:

- package 책임과 금지사항이 드러난다
- architecture 문서와 연결되는 설명이다

## 나쁜 예시 1: 자명한 getter 설명

```java
/**
 * Returns the user name.
 */
public String getUserName() {
    return userName;
}
```

문제:

- 이름만 읽어도 알 수 있다
- 유지보수 시 stale 될 가능성만 늘어난다

개선:

- 생략하거나
- 정말 추가 계약이 있을 때만 적는다

## 나쁜 예시 2: 구현 설명만 적음

```java
/**
 * Uses ArrayList internally and loops over all elements to find the user.
 */
public User findUser(String email) {
    ...
}
```

문제:

- 구현 세부에 과도하게 묶인다
- 리팩터링 시 쉽게 거짓 문서가 된다

개선:

- 호출 계약, 검색 조건, 실패 조건을 설명한다

## 나쁜 예시 3: 태그만 채우는 문서

```java
/**
 * @param email the email
 * @return the user
 */
public User findUser(String email) {
    ...
}
```

문제:

- 독자에게 새로운 정보가 없다
- 형식만 있고 계약이 없다

개선:

- summary와 제약/의미를 써라
- 아니면 생략하라

## 나쁜 예시 4: stale Javadoc 방치

```java
/**
 * Returns a mutable list of authorities.
 */
public List<String> getAuthorities() {
    return List.copyOf(authorities);
}
```

문제:

- 코드와 문서가 충돌한다
- 거짓 문서가 된다

개선:

```java
/**
 * Returns an unmodifiable snapshot of authorities.
 */
public List<String> getAuthorities() {
    return List.copyOf(authorities);
}
```

또는 Javadoc 삭제 후 더 적절한 형태로 재작성
