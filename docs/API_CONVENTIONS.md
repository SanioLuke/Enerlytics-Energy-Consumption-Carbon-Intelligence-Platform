# API Conventions

## Scope

These conventions apply to Enerlytics HTTP APIs. OpenAPI documents in `contracts/openapi/` will be the source of truth before implementation.

## General

- Base path: `/api/v1`.
- JSON is the default representation; UTF-8 is required.
- Resource names are plural nouns and URLs do not contain implementation verbs.
- Breaking changes require a new API version; additive compatible changes remain within the current version.
- Clients send and receive ISO 8601 timestamps. Authoritative timestamps are UTC with an offset (`Z`).
- Identifiers are opaque strings to API consumers.
- Units must be explicit in field names or companion unit fields and conform to a documented unit vocabulary.
- Energy, carbon, and monetary decimals are represented without binary floating-point assumptions.

## Validation and Errors

Use `application/problem+json`, compatible with RFC 9457, with this stable extension shape:

```json
{
  "type": "https://docs.enerlytics.example/problems/validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/sites",
  "code": "VALIDATION_ERROR",
  "correlationId": "opaque-correlation-id",
  "errors": [
    { "field": "timezone", "code": "INVALID_TIMEZONE", "message": "Must be an IANA timezone." }
  ]
}
```

The documentation host is illustrative until a production domain is approved. Error codes are stable and machine-readable. Validation failures never expose stack traces or internals.

## Collections

Potentially large collections must support:

- Zero-based `page` and bounded `size`, with documented defaults and maximums.
- Repeatable, allowlisted `sort=field,asc|desc` parameters.
- Domain-specific filters with explicit semantics.
- Response metadata containing page, size, total elements, and total pages when a count is operationally reasonable.

Cursor pagination may replace offset pagination for high-volume time-series endpoints and must be specified in the contract.

## Idempotency and Concurrency

- Mutation endpoints that may be retried accept `Idempotency-Key` and bind it to tenant, actor, operation, and payload fingerprint.
- Reuse with a different payload returns a conflict.
- Stable external reading IDs and database uniqueness protect ingestion.
- Conditional requests use ETags or explicit versions for updates where lost updates are possible.

## Security and Tenancy

- Authentication uses standards-based bearer tokens over TLS.
- Authorization is enforced server-side for every resource.
- Tenant context is derived from trusted identity and membership data, never solely from a client-controlled header.
- Resource-not-found and forbidden behavior must avoid cross-tenant information disclosure.

## Correlation, Rate Limits, and Caching

- Accept a valid incoming correlation/trace identifier or generate one; echo the correlation ID in the response.
- Return standard rate-limit information when rate limiting is enabled.
- Cache semantics are explicit. Sensitive or user-specific responses default to `Cache-Control: no-store`.

## Asynchronous Operations

Long-running imports, calculations, and reports return `202 Accepted` with an operation resource. Operation states and failure details are contractually defined. SSE or WebSocket channels are used only for justified real-time updates and retain authorization checks.
