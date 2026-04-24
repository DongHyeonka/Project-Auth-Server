# Keycloak Claim / Role 설계

## Why

Resource Server는 token을 검증한 뒤 어떤 claim을 내부 principal로 신뢰할지 정해야 합니다.
모든 claim을 그대로 비즈니스 계층에 넘기면 Keycloak 계약 변경이 내부 로직 전체로 번집니다.

## What

auth-server가 사용하는 최소 claim은 다음입니다.

| Claim | 용도 | 내부 모델 |
|------|------|-----------|
| `sub` | Keycloak 사용자 고정 식별자 | `provider_subject` |
| `email` | 내부 사용자 신규 생성과 충돌 검사 | `UserEmail` |
| `name` | 내부 사용자 표시 이름 | `UserName` |
| `preferred_username` | `name`이 없을 때 fallback | `AuthenticatedUser.name` |
| `scope` | OAuth2 scope authority | `SCOPE_*` |
| `realm_access.roles` | Realm role authority | `ROLE_*` |

## How

`KeycloakJwtAuthenticationConverter`가 JWT claim을 `AuthenticatedUser`로 변환합니다.
controller는 `@CurrentUser`로 이 principal을 받고, application에는 `KeycloakUserClaims(subject, email, name)`만 전달합니다.
`KeycloakUserClaimsValidator`는 claim이 비어 있거나 `UserEmail`/`UserName` 도메인 규칙에 어긋나면 `InvalidKeycloakClaimsException(AUTH-003, HTTP 400)`으로 번역해 Keycloak 오염 클레임이 비즈니스 로직까지 흘러들지 않게 차단합니다.

권장 realm role:

| Keycloak role | Spring authority | 용도 |
|---------------|------------------|------|
| `user` | `ROLE_user` | 일반 인증 사용자 |
| `admin` | `ROLE_admin` | 운영/관리 API |

역할 이름은 Keycloak realm에서 소유합니다. auth-server는 role 값을 새로 발급하거나 DB 값으로 권한을 덮어쓰지 않습니다.
현재 `/api/v1/auth/me`는 `GET` 요청에 대해 `ROLE_user`가 필요합니다. 따라서 Keycloak realm의 self-service registration 기본 역할에는 `user`를 포함해야 하며, 역할이 없는 token은 인증은 성공하더라도 `AUTH-002`로 거절됩니다.

## Result / Trade-offs

- token 검증 결과는 신뢰하되, 내부 사용자 조회는 `(provider=KEYCLOAK, provider_subject=sub)` 기준으로만 수행합니다.
- email은 자동 계정 연결 키로 쓰지 않습니다. 연결된 내부 사용자가 없으면 자동 생성 없이 `AUTH-004` 404를 반환합니다.
- 실무 권장안은 “IdP subject를 immutable external identity로 삼고, email은 표시/연락/초기 등록 보조값으로만 다루는 것”입니다. email은 변경될 수 있고 재사용될 수 있으므로 권한 있는 내부 계정 연결 키로 쓰면 위험합니다.
- 표시용 `email`/`name`은 token claim에서 왔더라도 DB에 저장된 값이 authoritative입니다. 따라서 `/api/v1/auth/me` 응답은 DB 값을 노출하고, 권한(`authorities`)은 token claim에서 그대로 가져옵니다.
