# 표준 적용 우선순위와 충돌 해결 규칙

## 목적

이 문서는 프로젝트 내 모든 standards/examples 문서를 **어떤 상황에서 어떤 순서로 읽고 적용할지** 정의한다.

이 문서의 목적은 다음과 같다.

- 작업마다 필요한 표준이 빠지지 않게 한다
- 여러 표준이 동시에 걸릴 때 우선순위를 명확히 한다
- AGENTS.md를 짧게 유지하면서도 실제 표준 적용 누락을 막는다
- AI가 “문서가 있었지만 읽지 않았다”는 상태를 줄인다

---

## 기본 원칙

- `AGENTS.md`는 **백과사전이 아니라 라우터**다
- 실제 규칙의 source of truth는 `docs/standards/**` 이다
- 실제 구현의 기준 예시는 `docs/examples/**` 이다
- architecture 문서는 레이어 책임과 의존 방향의 최상위 기준이다
- examples는 standards를 대체하지 않는다. 항상 **standards -> examples** 순서로 본다

---

## 우선순위

충돌 시 아래 순서대로 우선한다.

1. 직접적인 system / developer / user instruction
2. 더 깊게 중첩된 `AGENTS.md`
3. 더 바깥의 `AGENTS.md`
4. `/docs/architecture/README.md`
5. 이 문서에 의해 강제되는 relevant standards
6. relevant examples
7. 현재 코드베이스의 기존 패턴
8. 개인 선호 / 임시 편의

즉:
- “기존 코드가 이렇게 되어 있다”는 이유만으로 architecture나 standards를 깨면 안 된다
- examples가 standards와 충돌하면 standards를 우선한다
- 표준이 없을 때만 기존 코드 패턴을 참고한다

---

## 작업 시작 절차

모든 작업은 아래 순서로 진행한다.

1. 수정 대상 레이어를 식별한다
2. 경계 crossing 여부를 식별한다
   - HTTP 경계
   - transaction 경계
   - DB query 경계
   - external API 경계
   - security/authentication 경계
3. root AGENTS와 nearest module AGENTS를 읽는다
4. 해당 작업에 필요한 standards를 읽는다
5. 필요한 경우 examples를 읽는다
6. 구현 전 “적용할 표준 목록”을 짧게 정리한다
7. 구현한다
8. 구현 후 standards 위반 여부를 다시 확인한다

---

## 표준 읽기 규칙

### 전역 기준: 항상 먼저 읽는다

아래 문서는 모든 작업 전에 기본적으로 적용된다.

- `/docs/standards/language/stream.md`
- `/docs/standards/language/optional.md`
- `/docs/standards/language/null.md`
- `/docs/standards/language/collections-immutability.md`
- `/docs/standards/language/enum-constants.md`
- `/docs/standards/language/time.md`
- `/docs/standards/language/exceptions.md`
- `/docs/standards/language/duplication.md`
- `/docs/standards/language/javadoc.md`

### 상황별 기준: 작업 유형에 따라 추가로 읽는다

#### controller / dto / api 응답 / validation / 인증 객체 접근 변경
추가로 읽는다:
- `/docs/standards/web/**`
- `/docs/standards/spring/filter-interceptor-resolver-advice.md`

#### use case / service / transaction / port 변경
추가로 읽는다:
- `/docs/standards/spring/transaction.md`
- `/docs/standards/spring/abstraction.md`

#### external API / client / serialization / timeout / retry 변경
추가로 읽는다:
- `/docs/standards/integration/**`

#### repository / entity / query / lock / migration 변경
추가로 읽는다:
- `/docs/standards/db/**`

#### configuration / bean wiring / security filter / bootstrap adapter 변경
추가로 읽는다:
- `/docs/standards/spring/**`
- `/docs/standards/integration/logging.md`

#### env / profile / docs / runbook / migration 절차 변경
추가로 읽는다:
- `/docs/standards/ops/**`

---

## examples 사용 규칙

examples는 아래 조건을 만족할 때만 사용한다.

- relevant standard를 먼저 읽었다
- example가 같은 레이어/비슷한 책임을 가진다
- architecture와 충돌하지 않는다

examples 사용 규칙:
- examples는 복붙 대상이 아니라 **형태와 책임 분리의 기준**이다
- example가 현재 standard와 충돌하면 example를 버린다
- example가 오래되었거나 애매하면 standard만 따르고 example는 무시한다

---

## 충돌 해결 규칙

### 1. example vs standard
- standard 우선

### 2. 기존 코드 패턴 vs standard
- standard 우선
- 단, 기존 코드가 널리 퍼져 있으면 한 번에 다 고치지 않고 현재 변경 범위에서만 맞춘다

### 3. 모듈 AGENTS vs root AGENTS
- 더 가까운 module AGENTS 우선
- 단, root의 전역 기준을 무시하는 근거로 쓰면 안 된다

### 4. 성능 최적화 vs 가독성
- 측정 근거 없는 성능 주장은 금지
- 기본값은 명확한 코드
- 성능 민감 경로는 측정 결과가 있으면 예외 허용

### 5. 빠른 구현 vs 구조 일관성
- 임시 구현으로 레이어를 깨는 것 금지
- 오늘 편한 구조보다 이후 반복 작업에서 덜 무너지는 구조를 우선

---

## AI 작업 지시 규칙

AI에게 작업을 줄 때는 다음을 포함한다.

- 수정 목표
- 파일 경로 또는 모듈 이름
- 변경 범위
- 관련 standards 파일
- 관련 examples 파일
- 금지사항
- 완료 조건

프롬프트는 이슈처럼 쓴다.
즉:
- 무엇을 바꿀지
- 어디를 바꿀지
- 어떤 기준을 따를지
- 무엇을 하지 말아야 하는지
를 명확히 적는다.

큰 변경은 바로 구현부터 시키지 말고:
1. Ask mode로 구현 계획
2. relevant standards/examples 확인
3. Code mode로 구현
순서로 진행한다.

---

## 표준 누락 방지 규칙

새 standards 파일을 만들면 반드시 아래를 함께 갱신한다.

- root AGENTS의 전역/폴더 라우팅 또는 relevant module AGENTS
- 관련 module AGENTS의 `Read first`
- 관련 examples 연결
- 이 문서의 상황별 기준 목록이 바뀌어야 하는지 검토

즉 파일만 만들고 라우팅하지 않는 것을 금지한다.

---

## 구현 전 체크리스트

- 이 작업의 owning layer는 어디인가?
- 어떤 boundary를 건드리는가?
- 전역 language standard를 읽었는가?
- 이 작업에 필요한 module-specific standard를 읽었는가?
- example는 relevant standard를 읽은 뒤에 봤는가?
- 충돌 시 무엇을 우선할지 명확한가?

---

## 구현 후 체크리스트

- architecture 위반이 없는가?
- 레이어 책임이 흐려지지 않았는가?
- 예외 번역 위치가 맞는가?
- transaction 범위가 맞는가?
- query/lock/migration 기준을 위반하지 않았는가?
- stale Javadoc/docs가 남지 않았는가?
- example를 그대로 복붙해 책임이 섞이지 않았는가?
