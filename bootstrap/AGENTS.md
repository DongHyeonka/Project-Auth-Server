# bootstrap AGENTS

Role:
- compose the Spring Boot application
- register security, filter, configuration, entrypoints
- translate framework/technical exceptions that inner layers must not know

Allowed:
- `@Configuration`
- `SecurityFilterChain`
- filter/interceptor registration
- app startup wiring
- migration entrypoint
- outer technical adapters for HTTP/security boundary

Forbidden:
- business rules
- repository-driven use case logic
- response shaping logic
- domain policy decisions
- hardcoded business semantics

Rules:
- bootstrap wires flow; it does not own business meaning
- use Filter for servlet-wide concern
- use Interceptor for MVC flow concern
- use Advice only when the dependency direction remains valid
- if exception translation would create `presentation -> infrastructure`, keep it here
- trace/request context setup belongs here, not in dto/model

Read first:
- `/docs/architecture/README.md`
- `/docs/standards/spring/filter-interceptor-resolver-advice.md`
- `/docs/standards/spring/bean-registration.md`
- `/docs/standards/spring/dependency-injection.md`
- `/docs/standards/spring/configuration-properties.md`
- `/docs/standards/observability/trace-principal-path-recording.md`
- `/docs/standards/observability/log-level.md`
- `/docs/standards/observability/exception-log.md`
- `/docs/standards/testing/springboottest-usage.md`
- `/docs/standards/testing/mock-usage.md`
- `/docs/standards/db/migration.md`

Examples:
- `/docs/examples/spring/filter-interceptor-resolver-advice.md`
- `/docs/examples/spring/bean-registration.md`
- `/docs/examples/spring/dependency-injection.md`
- `/docs/examples/spring/configuration-properties.md`
- `/docs/examples/observability/trace-principal-path-recording.md`
- `/docs/examples/testing/springboottest-usage.md`
- `/docs/examples/testing/mock-usage.md`
- `/docs/examples/db/migration.md`
