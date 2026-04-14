# domain AGENTS

Role:
- express domain meaning and invariants only
- own entity, value object, domain policy, domain exception

Allowed:
- entity
- value object
- domain service
- invariant check
- domain exception

Forbidden:
- Spring/JPA/Servlet/HTTP/Jackson
- ResponseEntity/status code/API response shape
- repository/client implementations
- MDC/log context
- string-formatted time generation

Rules:
- prefer expressive types over raw strings
- invariants must be enforced at creation/state-change boundary
- domain exception expresses domain meaning only
- domain must run without web/persistence framework
- keep time as type, not formatted string

Read first:
- `/docs/architecture/README.md`
- `/docs/standards/design/value-object.md`
- `/docs/standards/design/dto-domain-entity-separation.md`
- `/docs/standards/language/null.md`
- `/docs/standards/language/time.md`
- `/docs/standards/language/collections-immutability.md`
- `/docs/standards/language/exceptions.md`
- `/docs/standards/language/javadoc.md`

Examples:
- `/docs/examples/design/value-object.md`
- `/docs/examples/language/null.md`
- `/docs/examples/language/time.md`
- `/docs/examples/language/collections-immutability.md`
- `/docs/examples/language/exceptions.md`
