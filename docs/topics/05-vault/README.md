# Vault

> 2026-04-17 기준 auth-server가 자체 JWT를 발급하지 않도록 전환되면서 Vault Transit signer 구현은 제거되었습니다.  
> 이 문서는 전환 전 실험/설계 기록이며 현재 인증 기준은 [docs/topics/03-keycloak](../03-keycloak/README.md)을 따릅니다.

## 개요

이 주제는 현재 프로젝트가 Vault Transit을 이용해 JWT 서명을 외부화하는 이유와, 로컬 개발 환경에서 Vault를 어떻게 다루는지 정리합니다.

## 현재 프로젝트 맥락

- `infrastructure`에 Vault Transit 클라이언트와 signer가 존재합니다.
- 로컬 Docker 환경에 `vault`, `vault-init` 서비스가 포함됩니다.
- JWT 키를 애플리케이션 내부에 고정하지 않는 방향을 지향합니다.

## 현재 문서

| 문서 | 내용 |
|------|------|
| [01-concepts.md](./01-concepts.md) | 현재 프로젝트에서 Vault가 맡는 역할과 핵심 개념 |

## 관련 문서

- [Vault Local Setup](../../development/vault-local-setup.md)

## 다음에 확장할 문서

- `02-transit-engine.md`
- `03-kubernetes-integration.md`
- `04-bootstrap-runbook.md`
