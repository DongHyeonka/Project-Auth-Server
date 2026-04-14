# presentation AGENTS

Role:
- own HTTP contract only
- handle controller, request/response dto, validation, response/error mapping

Allowed:
- controller
- request/response dto
- validation annotation
- API response model
- HTTP status mapping
- controller advice
- argument resolver for controller parameter injection only

Forbidden:
- repository access
- external API call
- business rule decision
- direct dependency on domain/infrastructure
- MDC/clock logic inside dto

Rules:
- controller handles HTTP only
- dto must stay pure data
- validation at boundary, invariant in domain
- use ArgumentResolver only for controller parameter injection
- do not hardcode provider/business meaning in mapper
- response shape must stay centralized and consistent

Read first:
- `/docs/architecture/README.md`
- `/docs/standards/web/api-controller.md`
- `/docs/standards/web/request-response-dto.md`
- `/docs/standards/web/response-format.md`
- `/docs/standards/web/error-code-http-status-separation.md`
- `/docs/standards/web/serialization-jackson.md`
- `/docs/standards/web/authentication-object-access.md`
- `/docs/standards/spring/filter-interceptor-resolver-advice.md`
- `/docs/standards/spring/validation-location.md`

Examples:
- `/docs/examples/web/api-controller.md`
- `/docs/examples/web/request-response-dto.md`
- `/docs/examples/web/response-format.md`
- `/docs/examples/web/error-code-http-status-separation.md`
- `/docs/examples/web/authentication-object-access.md`
