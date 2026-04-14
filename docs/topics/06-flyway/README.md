# Flyway

## 개요

이 주제는 DB migration을 애플리케이션 웹 실행과 분리해 관리하는 현재 전략을 정리합니다.

## 현재 프로젝트 맥락

- `bootstrap`에 Flyway 설정이 존재합니다.
- 웹 앱과 별도로 `MigrationApplication`이 있습니다.
- migration은 운영 절차와 실패 반경을 분리하는 관점에서 다룹니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-migration-strategy.md](./01-migration-strategy.md) | 전용 migration 실행 경로를 둔 이유와 구조 |

## 다음에 확장할 문서

- `02-versioning.md`
- `03-rollback.md`
- `04-runbook.md`
