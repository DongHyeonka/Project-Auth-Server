# Clean Architecture 원칙

## Why

인증, 응답 계약, 외부 시스템 연동이 섞인 서버는 금방 변경 영향 범위를 통제하기 어려워집니다.  
그래서 이 저장소는 비즈니스 규칙과 기술 구현을 분리하는 구조를 기본 원칙으로 둡니다.

## What

- `domain`: 순수 도메인 규칙
- `application`: 유스케이스와 포트
- `presentation`: HTTP 계약
- `infrastructure`: 기술 구현체
- `bootstrap`: 실제 조립과 presentation이 직접 알 수 없는 기술 예외의 경계 번역

## How

이 원칙은 설명만으로 유지하지 않고 `LayerDependencyArchitectureTest`로 강제합니다.  
즉 문서와 테스트가 함께 구조를 지키는 방식입니다.

## Result

현재 구조 덕분에 응답 모델, 인증 설정, persistence 구현이 서로 침범하는 범위를 줄일 수 있습니다.
