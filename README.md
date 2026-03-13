# Project-Auth-Server

인증과 인가를 담당하는 서버입니다.

## 현재 브랜치 범위

- 회원 도메인 구성
- 회원가입 API 구현
- 요청/응답 DTO 및 Mapper 구성
- 이후 인증 흐름으로 이어질 수 있도록 클린 아키텍처 기반 구조 유지

## 모듈 구성

- `bootstrap`
  - 애플리케이션 실행 모듈입니다.
  - Spring Boot 설정과 런타임 구성을 담당합니다.
- `presentation`
  - Controller, 요청/응답 DTO, Mapper를 담당합니다.
  - 외부 요청을 애플리케이션 유스케이스로 전달하는 진입점입니다.
- `application`
  - 유스케이스, 커맨드/결과 DTO, 포트를 담당합니다.
  - 도메인 규칙을 조합해 실제 요청 흐름을 처리합니다.
- `domain`
  - 엔티티, 값 객체, 도메인 서비스, 도메인 예외를 담당합니다.
  - 특정 프레임워크나 저장소 구현에 의존하지 않습니다.
- `infrastructure`
  - 영속성 어댑터, 외부 시스템 연동, 보안 연동 구현을 담당합니다.
  - Keycloak, DB, Kafka 같은 외부 자원과 맞닿는 계층입니다.
- `common`
  - 공통 응답 모델과 외부 계층에서 함께 사용하는 공통 요소를 담당합니다.

## 현재 구현 방향

- `bootstrap -> presentation, infrastructure`
- `presentation -> application, common`
- `infrastructure -> application, domain`
- `application -> domain`
- `domain -> none`
- `common -> none`

- 실행 가능한 Spring Boot 모듈은 `bootstrap` 하나만 둡니다.
- 현재 브랜치에서는 DB 대신 인메모리 저장소로 회원가입 흐름을 검증합니다.
- 응답은 공통 응답 형식으로 감싸서 반환합니다.

## 현재 브랜치에서 확인할 수 있는 기능

- 회원 도메인, 값 객체, 회원가입 유스케이스
- 회원가입 API와 공통 응답 구조
- Swagger 기반 API 문서
- Postman 컬렉션 기반 수동 검증

## 실행 방법

```bash
./gradlew :bootstrap:bootRun
```

애플리케이션이 실행되면 기본 포트는 `8080`입니다.

## API 문서

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

현재 브랜치에서는 회원가입 API 한 개를 기준으로 문서를 확인할 수 있습니다.

## Postman 사용 방법

`docs/postman/user-sign-up.postman_collection.json` 파일을 Postman에 import 하면 바로 테스트할 수 있습니다.

컬렉션에는 아래 요청이 포함되어 있습니다.

- 회원가입 성공
- 회원가입 중복 이메일
- 회원가입 요청값 검증 실패

기본 변수는 `baseUrl=http://localhost:8080` 으로 설정되어 있습니다.

## 테스트

```bash
./gradlew test
```

테스트 범위는 다음을 포함합니다.

- 회원가입 유스케이스 단위 테스트
- 회원가입 컨트롤러 테스트
- Swagger OpenAPI 노출 통합 테스트
