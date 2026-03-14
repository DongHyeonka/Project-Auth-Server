# Project-Auth-Server

인증과 인가를 담당하는 서버입니다.

## 현재 브랜치 범위

- 회원 도메인 구성
- 회원가입 API 구현
- 로그인 API 및 JWT 발급 기반 구현
- JPA 기반 사용자 영속성 어댑터 구성
- Flyway 기반 스키마 마이그레이션 추가
- 이후 Keycloak/OAuth 연동으로 이어질 수 있도록 클린 아키텍처 기반 구조 유지

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

## 모듈 내부 패키지 규칙

- `bootstrap`
  - 애플리케이션 조립과 설정만 둡니다.
  - 예: `config/auth`, `config/openapi`
- `application`
  - 기능별 유스케이스를 기준으로 둡니다.
  - 유스케이스 안에서 `port/in`, `port/out`으로 구분합니다.
  - 예: `auth/login/port/in`, `auth/login/port/out`, `user/signup/port/in`, `user/signup/port/out`, `support/exception`
- `domain`
  - 도메인 개념별로 묶습니다.
  - 예: `user/model`, `user/exception`, `user/service`
- `presentation`
  - 외부 진입점도 기능별로 나눕니다.
  - 예: `auth/controller`, `auth/dto`, `user/controller`, `support/exception`
- `infrastructure`
  - 기술 구현과 대상 자원 기준으로 나눕니다.
  - 예: `persistence/user/entity`, `persistence/user/repository`, `persistence/user/mapper`, `security/password`, `security/token`

새 기능을 추가할 때는 먼저 `기능(auth, user 등)`을 고르고, 그 안에서 `controller`, `dto`, `service`, `exception`처럼 책임에 맞는 위치에 배치하는 것을 기본 규칙으로 삼습니다.

## 현재 구현 방향

- `bootstrap -> presentation, infrastructure`
- `presentation -> application, common`
- `infrastructure -> application, domain`
- `application -> domain`
- `domain -> none`
- `common -> none`

- 실행 가능한 Spring Boot 모듈은 `bootstrap` 하나만 둡니다.
- 현재 브랜치에서는 PostgreSQL + JPA + Flyway 기준으로 회원가입/로그인 흐름을 검증합니다.
- 응답은 공통 응답 형식으로 감싸서 반환합니다.
- JWT는 auth-server가 직접 발급하고 `issuer`, `secret`, `expiration`은 설정값으로 관리합니다.
- JPA는 `ddl-auto=validate`로만 두고, 스키마 변경은 Flyway 스크립트로 관리합니다.

## 현재 브랜치에서 확인할 수 있는 기능

- 회원 도메인, 값 객체, 회원가입/로그인 유스케이스
- 회원가입 API, 로그인 API, 공통 응답 구조
- JPA 사용자 엔티티, Spring Data JPA 저장소, 영속성 매퍼
- Flyway 마이그레이션 스크립트
- Swagger 기반 API 문서
- Postman 컬렉션 기반 수동 검증

## 실행 방법

```bash
./gradlew :bootstrap:bootRun
```

애플리케이션이 실행되면 기본 포트는 `8080`입니다.

애플리케이션 실행 전에 대상 DB에 스키마가 반영돼 있어야 합니다.

필수 DB 설정은 아래 환경 변수로 덮어쓸 수 있습니다.

- `APP_DATASOURCE_URL`
- `APP_DATASOURCE_USERNAME`
- `APP_DATASOURCE_PASSWORD`

JWT 설정은 아래 환경 변수로 덮어쓸 수 있습니다.

- `APP_SECURITY_JWT_ISSUER`
- `APP_SECURITY_JWT_SECRET`
- `APP_SECURITY_JWT_ACCESS_TOKEN_EXPIRATION`

## Flyway 운영 기준

Flyway는 스키마 변경을 추적하기 위해 이번 브랜치에서 적용했습니다. 다만 운영 환경에서 애플리케이션 Pod가 스케일 아웃될 때마다 마이그레이션을 시도하게 두는 구조는 지양합니다.

이 프로젝트는 기본적으로 애플리케이션 시작 시 Flyway를 실행하지 않습니다.

- 기본값: `APP_PERSISTENCE_MIGRATION_RUN_ON_STARTUP=false`
- 일반 애플리케이션 Pod: `false`
- 마이그레이션 전용 Job/배포 단계: `true`

즉, 운영에서는 보통 아래 순서로 가져갑니다.

1. 마이그레이션 전용 Job 또는 CI/CD 단계가 DB에 먼저 붙어서 Flyway를 실행
2. 마이그레이션이 끝난 뒤 애플리케이션 Pod를 롤아웃

로컬에서 마이그레이션만 실행하고 싶다면, 같은 애플리케이션 이미지를 사용하더라도 아래처럼 별도 실행 컨텍스트로 분리하는 방식을 권장합니다.

```bash
APP_PERSISTENCE_MIGRATION_RUN_ON_STARTUP=true \
SPRING_MAIN_WEB_APPLICATION_TYPE=none \
./gradlew :bootstrap:bootRun
```

이 방식은 “앱 서버가 뜰 때마다 Flyway를 돈다”가 아니라, “DB에 대해 한 번만 실행하는 마이그레이션 프로세스”를 따로 두는 운영 형태를 연습하기 위한 기준입니다.

Kubernetes에서 같은 이미지를 마이그레이션 전용 Job으로 분리하는 예시는 `docs/k8s/auth-db-migration-job.yaml` 파일을 참고하면 됩니다.

## API 문서

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

현재 브랜치에서는 회원가입 API와 로그인 API를 기준으로 문서를 확인할 수 있습니다.

## Postman 사용 방법

`docs/postman/auth-core.postman_collection.json` 파일을 Postman에 import 하면 바로 테스트할 수 있습니다.

컬렉션에는 아래 요청이 포함되어 있습니다.

- 회원가입 성공
- 로그인 성공
- 회원가입 중복 이메일
- 로그인 인증 실패
- 회원가입 요청값 검증 실패

기본 변수는 `baseUrl=http://localhost:8080` 으로 설정되어 있습니다.

## 테스트

```bash
./gradlew test
```

테스트 범위는 다음을 포함합니다.

- 회원가입 유스케이스 단위 테스트
- 로그인 유스케이스 단위 테스트
- H2 기반 사용자 영속성 통합 테스트
- 로그인 컨트롤러 테스트
- 회원가입 컨트롤러 테스트
- Swagger OpenAPI 노출 통합 테스트
