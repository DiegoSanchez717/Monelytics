# Monelytics contribution and review instructions

Repository guidance for GitHub Copilot; this does not claim Copilot generated existing work.

- Keep Angular standalone components, TypeScript models, reactive forms, routes, guards and HTTP services aligned with Java DTOs. Use accessible HTML/CSS, #48D1CC/#EF7A6C, readable contrast and keyboard focus.
- Use PostgreSQL NUMERIC and Java BigDecimal for exact money. Transfers atomically update both owned accounts and stay outside income/expense reports.
- Put business rules in Spring Boot services, authorize every owned resource, validate DTOs and preserve safe errors. Never weaken Spring Security, CSRF, MFA, revocation, audit or rate limiting.
- Add Flyway migrations rather than changing applied SQL. Test H2 and real PostgreSQL, unique recurring occurrences and lock ordering.
- Never commit private credentials, .env, recovery tokens, MFA secrets or account records. Public demo credentials are local fixtures only.
- Keep AI read-only. Providers cannot mutate finances. Actual account facts come from recorded data; untrusted model prose cannot invent balances.
- Preserve zero-spend safeguards: no paid AI requests, AWS provisioning, registry pushes or external mail delivery during setup/tests. Cloud references remain synthesis-only and provisioning-disabled.
- Run relevant lint/build/unit/integration/browser checks. Use descriptive commits; never force-push or overwrite unrelated work.
