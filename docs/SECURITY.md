# Security

## Principles

- Secure by default, least privilege, explicit trust boundaries, and deny-by-default authorization.
- Defense in depth across Nginx, application, database, messaging, containers, and orchestration.
- No credentials, tokens, private keys, or production-sensitive data in source control, images, logs, or client bundles.

## Identity and Authorization

- Use Spring Security with OIDC/OAuth 2.1 integration; do not invent authentication protocols.
- Validate issuer, audience, signature, expiry, and intended token use.
- Model organization membership and scoped roles explicitly.
- Enforce tenant and resource authorization in application use cases, not only controllers or UI guards.
- Angular guards improve navigation but are never authorization boundaries.
- Privileged actions require auditable, narrowly scoped permissions.

## Application Security

- Validate all untrusted input at transport and domain boundaries.
- Use parameterized persistence through JPA/query APIs and allowlist dynamic sort/filter fields.
- Apply CSRF defenses according to the selected browser credential model.
- Configure restrictive CORS for approved origins only.
- Nginx and the application set appropriate security headers, including CSP, frame restrictions, content-type protection, and referrer policy.
- Do not expose stack traces, SQL, internal hostnames, or sensitive resource existence through errors.
- Upload/import features enforce size, type, parsing, storage, and malware-control policies appropriate to the accepted format.
- WebSocket/SSE handshakes and subscriptions are authenticated and authorized.

## Secrets and Cryptography

- Local development uses ignored environment files derived from `.env.example`; deployed environments use platform secret stores.
- Rotate secrets without rebuilding application code.
- Use TLS for external traffic and authenticated encryption for infrastructure links according to deployment risk.
- Use established platform cryptography and password encoders; never implement custom cryptography.

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
