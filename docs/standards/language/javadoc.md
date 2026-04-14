# Javadoc 작성 / 수정 / 삭제 기준

## 목적

Javadoc은 코드 설명서가 아니라 **API 계약과 오해 가능성이 있는 의미를 문서화하는 도구**로 사용한다.  
“코드를 읽으면 바로 아는 내용”을 반복하지 않는다.

## 공식 의미

- Javadoc doc comment는 선언 바로 앞에 있어야 인식된다.
- 한 doc comment는 설명(description)과 block tags로 구성된다.
- 첫 요약 문장은 summary/index에 재사용되므로 특히 중요하다.
- override/implement 메서드는 자체 문서가 없으면 상위 문서를 상속할 수 있다.
- visible한 class/member에는 Javadoc이 있는 것이 기본이지만, 정말 단순하고 obvious한 경우나 override는 예외가 될 수 있다.

## 기본 규칙

### 1. Javadoc은 계약을 문서화할 때만 쓴다
다음 중 하나가 있으면 Javadoc을 작성한다.

- public/protected API
- 외부 모듈이 호출하는 계약
- null 허용 여부가 중요함
- 예외 조건/전제조건/후조건이 중요함
- thread-safety, side effect, state change가 중요함
- 경계 조건이나 corner case를 안 쓰면 오해할 수 있음
- 이름만으로는 의미가 충분하지 않음

### 2. “코드를 읽으면 아는 내용”은 쓰지 않는다
다음은 기본 금지한다.

- getter/setter를 그대로 풀어쓴 설명
- 필드명/파라미터명을 문장으로 반복
- 구현을 한 줄씩 설명하는 Javadoc
- 리팩터링 후 쉽게 stale 해질 정보

금지 예:
- `Returns the user name.` 만 있는 자명한 `getUserName()`
- `Sets the value.` 같은 설명

### 3. 첫 줄은 summary로 쓴다
Javadoc 첫 줄은 짧고 독립적으로 읽히는 요약이어야 한다.

기본:
- 요약 1줄
- 필요한 경우 상세 설명
- 그 후 block tags

### 4. 구현 세부보다 호출 계약을 우선
Javadoc은 아래를 더 우선해서 쓴다.

- 무엇을 보장하는가
- 어떤 입력이 허용되는가
- 어떤 경우 실패하는가
- 호출자가 믿어도 되는 동작은 무엇인가
- 반환값의 의미는 무엇인가

다음은 기본 지양한다.
- 내부 알고리즘 설명
- 현재 구현 방식
- 성능 미세 최적화 세부

### 5. `@param`, `@return`, `@throws`는 의미가 있을 때만 정확히 쓴다
태그를 채우기 위한 태그를 금지한다.

기본:
- `@param`: 파라미터 의미/제약/허용 범위/nullable 여부
- `@return`: 반환값 의미, empty/optional/null/ordering/ownership
- `@throws`: 실제 계약상 중요한 예외 조건

### 6. 예외 문서는 “언제 왜 던지는가”를 쓴다
단순히 예외 타입만 나열하지 않는다.

좋은 방향:
- 어떤 입력/상태에서
- 어떤 이유로
- 호출자가 무엇을 기대해야 하는지

### 7. self-explanatory 멤버는 Javadoc 생략 가능
정말 단순하고 obvious한 멤버는 Javadoc을 생략할 수 있다.

예:
- 의미가 완전히 자명한 getter
- record component 중 이름만으로 충분한 경우

단, typical reader가 모를 수 있는 의미가 있으면 생략하지 않는다.

### 8. override는 상속 문서를 우선 활용
override/implement 메서드에서 상위 문서가 충분하면 Javadoc을 반복하지 않는다.

다만 아래 경우에는 다시 쓴다.
- 하위 타입에서 계약이 추가됨
- 예외/부작용/동시성 보장이 달라짐
- 더 좁은 의미가 생김

### 9. Javadoc과 코드가 어긋나면 Javadoc이 잘못된 것
코드가 바뀌면 Javadoc도 같이 수정한다.  
맞출 수 없으면 지운다.

기본 규칙:
- stale Javadoc 금지
- 애매한 Javadoc보다 없는 편이 낫다
- 거짓 문서 금지

### 10. Javadoc은 boundary/API 중심으로 우선 배치
프로젝트에서는 아래 우선순위로 작성한다.

1. public/protected API
2. 외부 호출되는 application/presentation boundary
3. 의미가 어려운 domain type / value object
4. 예외/정책/동시성 규약이 중요한 infrastructure API
5. package-level overview가 필요한 package

### 11. implementation comment로 계약을 설명하지 않는다
class/member의 전체 목적이나 호출 계약을 설명하는 내용이면 `//` 주석 대신 Javadoc으로 쓴다.

### 12. package/class level Javadoc은 구조 설명에 사용
package/class 수준에서는:
- 목적
- 포함 내용
- 관계
- 사용 시 주의점
- 외부 문서 링크
를 설명할 수 있다.

긴 설명은 외부 architecture/spec 문서로 분리하고 링크한다.

### 13. 한 줄 Javadoc은 정말 짧을 때만
한 줄로 끝나는 Javadoc은:
- 매우 짧고
- block tag가 없고
- 요약만으로 충분할 때만 사용한다

### 14. 포맷보다 의미를 우선하되 형식은 일관되게
기본 형식:
- summary
- 빈 줄
- 상세 설명 (필요 시)
- block tags (`@param`, `@return`, `@throws`, `@deprecated` 순)

### 15. Javadoc은 examples보다 계약 우선
examples/tutorial 성격의 설명은 docs/examples나 외부 문서가 더 적합할 수 있다.  
Javadoc은 먼저 API contract를 충실히 담는다.

## 프로젝트 기준 요약

- Javadoc은 계약/제약/의미를 문서화할 때만 작성
- 자명한 설명, 구현 반복 설명 금지
- 첫 줄 summary 필수
- `@param/@return/@throws`는 의미 있을 때만 정확히 작성
- self-explanatory 멤버와 override는 생략 가능
- stale Javadoc 금지
- 맞출 수 없으면 수정하거나 삭제
