# API documentation boundary

The versioned API payload contracts live in [`../../contracts/`](../../contracts/).
The ASP.NET Core implementation lives in [`../../platform/api/`](../../platform/api/).

This separation is intentional:

- `contracts/` is the public, language-neutral compatibility surface;
- `platform/api/` owns authentication, authorization, validation, persistence
  orchestration, health, idempotency, and error mapping;
- the mobile free core does not depend on the API being reachable.

OpenAPI generation and managed endpoint publication remain deployment gates.
Do not document an endpoint as externally available until its environment and
authentication behavior have been observed.
