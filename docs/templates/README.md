# Documentation Templates

새 문서를 시작할 때 기본 골격으로 사용한다. 이 repo 는 *ADR* 과 *트러블슈팅 / 런북* 두 축 + 토픽 README + 아키텍처 설명만 다루므로 템플릿도 5 개로 압축되어 있다.

| 템플릿 | 용도 |
|--------|------|
| [topic-readme-template.md](./topic-readme-template.md) | `docs/topics/<n>-<topic>/` 폴더의 시작 README |
| [architecture-template.md](./architecture-template.md) | 구조와 내부 동작 설명 |
| [adr-template.md](./adr-template.md) | 기술 선택 근거 기록 |
| [runbook-template.md](./runbook-template.md) | 운영 절차 |
| [troubleshooting-template.md](./troubleshooting-template.md) | 문제 해결 기록 |

## 사용 규칙

- 템플릿은 그대로 두고, 새 문서를 만들 때 복사해서 사용한다.
- 문서를 추가하면 관련 `README.md` 에 링크를 함께 넣는다.
- 템플릿을 쓰더라도 *대안 비교* 와 *트레이드오프* 는 생략하지 않는다.
