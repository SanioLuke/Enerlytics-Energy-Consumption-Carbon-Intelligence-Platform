# Security

## Principles

- Secure by default, least privilege, explicit trust boundaries, and deny-by-default authorization.
- Defense in depth across Nginx, application, database, messaging, containers, and orchestration.
- No credentials, tokens, private keys, or production-sensitive data in source control, images, logs, or client bundles.

## Identity and Authorization

### Authentication Model

The Enerlytics backend uses stateless JSON Web Token (JWT) authentication:

- **Access token**: short-lived JWT signed with `enerlytics.security.jwt.access-secret`.
  - Default expiry is 15 minutes and is configurable via `JWT_ACCESS_EXPIRATION_MS`.
  - Contains the user id, email, active status, and default organization id.
  - Must be sent in the `Authorization: Bearer <token>` header.
- **Refresh token**: long-lived opaque token stored as a SHA-256 hash in `iam.refresh_token`.
  - Default expiry is 7 days and is configurable via `JWT_REFRESH_EXPIRATION_MS`.
  - Created on login and rotated on every refresh: the old token is revoked and a new token is issued.
  - Sent to `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` in the request body.

### Password Hashing

Local passwords are hashed with Spring Security's `BCryptPasswordEncoder` at strength 12. Passwords are never logged, returned, or persisted in plain text. OAuth2/OIDC passwords are not stored locally.

### Account Lifecycle

`iam.app_user.status` controls login eligibility:

- `INVITED`: pending invitation acceptance; cannot authenticate.
- `ACTIVE`: authentication and authorization are evaluated normally.
- `SUSPENDED`: authentication is rejected with HTTP 401.

Suspended users are still rejected if they present an access token issued before suspension, because the JWT filter re-checks the database status on every request.

## Roles and Permissions (RBAC)

### Predefined System Roles

| Role | Scope | Typical Permissions |
|---|---|---|
| `PLATFORM_ADMIN` | Platform | All permissions; cross-tenant access is permitted for platform operations. |
| `ORGANIZATION_ADMIN` | Organization | Full management of one organization, users, sites, meters, tariffs, and configuration. |
| `FACILITY_MANAGER` | Organization | Sites, meters, telemetry ingestion, and alert management. |
| `ENERGY_ANALYST` | Organization | Read energy, carbon, analytics, forecasts, telemetry, and reports. |
| `SUSTAINABILITY_MANAGER` | Organization | Carbon, sustainability, analytics, and reporting. |
| `VIEWER` | Organization | Read-only access to organization data and reports. |

Permissions are stored in `iam.permission` and granted through `iam.role_permission`. Roles are enforced with Spring `@PreAuthorize("hasAuthority('permission:code')")`.

### Permission Examples

- `organization:read`, `organization:write`
- `user:read`, `user:write`
- `site:read`, `site:write`
- `meter:read`, `meter:write`
- `telemetry:read`, `telemetry:write`
- `energy:read`, `carbon:read`
- `tariff:read`, `tariff:write`
- `analytics:read`, `forecast:read`
- `alert:read`, `alert:write`
- `report:read`, `report:write`
- `notification:read`, `notification:write`
- `audit:read`

Custom organization-specific roles may be added later. Seeded role-permission mappings must be changed through reviewed database migrations.

## Tenant Isolation

Every tenant-owned resource belongs to exactly one organization. The request tenant is resolved as follows:

1. The access token carries a `defaultOrganizationId` claim chosen at login from the user's first active membership.
2. An explicit `X-Organization-Id` header overrides the default.
3. The authentication filter loads the user's active membership and role assignments for that organization.
4. The resulting Spring Security `Authentication` contains only permissions valid for that organization.
5. Controllers enforce that the path organization id equals the current tenant id unless the user is a `PLATFORM_ADMIN`.

A user **must never** access another organization's data unless explicitly authorized as a platform administrator. Resource-not-found and forbidden responses are intentionally uniform to avoid cross-tenant information leakage.

## Authentication Flow

```text
Client --(email/password)--> POST /api/v1/auth/login
    Backend validates credentials (DaoAuthenticationProvider + BCrypt)
    Backend records last sign-in, issues JWT access token + opaque refresh token
Client stores tokens and sends access token on every request
Client sends X-Organization-Id to operate in a specific tenant
Client refreshes with POST /api/v1/auth/refresh (old refresh token revoked)
Client logs out with POST /api/v1/auth/logout (refresh token revoked)
```

## Authorization Flow

```text
Request reaches JwtAuthenticationFilter
    -> Parse and validate JWT
    -> Load user from database and re-verify active status
    -> Resolve current organization id (header or token claim)
    -> Load active role assignments and permissions for that organization
    -> Build UserPrincipal with tenant-scoped authorities
Spring Security evaluates @PreAuthorize expressions
Controller checks path organization id against current tenant (unless platform admin)
```

## Token Storage and Rotation

- Refresh tokens are stored as SHA-256 hashes; the raw value is only returned to the client on creation/rotation.
- A refresh token can be used exactly once: using it marks the old token revoked and issues a new one.
- `replaced_by_token_hash` links revoked tokens to their replacements for audit/debugging.
- Logout revokes the supplied refresh token. Access tokens remain valid until their short expiry.
- A scheduled cleanup job (future work) will purge expired revoked tokens.

## Development Seeding

A development-only seeder runs when the Spring profile is `local` or `dev` and the following environment variables are set:

- `DEV_ADMIN_EMAIL`
- `DEV_ADMIN_PASSWORD`
- `DEV_ADMIN_ORG_KEY`
- `DEV_ADMIN_ORG_NAME`

The seeder creates an organization and a `PLATFORM_ADMIN` user with a BCrypt-hashed password. The credentials must be configured in `.env` and must never be committed.

## Application Security

- Validate all untrusted input at transport and domain boundaries.
- Use parameterized persistence through JPA/query APIs and allowlist dynamic sort/filter fields.
- CSRF is disabled because the backend uses stateless bearer tokens, not session cookies.
- CORS is restricted to origins configured in `CORS_ALLOWED_ORIGINS`.
- Nginx and the application set appropriate security headers, including CSP, frame restrictions, content-type protection, and referrer policy.
- Do not expose stack traces, SQL, internal hostnames, or sensitive resource existence through errors.
- Upload/import features enforce size, type, parsing, storage, and malware-control policies appropriate to the accepted format.
- WebSocket/SSE handshakes and subscriptions are authenticated and authorized.

## Secrets and Cryptography

- Local development uses ignored environment files derived from `.env.example`; deployed environments use platform secret stores.
- Rotate secrets without rebuilding application code.
- Use TLS for external traffic and authenticated encryption for infrastructure links according to deployment risk.
- Use established platform cryptography and password encoders; never implement custom cryptography.
- JWT signing keys must be at least 256 bits and are configured through `JWT_ACCESS_SECRET` and `JWT_REFRESH_SECRET`.

## Standard Error Responses

Authentication and authorization failures return `application/problem+json` per RFC 9457:

| Code | HTTP | Meaning |
|---|---|---|
| `AUTHENTICATION_FAILED` | 401 | Missing, expired, or invalid credentials/token. |
| `ACCESS_DENIED` | 403 | Authenticated user lacks permission or tenant access. |
| `VALIDATION_ERROR` | 400 | Request body or parameters failed validation. |
| `RESOURCE_NOT_FOUND` | 404 | Requested resource does not exist or is not visible. |
| `INTERNAL_ERROR` | 500 | Unexpected server error. |

Validation errors include a per-field `errors` array. All error responses include a unique `correlationId` for tracing.

## Infrastructure and Supply Chain

- Run containers as non-root with read-only filesystems and minimal capabilities where practical.
- Pin and scan dependencies and images; produce an SBOM in CI.
- Protect CI environments, use short-lived deployment credentials where possible, and require review for production.
- Kafka, PostgreSQL, and Redis are not publicly exposed in production and use authenticated, least-privilege identities.
- Kubernetes network policies and workload/service-account isolation are required before production.

## Audit and Privacy

Security events and material business changes record actor, tenant, action, target, outcome, UTC timestamp, and correlation metadata. Audit records must not contain secrets or unnecessary personal data. Retention, deletion, export, and data residency controls require product and legal requirements.

## Verification

Security testing includes authorization tests, tenant-isolation tests, dependency/image scanning, secret scanning, static analysis, API abuse cases, and periodic threat modeling. Findings are remediated by severity and release risk rather than merely documented.
