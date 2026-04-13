# common module 예시

## 좋은 예시 1: common 대신 owning module에 둠

```text
presentation/support/response/ApiResult.java
```

**왜 좋은가:**

- HTTP 응답 구조는 presentation 소유다
- 다른 레이어가 알 필요가 없다
- 공용으로 빼면 오히려 경계가 흐려진다

## 좋은 예시 2: common 대신 module API로 노출

```text
order/
  OrderManagement.java
order/spi/
  package-info.java (@NamedInterface("spi"))
  OrderLookup.java
```

**왜 좋은가:**

- 필요한 범위만 공개한다
- 전체 common으로 빼지 않고 모듈 API를 좁게 노출한다

## 좋은 예시 3: 예외적으로 허용 가능한 작은 공용 타입

```text
common/types/NormalizedHost.java
```

**허용 조건:**

- 여러 모듈이 실제로 사용
- framework/business/persistence 의존 없음
- 값 기반 타입
- 변화 이유가 동일함

**왜 좋은가:**

- 진짜 공통 값 의미를 담는다
- owning module이 특정되기 어렵다
- 경계를 섞지 않는다

## 나쁜 예시 1: 잡동사니 common

```text
common/
  StringUtils.java
  DateUtils.java
  ErrorUtils.java
  ValidationUtils.java
  AuthConstants.java
  ApiResult.java
  UserMapper.java
```

**문제:**

- 소유권이 불명확하다
- web/domain/infrastructure가 섞인다
- dump zone이 된다

## 나쁜 예시 2: 경계 회피용 common

```text
common/UserDto.java
```

**문제:**

- presentation DTO를 공용으로 올려 application/infrastructure도 기대게 만들 수 있다
- DTO/Domain/Entity 경계가 무너진다

## 나쁜 예시 3: premature abstraction common

```text
common/DeadlineHelper.java
```

**문제:**

- task와 payment가 지금은 비슷해 보여도 미래에 독립 진화할 수 있다
- owning module 안에 두는 편이 더 안전할 수 있다
