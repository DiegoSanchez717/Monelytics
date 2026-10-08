# Testing and verification

## Automated layers

- Angular/Vitest tests cover authentication and CSRF communication, guards, forms, reusable components, budgets, bills, savings, charts and assistant states. ESLint, Prettier and strict production compilation check the application contracts.
- JUnit/Mockito tests cover exact-decimal calculations, TOTP and assistant assessment/provider behavior. MockMvc integration tests use actual CSRF cookies and headers to exercise validation, sessions, ownership, roles, transfers, budget limits, recurring payment idempotency, savings contributions, CSV escaping and recovery. H2 is a fast test fixture, not the production database.
- The `postgres-it` Maven profile uses Testcontainers with real PostgreSQL, exercising all Flyway migrations, database constraints and ledger behavior. Docker must be running.
- Playwright drives the production Compose application and same-origin APIs backed by PostgreSQL. Tests create synthetic accounts and check financial workflows, reset emails in local Mailpit, MFA/replay, authorization, assistant affordability and read-only behavior. Axe checks cover desktop/mobile pages, loaded tables and dialogs; keyboard checks include Escape and focus restoration.
- CDK assertions verify reference security properties and that every synthesized resource is disabled. Policy tests enforce fail-closed audit exceptions, no-spend deployment guards and public-runner-only CI without artifact/cache billing.
- GitHub Actions repeats builds, tests, PostgreSQL integration, Docker/browser verification, dependency reviews and Trivy image scans. It never deploys resources.

## Commands

```sh
npm ci --prefix frontend
npm run lint --prefix frontend
npm run format:check --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
cd backend
./mvnw clean verify -Ppostgres-it
cd ..
npm ci --prefix infrastructure
npm test --prefix infrastructure
npm run synth --prefix infrastructure
npm run audit --prefix infrastructure
node scripts/run-local.mjs
npm ci --prefix tests/e2e
cd tests/e2e
npx playwright install chromium
npm test
```

On Windows use `npm.cmd`, `npx.cmd` and `backend/mvnw.cmd`, with Java 21 in `JAVA_HOME`. `./mvnw spotless:apply` formats Java; Maven verification enforces formatting. Linux CI installs browser dependencies using `npx playwright install --with-deps chromium`.

`E2E_BASE_URL` selects a disposable app target; `E2E_MAIL_URL` selects its local Mailpit viewer. Tests create synthetic records. Authentication is limited to 30 requests per IP per 15 minutes, so extensive reruns should use a fresh disposable test instance. Never point these tests at a customer database or expose the development mailbox publicly.

## Verification record

Verification uses Node 24.12, Java 21 and Docker Desktop Linux containers on the provided Windows workstation. Local logs, reports, email tokens and session-bearing traces stay in ignored directories. Published screenshots contain fictional demo records.

| Check | Result |
| --- | --- |
| Angular ESLint / Prettier | Passed |
| Frontend / browser-test dependency audits | Zero vulnerabilities |
| Angular/Vitest | 66 tests passed across 11 files |
| Angular production build | Passed; 357.03 kB initial raw bundle, 94.94 kB estimated transfer |
| Backend regression + PostgreSQL | 53 tests passed in reverse class order, including 5 PostgreSQL tests |
| Assistant / local provider verification | 12 tests passed, including 2 additional local HTTP provider tests |
| Backend unique test count | 56 tests including the additional IRA-conversion regression; overlapping runs are counted once |
| Maven packaging / Spotless | Passed |
| Infrastructure policies / CDK assertions | 18 tests passed |
| CDK offline synthesis | Passed; all resources have a constant-false provisioning condition |
| Infrastructure dependency review | One documented high-severity CDK tooling exception; no critical findings |
| Compose production builds / startup | Built; PostgreSQL, API, frontend and local mailbox healthy |
| Browser / accessibility suite | All 10 distinct tests passed; an ambiguous locator was fixed and its workflow rerun successfully |
| Runtime image vulnerability scans | Final image scans pending |

The backend regression deliberately ran API security tests after the newer integration classes to verify that CSRF fixtures do not change shared security behavior. The local-provider tests check actual HTTP requests, rejected generated prose and reviewed fallback answers without paid API calls.

The narrow infrastructure exception concerns the CDK bundle's `brace-expansion` dependency, is machine-checked against exact advisory IDs and expires on November 7, 2026. It does not permit unexpected findings or runtime-image vulnerabilities. Raw audit output remains in CI job logs and an ignored local report; no billable Actions artifact storage is used.

## Interpretation and limits

Accessibility automation checks a subset of WCAG rules; screenshots and keyboard checks complement it. Passing these checks is not a formal accessibility certification. Angular transfer figures are CLI estimates, not measured network performance. Tests do not establish banking compliance, production throughput, a coverage percentage or real customer outcomes.

Offline infrastructure validation cannot verify AWS permissions, account eligibility, quotas, DNS, certificates, instance availability or deployed health. AWS remains unprovisioned. Public HTTPS, private SMTP, backup restoration, load testing and independent security review remain operator work before a deliberately shared release.
