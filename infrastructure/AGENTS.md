# infrastructure AGENTS

Role:
- implement persistence and external integrations
- own JPA/repository/external client/token-security technical details

Allowed:
- JPA entity/repository
- persistence mapper
- external API client
- serializer/deserializer usage
- infrastructure exception
- output port implementation

Forbidden:
- ResponseEntity/API response decision
- client-facing error policy
- controller semantics
- business workflow ownership

Rules:
- translate technical failures into infrastructure exception
- no inline remote-call code outside dedicated client/adapter
- prefer typed request/response models over ad-hoc maps
- do not ignore invalid persisted state; translate explicitly
- choose fetch strategy deliberately
- avoid N+1 by design, not after the fact
- validate slow query with EXPLAIN ANALYZE
- do not hold DB transaction across remote call
- PostgreSQL constraints are mandatory, not optional

Read first:
- `/docs/architecture/README.md`
- `/docs/standards/design/mapper-separation.md`
- `/docs/standards/integration/external-api-client-structure.md`
- `/docs/standards/integration/timeout.md`
- `/docs/standards/integration/retry.md`
- `/docs/standards/integration/serialization-deserialization.md`
- `/docs/standards/observability/exception-log.md`
- `/docs/standards/db/schema-structure.md`
- `/docs/standards/db/column-types.md`
- `/docs/standards/db/pk-fk-unique-check.md`
- `/docs/standards/db/audit-columns.md`
- `/docs/standards/db/index.md`
- `/docs/standards/db/query.md`
- `/docs/standards/db/pagination-query.md`
- `/docs/standards/db/jpa-fetch-strategy.md`
- `/docs/standards/db/n-plus-one.md`
- `/docs/standards/db/transaction.md`
- `/docs/standards/db/isolation.md`
- `/docs/standards/db/lock.md`
- `/docs/standards/db/concurrency.md`
- `/docs/standards/db/soft-delete.md`
- `/docs/standards/db/migration.md`
- `/docs/standards/testing/repository-test.md`
- `/docs/standards/testing/fixture-factory.md`

Examples:
- `/docs/examples/design/mapper-separation.md`
- `/docs/examples/integration/external-api-client-structure.md`
- `/docs/examples/integration/timeout.md`
- `/docs/examples/integration/retry.md`
- `/docs/examples/integration/serialization-deserialization.md`
- `/docs/examples/db/**`
- `/docs/examples/testing/repository-test.md`
- `/docs/examples/testing/fixture-factory.md`
