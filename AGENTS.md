# AGENTS.md

Read order:
1. `/AGENTS.md`
2. nearest module `AGENTS.md`
3. `/docs/architecture/README.md`
4. relevant `/docs/standards/**`
5. relevant `/docs/examples/**`
6. `/docs/standards/ops/standard-application-priority.md`
7. current request

Goal:
- prefer clear boundaries over quick implementation
- prefer maintainable structure over local convenience
- follow the repository architecture before adding code

Modules:
- `bootstrap`: Spring Boot composition, security, filter/wiring, outer technical adapters
- `domain`: invariant, entity, value object, domain rule only
- `application`: use case, port, transaction boundary, business outcome
- `presentation`: HTTP contract, validation, response/error translation
- `infrastructure`: persistence, external API, token/security technical implementation
- `common`: forbidden by default; reintroduce only with explicit rules

Global standards: always read before editing
- `/docs/standards/language/stream.md`
- `/docs/standards/language/optional.md`
- `/docs/standards/language/null.md`
- `/docs/standards/language/collections-immutability.md`
- `/docs/standards/language/enum-constants.md`
- `/docs/standards/language/time.md`
- `/docs/standards/language/exceptions.md`
- `/docs/standards/language/duplication.md`
- `/docs/standards/language/javadoc.md`

Hard bans:
- no layer violation
- no hardcoded provider/header/path/status/business value without explicit owner
- no `Instant.now().toString()` inside dto/response/model
- no broad catch or swallowed exception
- no stale Javadoc
- no inline external API call inside controller/use case
- no direct `presentation -> domain` dependency
- no direct `presentation -> infrastructure` dependency
- no DB transaction held across remote call
- no meaningless interface pair like `XService` + `XServiceImpl` by default

Routing:
- layer ownership / boundary -> `/docs/architecture/README.md`
- standard application priority / conflict resolution -> `/docs/standards/ops/standard-application-priority.md`
- abstraction / design -> `/docs/standards/design/**`
- language/style -> `/docs/standards/language/**`
- Spring abstraction/wiring -> `/docs/standards/spring/**`
- web/http contract -> `/docs/standards/web/**`
- external call/retry/timeout/fallback -> `/docs/standards/integration/**`
- logging/trace/health/pii -> `/docs/standards/observability/**`
- PostgreSQL/query/transaction/concurrency -> `/docs/standards/db/**`
- approved examples -> `/docs/examples/**`

Before editing:
- identify the owning layer
- identify the boundary crossed
- identify whether exception mapping, transaction scope, query shape, or docs must change

After code work — documentation cycle:
1. identify whether the change involves a design decision, troubleshooting, or architectural insight worth recording
2. if yes, write the topic document first in `/docs/topics/<nn-topic>/` following the relevant template from `/docs/templates/`
3. update the topic's `README.md` index
4. if the content is suitable for external publication (troubleshooting, ADR, architecture overview), create a publish draft in `/docs/publish/` using `/docs/templates/velog-post-template.md`
5. update `/docs/publish/README.md` with the new draft entry
6. follow `/docs/documentation-guide.md` for writing standards (Why → What → How → Result)

Documentation routing:
- writing standards / structure / checklist -> `/docs/documentation-guide.md`
- topic deep-dive documents -> `/docs/topics/`
- external publication drafts -> `/docs/publish/`
- reusable templates -> `/docs/templates/`
