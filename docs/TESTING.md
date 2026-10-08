# Testing and verification

## Automated layers

- Angular tests cover authentication/CSRF communication, guards, forms, components, and retirement validation. Strict production compilation and ESLint check the application contracts.
- JUnit/Mockito tests cover retirement calculations and TOTP behavior. API integration tests exercise controllers, validation, cookies/CSRF, ownership, roles, and ledger mutations. H2 is a fast test fixture, not the production database.
- The `postgres-it` Maven profile runs Testcontainers against PostgreSQL, validating real migrations and database behavior. A running Docker engine is required.
- Playwright calls the live same-origin API and drives the actual UI against Compose/PostgreSQL. It checks ledger reversal/application, overdrafts, ownership, CSRF, beneficiary totals, goals, MFA/replay, administrator audit access, registration, account and transaction forms, calculator output, modal focus restoration, production stylesheet loading, mobile overflow, and axe accessibility rules. Table checks wait for data to render before accessibility analysis.
- AWS CDK assertions verify security and routing properties of synthesized infrastructure. Synthesis creates a local CloudFormation artifact; it does not create AWS resources.
- GitHub Actions repeats builds, tests, Compose/browser verification, dependency audits, and image scans before an explicitly triggered deployment.

## Commands

```powershell
cd frontend
npm.cmd ci
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

```powershell
cd backend
./mvnw.cmd verify
./mvnw.cmd verify -Ppostgres-it
```

Java formatting is enforced by Maven verification. Use the formatting goal documented in `pom.xml` before committing Java changes.

```powershell
cd infrastructure
npm.cmd ci
npm.cmd test
npm.cmd run synth -- --quiet
```

```powershell
node scripts/run-local.mjs
cd tests/e2e
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd test
```

Linux CI installs Chromium system dependencies with `npx playwright install --with-deps chromium`. Use `E2E_BASE_URL` to select a disposable target. Tests create synthetic user records, and repeated tests can reach the authentication rate limit; use a fresh disposable test instance for extensive reruns. Do not use a customer database.

## Verification record

Executed on the provided Windows workstation with Node 24.12, Java 21, and Docker Desktop using Linux containers. Test reports are generated locally in ignored output directories; live session-bearing traces are not published. Screenshots in `docs/screenshots` show synthetic demo information.

| Check | Result |
|---|---|
| Frontend ESLint | Passed |
| Angular/Vitest | 33 tests passed in 7 files |
| Angular production build | Passed; initial raw bundle 330.16 kB, CLI estimated transfer 89.62 kB |
| Frontend npm dependency audit | 0 vulnerabilities |
| Maven + Spotless + PostgreSQL profile | 24 tests passed, including OpenAPI and session cookie checks; executable JAR packaged |
| CDK assertions and audit-policy tests | 10 tests passed |
| CDK synthesis | Passed without provisioning resources |
| Infrastructure dependency review | 1 high, 0 critical; exact deployment-tool exception documented in SECURITY.md |
| Docker Compose main/development configuration | Validated |
| Frontend/backend Docker images | Built successfully with runtime security updates |
| Docker Compose startup | Database, API, and frontend healthy |
| Live Playwright/API/browser suite | 8 tests passed against the final production containers and PostgreSQL |
| Accessibility and keyboard checks | Landing, dashboard, loaded transactions, accounts, forms, goals/calculator, settings, and admin audit passed axe checks; Escape restores modal focus |
| Runtime image security scans | Trivy 0.75.0: 0 fixable HIGH/CRITICAL findings in both final images; pgJDBC coordinate separately verified |
| Source formatting and staged-secret checks | Passed; ignored local secrets excluded from staged files |

All 75 tests passed: 33 frontend, 24 backend, 10 infrastructure, and 8 end-to-end tests. Desktop/mobile screenshots were visually inspected. Bundle transfer values are Angular CLI estimates, not measured production network performance. The security scan record (being refreshed for the general-finance features) records the tested image digests, timestamps, package counts, and scan scope. Trivy's JAR index did not identify the newly released PostgreSQL JDBC artifact automatically; its packaged version was checked and its explicit Maven coordinate was scanned separately.

## Interpretation and limits

Accessibility automation checks a subset of WCAG rules. Keyboard operation and responsive screenshots complement those checks; automated success is not a formal accessibility certification. Infrastructure assertions cannot verify AWS account permissions, quotas, certificate issuance, DNS propagation, regional capacity, or actual deployment health.

This project does not claim measured production throughput, banking compliance, or a coverage percentage. Production load testing, penetration testing, cloud smoke checks, and backup restoration exercises remain separate operational work.
