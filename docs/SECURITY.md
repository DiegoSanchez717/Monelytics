# Security controls and operational limits

## Authentication and recovery

Passwords use BCrypt cost 12, a minimum 12-character strong-password policy for registration/recovery and a 72-byte UTF-8 maximum. Credential DTOs redact `toString()` output. Login rotates the session and stores authentication in PostgreSQL-backed Spring Session; browser storage contains no authentication token. Session cookies are HttpOnly/SameSite=Lax and become Secure through `COOKIE_SECURE=true`. Logout invalidates sessions.

TOTP MFA uses authenticator-app codes, AES-GCM encrypted secrets, a stable 32-byte environment key and monotonic accepted steps to reject replay. Setup/enable/disable require password confirmation; enable/disable also require a valid unused code. Failed login counts and lockouts persist in PostgreSQL.

Recovery creates a cryptographically random 256-bit URL token, stores only its SHA-256 hash, expires after 30 minutes, and consumes all outstanding links on success. Per-account request cooldown and per-IP throttling limit abuse. The request response is identical for unknown/throttled addresses; tokens and delivery state never enter API responses. SMTP dispatch happens asynchronously after commit. Reset revokes existing sessions and preserves MFA. Tokens, recipients, SMTP secrets and message bodies are not logged. Local Mailpit captures synthetic mail without external delivery.

## Authorization and financial integrity

Spring Security protects APIs, enforces USER/ADMIN roles and restricts audit access to administrators. Every account/category/budget/bill/goal/transaction lookup is owner-scoped; foreign IDs receive a safe missing-resource response. Client route guards complement server enforcement.

All mutating endpoints, including auth/recovery/assistant POSTs, require CSRF cookies and `X-XSRF-TOKEN`. Money uses exact decimal arithmetic. Database constraints, user/account locks, deterministic lock ordering, optimistic versions and occurrence uniqueness enforce transactional consistency. CSV fields are quoted and formula-starting text is escaped. Search/sort fields are allowlisted and exports are bounded.

The assistant is read-only. It obtains factors from owned records and reports missing income instead of inventing affordability. Provider defaults are mock/$0. Optional Ollama is local-only, cannot follow redirects or route cloud models, receives no ledger/account records and can only select a reviewed education topic. It cannot execute a transaction. Every reply states that it is educational, not professional financial advice.

## Request and runtime protections

Authentication/recovery share a 30-attempt per-IP/15-minute window; assistant requests have a separate 20-request window. Maps are bounded and stale entries are evicted. This is per-instance protection, not a distributed edge rate limiter. Request bodies are capped at 256 KiB including missing Content-Length. Validation, framework errors and conflicts return safe Problem Details with a correlation ID; unexpected details remain server-side.

Nginx serves same-origin APIs with browser security headers and a restrictive app script policy. Swagger's backend UI requires its own script policy; disable API docs before a public release. Local database/SMTP services are private, exposed app/API/mailbox ports bind to loopback, and the application container runs as non-root. Structured logging, dependency checks, image scans and health checks support operations.

## Configuration and release prerequisites

`.env` is ignored. Setup generates random private database/MFA values, preserving existing keys. Public demo passwords, sample database values and example MFA keys are synthetic local/CI fixtures; disable demos and replace all fixture secrets for any shared instance.

Before deliberately sharing a deployment: configure HTTPS and Secure cookies, private credentials, authenticated TLS SMTP, database/key backups, backup-restore checks, monitoring, least-privilege access and a documented response process. Disable demo seeding and public Swagger. Never silently rotate the MFA key. These are manual operator responsibilities; the repository does not establish banking compliance or production customer use.

AWS and paid AI are disabled by default. Deployment scripts fail before AWS calls; generated resource templates have a constant-false condition on every resource. Local Compose is the supported permanent $0 runtime. AWS Free account plans are temporary, and a billing alert alone cannot enforce a zero-spend requirement. See [AWS policy](AWS-DEPLOYMENT.md).

The narrow CDK bundled-dependency exception is documented, machine-tested and time-limited. It does not cover unexpected findings or runtime images. See [test/security verification](TESTING.md) and the raw infrastructure audit file and CI job logs.
