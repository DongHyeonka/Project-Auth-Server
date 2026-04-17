# Migration 기준

## 1. 목적

이 문서는 PostgreSQL 스키마 변경을 운영 중에도 안전하게 배포 가능한 방식으로 설계하고 실행하는 기준을 정의한다. 목표는 다음과 같다. 첫째, migration을 “DDL 한 번 실행”이 아니라 애플리케이션 배포와 함께 움직이는 변경 절차로 다룬다. 둘째, 긴 테이블 rewrite, 강한 잠금, 비호환 rename/drop, 실패 시 복구 곤란한 변경을 줄인다. 셋째, 인덱스/제약/컬럼 추가/타입 변경을 공식 문서가 보장하는 동작 위에서 단계적으로 적용한다. PostgreSQL은 `ALTER TABLE` 하위 명령마다 필요한 lock level이 다르고, 명시되지 않으면 `ACCESS EXCLUSIVE`가 기본이라고 설명한다. 또한 `CREATE INDEX CONCURRENTLY`, `NOT VALID`/`VALIDATE CONSTRAINT`, 빠른 `ADD COLUMN ... DEFAULT` 같은 운영 친화적 경로를 공식적으로 제공한다.

## 2. 근거 수준

- Official: PostgreSQL 공식 문서에서 직접 확인되는 내용
- Official + Practice: 공식 동작 위에 일반적인 운영 best practice를 결합한 내용
- Project Recommendation: 이 프로젝트 구조와 운영 방식에 맞춘 규칙

이번 문서는 PostgreSQL의 `ALTER TABLE`, `CREATE INDEX`, Modifying Tables, Transaction Isolation, PostgreSQL 11 release notes를 기준으로 작성한다. 특히 운영 중 영향도를 줄이는 핵심 기능은 `CREATE INDEX CONCURRENTLY`, `ADD CONSTRAINT ... NOT VALID` + `VALIDATE CONSTRAINT`, PostgreSQL 11 이후의 “상수 기본값 컬럼 추가 시 테이블 rewrite 회피”다.

## 3. 기본 원칙

### 3.1 migration은 expand-contract를 기본으로 한다

프로젝트 기본 원칙은 한 번에 바꾸고 바로 치우는 방식보다, 호환 가능한 단계를 나눠 배포하는 방식이다. PostgreSQL 공식 문서가 직접 “expand-contract”라는 표현을 쓰지는 않지만, 운영 친화적 DDL 경로는 대부분 additive change → 데이터 보정 → 제약 검증 → cleanup 순서에 맞춰 제공된다. 예를 들어 컬럼 추가는 빠르게 할 수 있고, 기본값 변경은 기존 row를 바꾸지 않으며, 제약은 `NOT VALID`로 추가 후 나중에 검증할 수 있고, 인덱스는 `CONCURRENTLY`로 따로 만들 수 있다. 프로젝트에서는 이 공식 동작들을 조합해 additive first, destructive later를 기본값으로 둔다.

### 3.2 migration은 “DDL 문법 가능 여부”가 아니라 “운영 중 lock/scan/rewrite 영향”으로 평가한다

같은 `ALTER TABLE`이라도 어떤 하위 명령은 빠르고, 어떤 것은 긴 scan이나 rewrite를 유발한다. PostgreSQL은 `ALTER TABLE`의 하위 명령마다 lock requirement가 다르며, 명시되지 않으면 `ACCESS EXCLUSIVE`가 기본이라고 설명한다. 또한 타입 변경은 보통 테이블과 인덱스를 rewrite/rebuild하고, 큰 테이블에서는 시간과 디스크를 크게 사용할 수 있다고 설명한다. 프로젝트에서는 migration review 시 SQL 문법보다 잠금 수준, rewrite 여부, 전체 스캔 여부, 디스크 추가 사용량을 먼저 본다.

### 3.3 backward-compatible migration을 기본으로 한다

rename, drop, 의미 변경은 대개 배포 순서에 민감하다. 반면 컬럼 추가, 인덱스 추가, 새 제약의 단계적 검증은 기존 애플리케이션과 더 쉽게 공존할 수 있다. PostgreSQL 공식 문서도 컬럼 추가, 기본값 변경, 제약 검증, 인덱스 추가를 각각 독립된 단계로 지원한다. 프로젝트에서는 앱 선배포/DB 선배포 순서가 뒤바뀌어도 일정 기간 함께 버틸 수 있는 migration을 기본값으로 둔다. 이는 공식 기능 위에 얹는 운영 best practice다.

## 4. migration 단계 기준

### 4.1 기본 4단계: 추가 → 이중 호환 → 검증/전환 → 제거

프로젝트 권장 기본 흐름은 다음과 같다.

- 새 컬럼/인덱스/제약을 호환 가능한 형태로 추가한다.
- 애플리케이션이 구구조와 신구조를 함께 읽거나 함께 쓰도록 전환한다.
- backfill과 검증을 끝낸 뒤 제약을 강화한다.
- 마지막 배포에서 구컬럼/구제약/구코드를 제거한다.

이 흐름은 PostgreSQL의 빠른 컬럼 추가, `NOT VALID` 제약, `VALIDATE CONSTRAINT`, `CREATE INDEX CONCURRENTLY`, 기존 인덱스를 제약으로 승격하는 기능과 잘 맞는다.

### 4.2 destructive change는 마지막 단계로 미룬다

`DROP COLUMN`, 의미 변경, 이름 변경, 타입 변경은 호환성 파손 위험이 높다. PostgreSQL은 `DROP COLUMN`이 빠르지만 즉시 디스크 공간을 회수하지 않고, 타입 변경은 보통 rewrite를 유발한다고 설명한다. 따라서 프로젝트에서는 destructive change를 초기에 넣지 않고, 모든 애플리케이션이 새 구조를 사용한다는 것이 검증된 뒤 마지막 단계로 미룬다.

## 5. 컬럼 추가/변경 기준

### 5.1 nullable 컬럼 추가는 기본적으로 안전한 additive change다

PostgreSQL은 `ALTER TABLE ... ADD COLUMN`이 기본적으로 새 컬럼을 추가하고, 기본값이 없으면 기존 row에서는 `NULL`처럼 보인다고 설명한다. 이 경우 rewrite가 필요 없다. 프로젝트에서 새 필드를 도입할 때 기본 경로는 nullable 컬럼 추가 → 앱 쓰기 시작 → backfill → 제약 강화다.

### 5.2 상수 기본값이 있는 컬럼 추가는 PostgreSQL 11+에서 빠르게 처리될 수 있다

PostgreSQL 11 release notes와 현재 `ALTER TABLE` 문서는, `ADD COLUMN`에 non-volatile constant default가 있으면 전체 테이블 rewrite를 피할 수 있다고 설명한다. 현재 문서 표현으로는 non-volatile default 값이 metadata에 저장되고, 기존 row는 테이블이 나중에 rewrite될 때 물리적으로 반영된다. 프로젝트에서는 PostgreSQL 11+ 기준이라면 상수 기본값 컬럼 추가를 안전한 1차 선택지로 볼 수 있다. 다만 버전 호환성과 도메인/identity/generated column 여부는 반드시 확인한다.

### 5.3 volatile default, identity, stored generated column 추가는 신중히 본다

PostgreSQL은 `clock_timestamp()` 같은 volatile default, stored generated column, identity column, 제약이 있는 domain type column 추가가 테이블과 인덱스 전체 rewrite를 유발한다고 설명한다. 따라서 프로젝트에서는 이런 변경을 단순 additive change로 간주하지 않는다. 운영 테이블에는 보통 컬럼을 먼저 nullable/no default로 추가하고, backfill 후 default나 별도 정책을 도입하는 방식을 우선 검토한다.

### 5.4 SET DEFAULT는 기존 row를 바꾸지 않는다

PostgreSQL은 `ALTER COLUMN ... SET DEFAULT`가 이후의 INSERT/UPDATE에만 영향을 주고, 기존 row 값은 바꾸지 않는다고 설명한다. 따라서 “기본값을 바꿨으니 과거 데이터도 다 맞춰졌다”라고 해석하면 안 된다. 프로젝트에서는 default 변경과 historical data correction을 분리해서 설계한다.

### 5.5 타입 변경은 기본적으로 rewrite 후보로 본다

PostgreSQL은 기존 컬럼 타입 변경이 보통 테이블과 인덱스를 rewrite/rebuild하고, 큰 테이블에서는 상당한 시간과 임시 디스크를 요구할 수 있다고 설명한다. 예외적으로 binary coercible change처럼 rewrite가 필요 없는 경우도 있지만, 기본 가정은 “위험한 변경”이다. 프로젝트에서는 타입 변경을 직접 `ALTER`보다 신규 컬럼 추가 → backfill → 애플리케이션 전환 → 구컬럼 제거 방식으로 우선 검토한다.

### 5.6 rename은 기술적으로 빠를 수 있어도 배포 호환성 관점에서는 보수적으로 다룬다

PostgreSQL은 컬럼명/테이블명 rename을 지원한다. 하지만 rename은 애플리케이션 SQL, ORM 매핑, ETL, 운영 스크립트와 동시에 맞물린다. 따라서 프로젝트에서는 rename을 단순 메타데이터 변경으로만 보지 않고, dual-write/dual-read가 불가능한 비호환 변경으로 간주한다. 가능하면 새 이름의 컬럼을 추가하고 단계적으로 이전하는 방식을 우선 검토한다. 공식 문서는 rename 자체를 지원하지만, 이 보수적 해석은 프로젝트 운영 best practice다.

## 6. backfill 기준

### 6.1 backfill은 schema change와 분리된 단계로 본다

PostgreSQL 문서도 volatile default 대량 반영이 길 수 있으니, 컬럼을 먼저 추가하고 이후 `UPDATE`로 채운 뒤 default/constraint를 추가하는 방식을 제안한다. 프로젝트에서도 backfill은 DDL 한 문장에 숨기지 않고, 명시적 데이터 이행 단계로 분리한다.

### 6.2 대량 backfill은 작은 배치로 나눈다

이 원칙은 운영 best practice다. PostgreSQL 공식 문서가 “작은 배치”를 강제하지는 않지만, rewrite/scan/lock 비용이 큰 변경을 경고하고 있고, 긴 트랜잭션은 vacuum, bloat, contention에 불리하다. 프로젝트에서는 대형 테이블 backfill을 작은 배치 + 명시적 진행률 + 재실행 가능 구조로 나눈다. 이 규칙은 공식 문서의 rewrite/scan 비용 설명 위에 얹는 운영 권장안이다.

### 6.3 backfill 완료 확인 없이 NOT NULL/강한 제약으로 바로 올리지 않는다

PostgreSQL은 `SET NOT NULL`이나 즉시 검증되는 제약 추가가 테이블 scan을 요구할 수 있다고 설명한다. 따라서 프로젝트에서는 backfill이 끝났다는 증거 없이 곧바로 강한 제약을 걸지 않는다. 먼저 null/invalid row가 0건임을 점검하고, 그 다음에 제약을 강화한다.

## 7. 제약 추가 기준

### 7.1 큰 테이블의 FK/CHECK/NOT NULL 추가는 NOT VALID + VALIDATE CONSTRAINT를 기본 검토한다

PostgreSQL은 `ADD CONSTRAINT ... NOT VALID`가 기존 row 전체 검사를 생략하고 즉시 commit될 수 있으며, 이후 `VALIDATE CONSTRAINT`로 기존 데이터 검증을 수행할 수 있다고 설명한다. 또한 validation은 `SHARE UPDATE EXCLUSIVE` lock으로 수행되어, 제약 추가 시점보다 concurrent update에 미치는 영향이 더 작다. 프로젝트에서는 큰 테이블의 FK/CHECK/NOT NULL 추가에 이 경로를 기본값으로 검토한다.

### 7.2 SET NOT NULL은 증명 가능한 CHECK와 함께 단계적으로 올릴 수 있다

PostgreSQL은 `SET NOT NULL`이 보통 전체 테이블을 스캔하지만, 이미 존재하는 valid `CHECK` constraint가 null 불가능함을 증명하면 그 스캔을 건너뛸 수 있다고 설명한다. 프로젝트에서는 운영 중 큰 테이블에 `NOT NULL`을 올릴 때,

- `CHECK (col IS NOT NULL) NOT VALID` 추가
- backfill
- `VALIDATE CONSTRAINT`
- `ALTER COLUMN SET NOT NULL`
- 보조 `CHECK` 제거

순서를 기본 검토한다.

### 7.3 UNIQUE/PK는 기존 인덱스를 활용해 승격할 수 있다

PostgreSQL은 기존 unique index를 이용해 `PRIMARY KEY`나 `UNIQUE` 제약을 빠르게 추가할 수 있다고 설명한다. 또한 운영 중 장시간 update block을 줄이려면 먼저 `CREATE UNIQUE INDEX CONCURRENTLY`로 인덱스를 만들고, 그 뒤 `ALTER TABLE ... ADD CONSTRAINT ... USING INDEX`로 제약으로 바꾸는 방식을 권장 예시로 제시한다. 프로젝트에서는 큰 테이블의 unique/pk 추가에 이 경로를 기본 검토한다.

### 7.4 FK 추가는 잠금 범위도 같이 본다

PostgreSQL은 `ADD FOREIGN KEY`가 일반 제약 추가보다 약한 `SHARE ROW EXCLUSIVE` lock을 사용하고, 참조 대상 테이블에도 같은 수준의 lock을 잡는다고 설명한다. 프로젝트에서는 FK migration review 시 양쪽 테이블 영향을 함께 검토한다.

## 8. 인덱스 migration 기준

### 8.1 운영 중 새 인덱스는 CREATE INDEX CONCURRENTLY를 기본 검토한다

PostgreSQL은 `CREATE INDEX CONCURRENTLY`가 write를 막지 않고 인덱스를 만들 수 있어 production environment에 유용하다고 설명한다. 대신 두 번의 테이블 스캔, 여러 대기 구간, 더 큰 CPU/I/O 비용이 있고, 일반 index build보다 오래 걸린다. 프로젝트 기본값은 운영 테이블 인덱스 추가 = 먼저 `CONCURRENTLY` 검토다.

### 8.2 CREATE INDEX CONCURRENTLY는 트랜잭션 블록 안에서 실행할 수 없다

PostgreSQL은 regular `CREATE INDEX`는 transaction block 안에서 가능하지만, `CREATE INDEX CONCURRENTLY`는 불가능하다고 명시한다. 따라서 migration 도구나 배포 파이프라인은 non-transactional migration step을 지원해야 한다. 프로젝트에서는 concurrent index build를 일반 DDL 묶음과 같은 트랜잭션 안에 넣지 않는다.

### 8.3 concurrent build 실패 후 INVALID 인덱스를 방치하지 않는다

PostgreSQL은 concurrent index build가 deadlock이나 uniqueness violation 등으로 실패하면 `INVALID` 인덱스를 남길 수 있고, 이 인덱스는 query에는 쓰이지 않지만 update overhead는 계속 발생한다고 설명한다. 또한 권장 복구 방법은 해당 인덱스를 drop하고 다시 시도하거나 `REINDEX INDEX CONCURRENTLY`를 쓰는 것이라고 안내한다. 프로젝트에서는 failed concurrent migration 뒤 `INVALID` 인덱스 정리 확인을 필수로 둔다.

### 8.4 unique concurrent index는 “아직 valid가 아니어도” uniqueness를 일찍 강제할 수 있다

PostgreSQL은 unique index를 concurrently build할 때, 두 번째 scan이 시작되면 다른 트랜잭션에 대해 uniqueness가 이미 강제될 수 있고, 최종적으로 index build가 실패해도 invalid index가 uniqueness를 계속 강제할 수 있다고 설명한다. 프로젝트에서는 unique concurrent index migration을 완전히 무해한 준비 작업으로 오해하지 않는다. 실패 시 후속 영향까지 함께 점검한다.

## 9. destructive change 기준

### 9.1 DROP COLUMN은 빠르지만 즉시 디스크를 줄이지 않는다

PostgreSQL은 `DROP COLUMN`이 컬럼을 SQL에서 보이지 않게 할 뿐, 물리 저장공간은 기존 row update가 진행되며 점진적으로 회수된다고 설명한다. 따라서 프로젝트에서는 drop 후 즉시 디스크 이익을 기대하지 않는다. 또한 drop은 backward compatibility를 깨므로 cleanup phase 마지막에만 허용한다.

### 9.2 destructive migration은 “코드 미사용 확인” 이후에만 수행한다

이 항목은 운영 best practice다. PostgreSQL은 컬럼 drop이나 rename을 지원하지만, DB가 애플리케이션 사용 여부를 대신 판단해 주지는 않는다. 프로젝트에서는

- 애플리케이션이 더 이상 해당 컬럼/인덱스/제약을 사용하지 않음
- dual-write/dual-read가 끝남
- 모니터링 및 쿼리 검증 완료

이후에만 destructive migration을 허용한다. 공식 기능 위에 얹는 프로젝트 권장안이다.

## 10. 배포 및 실행 기준

### 10.1 한 migration 파일에 잠금 요구가 크게 다른 작업을 무심코 섞지 않는다

PostgreSQL은 여러 `ALTER TABLE` subcommand를 한 statement에 넣으면 가장 강한 lock requirement를 따른다고 설명한다. 따라서 프로젝트에서는

- 빠른 metadata 변경
- 오래 걸리는 validate
- concurrent index build
- destructive cleanup

을 같은 파일/같은 트랜잭션으로 무심코 합치지 않는다.

### 10.2 non-transactional step과 transactional step을 구분한다

`CREATE INDEX CONCURRENTLY`는 transaction block 안에서 실행할 수 없고, 일반 `ALTER TABLE`은 보통 transaction 안에서 실행된다. 프로젝트에서는 migration 도구가 transactional migration과 non-transactional migration을 구분하게 설계한다. 이 구분이 없으면 운영 친화적 경로를 쓰기 어렵다.

### 10.3 migration은 재실행 가능성과 중단 복구를 고려한다

PostgreSQL은 concurrent index build 실패 시 invalid index가 남을 수 있고, 제약 검증은 단계적으로 수행할 수 있다고 설명한다. 프로젝트에서는 migration을 “한 번에 무조건 성공” 전제로 쓰지 않고, 중간 실패 후 상태 점검과 재실행 경로가 있는 구조를 기본으로 한다.

## 11. 프로젝트 권장안

### 11.1 기본 패턴: additive first, validated later, destructive last

프로젝트 기본 migration 패턴은 다음과 같다.

- 새 컬럼/인덱스/제약을 먼저 추가
- 앱을 신구조와 호환되게 배포
- backfill 수행
- 제약 검증 및 강도 상승
- 마지막에 rename/drop/cleanup

이 패턴은 PostgreSQL이 제공하는 빠른 `ADD COLUMN`, `SET DEFAULT`, `NOT VALID`/`VALIDATE`, `CREATE INDEX CONCURRENTLY`, `USING INDEX` 경로와 가장 잘 맞는다.

### 11.2 large table migration은 “즉시 완료”보다 “운영 영향 최소화”를 우선한다

정식 문서도 rewrite, validation scan, concurrent build의 시간과 비용 차이를 분명히 설명한다. 프로젝트에서는 migration 시간을 줄이는 것보다 write block 최소화, rollback 복구성, 배포 호환성을 더 우선한다.

## 12. 금지 규칙

다음은 기본 금지다.

- 큰 테이블에 volatile default / identity / stored generated column을 무심코 추가
- 큰 테이블 타입 변경을 rewrite 영향 검토 없이 바로 수행
- FK/CHECK/NOT NULL을 운영 테이블에 즉시 검증 방식으로만 추가
- 운영 인덱스를 `CREATE INDEX`로 바로 만들어 write block을 유발
- `CREATE INDEX CONCURRENTLY`를 트랜잭션 블록 안에 넣기
- failed concurrent build 뒤 `INVALID` 인덱스를 방치
- backfill 없이 바로 `SET NOT NULL`
- 같은 migration에 lock 강도가 크게 다른 작업을 한꺼번에 섞기
- 코드 호환성 검증 없이 rename/drop부터 수행
- destructive change를 rollout 초기에 배치

이 금지 규칙은 PostgreSQL 공식 문서의 lock/rewrite/validation/concurrent index semantics를 운영 best practice로 압축한 것이다.

## 13. 체크리스트

다음 질문에 “예”로 답할 수 있어야 한다.

- 이 migration은 additive 단계와 destructive 단계를 분리했는가?
- 컬럼 추가가 rewrite를 유발하는 형태인지 확인했는가?
- backfill은 DDL과 분리되어 있는가?
- 제약 추가는 `NOT VALID` + `VALIDATE` 경로를 검토했는가?
- `NOT NULL` 강화 전에 데이터가 이미 null-free인지 증명했는가?
- 운영 인덱스는 `CONCURRENTLY` 필요 여부를 검토했는가?
- concurrent index build 실패 시 `INVALID` 인덱스 처리 계획이 있는가?
- migration 도구가 transactional / non-transactional step을 구분할 수 있는가?
- rename/drop이 모든 애플리케이션 배포 이후 cleanup 단계인지 확인했는가?
- lock level, rewrite, disk usage, rollback/retry 경로를 리뷰했는가?
