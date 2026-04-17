# application AGENTS

Role:
- orchestrate use cases
- define ports
- own transaction boundary
- translate domain meaning into business outcome and error code

Allowed:
- use case/service orchestration
- command/result dto
- input/output port
- business exception and error code
- transaction boundary

Forbidden:
- HTTP concerns
- Servlet/SecurityContext access
- ObjectMapper/HttpClient/WebClient/JPA details
- inline external call implementation
- direct dependency on presentation/infrastructure internals

Rules:
- application decides business outcome and error code
- do not expose HTTP status here
- catch only to translate or add business context
- transaction starts here, not in controller/filter/resolver
- do not hold transaction across remote IO
- ports exist for boundaries, not for every internal helper

Read first:
- `/docs/architecture/README.md`
- `/docs/standards/design/interface-creation.md`
- `/docs/standards/design/port-abstraction.md`
- `/docs/standards/design/dto-domain-entity-separation.md`
- `/docs/standards/spring/transaction.md`
- `/docs/standards/spring/application-event.md`
- `/docs/standards/language/exceptions.md`
- `/docs/standards/integration/external-api-client-structure.md`
- `/docs/standards/db/transaction.md`
- `/docs/standards/db/isolation.md`
- `/docs/standards/db/concurrency.md`
- `/docs/standards/testing/mock-usage.md`
- `/docs/standards/testing/fixture-factory.md`
- `/docs/standards/testing/springboottest-usage.md`

Examples:
- `/docs/examples/spring/transaction.md`
- `/docs/examples/spring/application-event.md`
- `/docs/examples/design/port-abstraction.md`
- `/docs/examples/design/interface-creation.md`
- `/docs/examples/design/dto-domain-entity-separation.md`
- `/docs/examples/db/transaction.md`
- `/docs/examples/db/isolation.md`
- `/docs/examples/db/concurrency.md`
- `/docs/examples/testing/mock-usage.md`
- `/docs/examples/testing/fixture-factory.md`
- `/docs/examples/testing/springboottest-usage.md`
