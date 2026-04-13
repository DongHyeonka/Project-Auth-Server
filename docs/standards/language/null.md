# null 처리 기준

## 목적

`null`은 “값이 없음”을 표현하는 기본 도구가 아니라, **명시적으로 허용된 경계에서만 제한적으로 다루는 값**으로 취급한다.

기본값은:
- non-null
- 명시적으로 nullable인 경우만 `null` 허용
- 내부 로직에서는 가능한 빨리 null을 제거하고 더 명확한 표현으로 바꾼다

## 공식 의미

- Java는 null-safety를 타입 시스템으로 직접 표현하지 못한다.
- 따라서 null 허용 여부는 API 계약, annotation, 검증 코드로 명시해야 한다.
- `Objects.requireNonNull(...)`은 메서드/생성자 파라미터 검증 용도로 우선 사용한다.
- Spring 계열에서는 package/type 수준의 nullability 기본값과 `@Nullable` 명시를 통해 API 계약을 드러내는 방식을 권장한다.

## 기본 규칙

### 1. 기본값은 non-null
명시적으로 nullable이라고 선언되지 않은 값은 non-null로 간주한다.

프로젝트 기본 태도:
- 파라미터: 기본 non-null
- 반환값: 기본 non-null
- 필드: 기본 non-null
- nullable은 예외적 상황만 명시

### 2. nullable 여부는 계약으로 드러낸다
다음 중 하나로 null 허용 여부를 명시한다.

- nullability annotation
- Optional 반환
- 빈 컬렉션/빈 문자열이 아닌 명시적 상태 타입
- API/문서/Javadoc 계약

“읽어보면 알 수 있음” 상태를 금지한다.

### 3. 경계에서만 null을 받는다
다음 경계에서는 null이 들어올 수 있다고 가정하고 방어한다.

- 외부 요청 입력
- DB/JPA 결과
- 외부 API 응답
- 설정값/환경변수
- legacy library API

하지만 경계를 지나 내부 로직으로 들어오면:
- 즉시 검증하거나
- Optional/value object/default object/명시적 상태로 변환한다

### 4. 내부 로직에서 null 전파 금지
application/domain/business 로직에서는 nullable 값을 계속 흘려보내지 않는다.

금지:
- 여러 계층을 거치며 nullable String/DTO field를 계속 전달
- null 여부를 business 의미처럼 암묵적으로 해석
- “일단 null로 두고 나중에 확인” 패턴

### 5. 파라미터 검증은 가능한 한 즉시
public/protected/package boundary 또는 생성자에서는 필요한 경우 초기에 검증한다.

기본 방식:
- `Objects.requireNonNull(...)`
- 명시적 argument validation
- request binding / validation annotation
- value object 생성 시 검증

### 6. Optional과 null을 섞지 않는다
- `Optional` 자체를 null로 두지 않는다
- Optional을 반환하면 null 반환 금지
- nullable field를 억지로 Optional field로 바꾸지 않는다

### 7. 컬렉션/배열/스트림은 null 대신 빈 값 우선
다건 결과는 가능한 한 null 대신 다음을 사용한다.

- empty list
- empty set
- empty map
- empty stream
- empty array

`null` 컬렉션은 기본 금지다.

### 8. DTO/직렬화 경계는 nullable을 명시적으로 관리
Request/Response DTO에서는 nullable field가 필요할 수 있다.  
이 경우:
- nullable 여부를 명시하고
- 내부 로직 진입 전에 변환/검증한다.

DTO의 nullable 상태를 domain/application 전체로 전파하지 않는다.

### 9. Entity와 Domain은 구분해서 본다
- JPA entity는 DB nullable 제약을 반영할 수 있다
- domain model은 비즈니스 invariant 기준으로 더 엄격해야 한다

즉:
- DB가 nullable이어도 domain은 non-null일 수 있다
- persistence mapper에서 변환/검증 책임을 진다

### 10. null-check는 가능한 한 한 곳에서 끝낸다
같은 값에 대해 여러 레이어에서 반복 null-check 하지 않는다.

기본 방향:
- 경계에서 검증
- 값 객체로 승격
- 이후는 non-null 가정

### 11. null을 business state로 쓰지 않는다
다음 패턴을 금지한다.

- `status == null`이면 임시 상태
- `provider == null`이면 로컬 로그인
- `deletedAt == null` 같은 인프라 관례를 business 의미로 직접 사용

business state는 enum, value object, explicit flag, 상태 타입으로 표현한다.

### 12. annotation 기반 nullability는 일관되게 사용
Spring/JSpecify 스타일을 도입하면:
- package/type default를 먼저 정하고
- 예외만 `@Nullable`로 표시한다
- 무의미하게 nullable/non-null annotation을 섞지 않는다

## 프로젝트 기준 요약

- 기본은 non-null
- nullable은 계약으로 명시
- 경계에서 null을 받고 내부에서 제거
- public/constructor 경계는 `requireNonNull` 등으로 빠르게 검증
- 컬렉션/스트림은 null 대신 empty
- Optional과 null 혼용 금지
- DTO/JPA nullable을 domain/application에 그대로 전파 금지
- null을 business meaning으로 사용 금지
