# Flyway 마이그레이션 전략

## Why

DB migration은 웹 요청 처리와 다른 실패 특성을 가지므로, 웹 애플리케이션과 같은 실행 경로에 묶으면 운영 판단이 어려워집니다.

## What

현재 저장소는 `MigrationApplication`을 별도 진입점으로 두고, `WebApplicationType.NONE`으로 migration만 수행할 수 있게 구성합니다.

## How

- `FlywayConfiguration`이 Flyway를 조립
- `MigrationApplication`이 필요 시 `flyway.migrate()` 실행
- 완료 후 애플리케이션 컨텍스트를 종료

## Result

웹 앱을 띄우지 않고 migration만 실행할 수 있어 운영 절차를 더 명확히 나눌 수 있습니다.
