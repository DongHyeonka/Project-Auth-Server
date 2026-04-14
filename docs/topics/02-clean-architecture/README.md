# Clean Architecture

## 개요

이 주제는 현재 저장소의 레이어 구조가 왜 필요한지와, `bootstrap`이 조립 지점으로 분리된 이유를 정리합니다.

## 현재 프로젝트 맥락

- `domain`, `application`, `presentation`, `infrastructure`, `bootstrap` 모듈로 나뉩니다.
- 의존 방향은 안쪽으로만 흐르도록 제한합니다.
- ArchUnit 테스트로 규칙을 검증합니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-principles.md](./01-principles.md) | 레이어 경계와 조립 원칙의 요약 |
| [02-error-handling.md](./02-error-handling.md) | 예외 처리 구조, 계층별 책임, 로깅/추적 전략 |

## 관련 문서

- [Architecture Overview](../../architecture/README.md)

## 다음에 확장할 문서

- `03-layer-structure.md`
- `04-practical-application.md`
- `05-adr-why-clean-architecture.md`
