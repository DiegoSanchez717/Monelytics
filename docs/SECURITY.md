# Security model

Monelytics demonstrates financial application engineering using synthetic information. It does not connect to banks, move money, store banking credentials, or claim regulatory compliance.

## Implemented controls

Passwords are BCrypt hashed. Registration validates strength, length, email, and names; client and server validation run independently. Session identity rotates on login and is stored with Spring Session JDBC. Production cookies are Secure, HttpOnly, and SameSite=Lax. The browser keeps neither bearer tokens nor passwords in localStorage. CSRF tokens protect session-authenticated mutations, including authentication endpoints.

The Angular application uses same-origin API routing. Arbitrary cross-origin access is not enabled. Spring Security enforces user/admin roles. Services scope every owned account, ledger entry, and goal to the authenticated user. An administrator can read audit events; administration does not grant access to another user's account operations.

MFA uses time-based authenticator codes and encrypted secrets with a 32-byte environment-supplied encryption key. Setup requires password verification, and enabling requires a valid code. Replay protection records the accepted time step. Disabling requires password and code. Protect and back up the key; changing it without a migration makes existing encrypted secrets unusable.

JPA parameters protect against SQL injection. DTOs constrain writable fields and reject unknown input properties. Monetary validation rejects fractional cents, negative deposits, overdrafts, and invalid allocations. Angular encodes template output. Secure headers reduce content sniffing and framing. Authentication throttling and lockout reduce repeated guesses. Request IDs and structured logs omit credentials and MFA secrets; audit records use bounded action descriptions.

The API reads JSON bodies through a bounded 256 KiB filter before controllers execute, including unknown-length/chunked requests. Local Nginx applies the same request-size ceiling. Oversized requests receive 413; unsupported media types and methods retain safe 415/405 responses. Field validation messages are summarized in the UI without exposing internal exceptions.

Flyway and constraints enforce database invariants. Balance changes and audit records use database transactions. Runtime container users are unprivileged. Secrets are injected through environment variables locally and AWS Secrets Manager in cloud configuration. Demo seeds are disabled in production; Swagger can be disabled. CI includes dependency/image scanning.

Production Angular builds disable critical CSS inlining so stylesheets load without inline JavaScript event handlers. This keeps `script-src 'self'` compatible with the generated application. Browser checks verify computed styles against the actual container security headers.

## Threat model

| Threat | Control | Residual concern |
|---|---|---|
| Cross-user resource access | Ownership-scoped service/repository queries | Review every new endpoint and test both users |
| Credential theft | BCrypt, HttpOnly cookie, optional MFA | Phishing, compromised endpoint, account recovery |
| CSRF | Token required on state-changing requests | XSS could act within an authenticated origin |
| XSS | Angular encoding and restrictive headers | Avoid unsafe HTML and audit third-party dependencies |
| Concurrent balance corruption | Atomic reversal/application and row locking | Multi-account operations must use deterministic lock order |
| Secret leakage | Ignored local env, injected secrets, safe logs | Protect CI permissions and CloudWatch access |
| Brute force | Rate limiting and account lockout | Per-instance limits require an edge/shared limiter at scale |
| Supply-chain issue | Lockfiles, CI scans, pinned runtime baseline | Triage new advisories and validate remediation |

## Before handling real financial information

Add reviewed recovery flows, MFA recovery codes, email verification, password reset, shared throttling/WAF, account lifecycle and retention policies, compliance review, penetration testing, incident response, encrypted backups with restore drills, approved secrets rotation, and access reviews. MFA currently has no self-service recovery; loss of the authenticator requires a reviewed operator procedure. Do not bypass MFA by changing user records casually.

The contribution threshold is configurable and intentionally not a complete tax-rules engine. Catch-up amounts, income eligibility, tax filing status, rollover eligibility, penalties, and jurisdiction-specific requirements require a separate reviewed implementation. Goals are user-entered planning scenarios. Synthetic transactions are editable; this portfolio ledger is not an immutable accounting system.

Local HTTP uses `COOKIE_SECURE=false` on loopback. HTTPS is required in production. Avoid exposing local Compose ports publicly. Production uses separate environment values, private RDS networking, least-privilege task roles, HTTPS delivery, and managed secrets. Never commit `.env`, AWS credentials, private keys, or real customer records.

## Deployment-tool dependency review

Runtime maintenance pins in `backend/pom.xml` upgrade Spring Boot/Spring Framework, align Jackson dependency families, and update Tomcat and PostgreSQL JDBC. The application uses the publicly patched Spring Framework 7 branch rather than suppressing the scanner's Spring MVC finding. Framework upgrades require the full API/PostgreSQL suite and live same-origin browser checks, including CSRF and MFA; `V3` invalidates older serialized sessions during this upgrade.

The infrastructure audit records one high finding in CDK's bundled `brace-expansion` 5.0.9. CDK tooling processes repository-controlled glob expressions; it is not shipped in the frontend or API runtime. A fail-closed, exact-advisory/path/version exception is defined in the [audit policy](../infrastructure/scripts/audit-policy.mjs) and expires November 7, 2026. Other high/critical findings still block CI. Replace the exception with a clean upstream upgrade when available; it is not a claim of zero infrastructure-tool vulnerabilities.
