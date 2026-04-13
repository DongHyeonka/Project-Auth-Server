# Vault 핵심 개념

## Why

민감한 서명 키를 애플리케이션 내부 파일이나 정적 설정으로만 관리하면 키 노출과 교체 부담이 커집니다.

## What

현재 저장소는 Vault Transit을 통해 JWT 서명 책임을 외부 서비스로 넘기는 구조를 갖고 있습니다.

- 클라이언트: `VaultTransitClient`
- 서명기: `VaultTransitJwtSigner`
- 로컬 초기화: `deploy/docker/resources/vault/init/01-init-transit.sh`

## How

애플리케이션은 raw private key를 직접 다루기보다, 서명 요청을 Vault로 보내고 결과 서명을 사용합니다.

## Result

키 관리 책임을 분리할 수 있지만, 네트워크 의존성과 운영 복잡도는 함께 증가합니다.
