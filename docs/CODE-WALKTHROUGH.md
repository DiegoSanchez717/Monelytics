# Code walkthrough

## Start in the browser

`frontend/src/main.ts` bootstraps the standalone Angular app. `app.routes.ts` declares public authentication/recovery pages and lazily loaded authenticated features. `core/models.ts` defines API contracts; `core/auth.service.ts` maintains the current profile. Session/role guards guide navigation, and Spring Security independently authorizes requests.

Feature components use semantic HTML and reactive forms for accounts, transactions, categories, budgets, recurring plans, savings goals and the assistant. The typed `core/api.service.ts` sends requests; `core/security.interceptor.ts` attaches credentials and obtains CSRF tokens for mutations. Shared SVG charts, notifications, dialogs and loading/error/empty states keep interactions consistent. Styles use teal `#48D1CC`, coral `#EF7A6C`, dark text and responsive layouts. TypeScript compiles to JavaScript; HTML and CSS are application assets.

## Follow an expense or transfer

The transaction form selects an owned source, type, amount, date and description. Income/expenses can select a matching category; transfers require a distinct destination and no category. HttpClient calls the relative `/api/transactions` route through the same-origin Nginx proxy.

`SecurityConfiguration` verifies the JDBC session and CSRF cookie/header before controller dispatch. `FinanceController` validates `ApiDtos.TransactionInput`; `FinanceService` locks the owner and affected accounts, checks ownership/business rules, updates exact-decimal balances, saves the ledger entry and records an audit event in one transaction. `Repositories`/`Models` map these operations through Hibernate to PostgreSQL.

An expense debits its account. A transfer is one ledger row debiting the source and crediting the destination, excluded from income/expenses. A card repayment changes checking/card balances without counting payment as a second purchase expense. Correcting an entry reverses its old effect before applying replacement; deleting reverses it. Both reject a reversal that would overdraw a non-credit account, and failures roll back the entire mutation.

`FinanceService.exportCsv` applies the same owner/filter rules, streams 500-row batches under repeatable-read isolation and clears managed entities between batches. Cells are quoted and formula prefixes escaped. Export audits access without changing the ledger.

## Follow planning and analytics

`PlanningService` owns category, budget, recurring-plan and goal-allocation mutations. Category names are unique per owner after normalization. `AnalyticsService` calculates monthly expense totals for overall/category budgets. Overlapping budgets constrain the same dollars; the assistant uses the tighter remaining room.

A recurring payment sends the displayed due-date snapshot and actual payment date. Under owner locking, the service returns the existing occurrence or creates one expense and advances the schedule. Database occurrence uniqueness prevents duplicate debits. Anchor-day recurrence retains January 31 across short months. Removing a posted payment reverses it and reopens its occurrence; removing the plan retains posted expenses.

Goal contributions are earmarked savings records. Progress equals an editable opening allocation plus recorded contributions; it does not automatically move bank funds. Editing cannot erase history. The optional `ProjectionService` retirement calculator models compounded growth and excludes real-world tax, fee and market effects.

The dashboard combines current signed balances, six monthly balance points, SQL income/expense aggregates, budgets, goals and recent activity. Historical-month selection changes charts/monthly totals while balances/plans remain current. `AnalyticsService` derives budget alerts, upcoming/overdue bills, exact-decimal unusual-spending comparisons and savings milestones. It does not run a payment scheduler or send notification emails.

## Follow authentication and recovery

The browser first gets `/api/auth/csrf`, retaining `XSRF-TOKEN` and sending the raw value as `X-XSRF-TOKEN` on mutations. `AuthService` checks BCrypt passwords and optional TOTP codes. MFA secrets are AES-GCM encrypted with a stable environment key, accepted time steps prevent replay, and failed-login lockout persists.

`AuthController` rotates session identity and explicitly saves the security context. PostgreSQL-backed Spring Session stores authentication; the browser receives an HttpOnly `MONELYTICS_SESSION` cookie. Authentication tokens are absent from browser storage. Registration seeds default categories, and logout invalidates session/CSRF cookies.

`PasswordRecoveryService` creates a random token, stores its SHA-256 hash and expires it after 30 minutes. Requests return a generic message for known/unknown/throttled accounts. An after-commit callback dispatches email asynchronously through `SmtpResetMailGateway`; local Mailpit captures it without external delivery. Confirmation consumes links, changes the password, revokes sessions and keeps MFA enabled. Credential DTOs redact their string representation and delivery errors omit recipients/tokens.

## Follow the assistant

`FinancialAssistantService` calls a separate `FinancialAssessmentService` bean under a read-only repeatable-read transaction. Assessment reads owned income/expenses, checking/savings cash, card debt, upcoming recurring plans, incomplete goals and applicable budgets. It reports insufficient data without recorded income, reserves commitments and returns limiting factors plus an educational disclaimer. It has no financial mutation endpoint.

The database transaction ends before provider communication. `FinancialEducationProvider` defaults to reviewed mock education with no API key. Explicitly configured local Ollama receives only the user's question, classifies it into a strict topic allowlist and selects reviewed text. Generated prose is never displayed, records are never sent, and invalid output/timeouts fall back to mock. Configuration rejects paid/remote/cloud-routed choices. `LocalEducationProviderTest` exercises actual local HTTP classification and unsafe-output fallback.

## Follow configuration and deployment

Multi-stage Dockerfiles compile Angular/Java, then package non-root runtime containers. Compose runs Nginx, Spring Boot, PostgreSQL and Mailpit with readiness checks, private database networking and loopback-bound app/API/mailbox ports. Nginx serves Angular browser routes and proxies `/api`. SQL and Java Flyway migrations run at startup; Hibernate validates the result. `node scripts/run-local.mjs` generates ignored random database/MFA secrets when absent and preserves configuration/data.

GitHub Actions runs frontend validation, Java/H2/PostgreSQL tests, offline CDK assertions/synthesis, security checks and Compose/browser flows. Pinned actions and read-only repository permissions support review. CI has no AWS deploy job.

`infrastructure/lib/monelytics-stack.ts` is an offline EC2/EBS/VPC/IAM/Systems Manager/CloudFormation reference for a single-host Docker/PostgreSQL stack. Every resource has a constant-false provisioning condition, and deployment scripts stop before AWS calls. Local Compose is the supported permanent zero-spend runtime; no AWS resources are provisioned by this repository. See [AWS instructions](AWS-DEPLOYMENT.md).

## Explain the choices

The monorepo keeps UI, API, schema and infrastructure reviewable together. Spring services separate financial mutations, planning, analytics, authentication and education without microservice operations. PostgreSQL provides exact numeric storage, transactional constraints and shared sessions. Cookie authentication limits token exposure while requiring CSRF protection. Native SVG charts support accessible visualizations without a large chart dependency.

Tests verify ownership, real-cookie authentication, ledger reversal, simultaneous transfers/contributions, idempotent payments, preserved migration data, local classification and browser accessibility. H2 alone is insufficient evidence for PostgreSQL behavior. Infrastructure synthesis is an architectural artifact, not proof of live hosting, cloud eligibility or operational readiness.

See [API](API.md), [database](DATABASE.md), [security](SECURITY.md) and [testing](TESTING.md).