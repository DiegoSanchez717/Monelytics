# Operations and troubleshooting

## Start with the local stack

Run these commands from the repository root:

```sh
docker compose ps
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend
```

The database, backend, frontend, and Mailpit should become healthy. First startup downloads images and dependencies; backend readiness waits for database migrations and initialization. Local setup is `node scripts/run-local.mjs`; `node scripts/setup-local.mjs` generates missing environment configuration without replacing an existing `.env`.

Default URLs are the app at `http://localhost:8080`, API readiness at `http://localhost:8081/actuator/health/readiness`, and recovery email viewer at `http://localhost:8025`. Use your configured `FRONTEND_PORT`, `BACKEND_PORT`, and `MAILPIT_UI_PORT` if changed. The browser calls `/api` on the app origin; Nginx forwards those requests to the backend container's port 8080. PostgreSQL stays private unless the explicit development Compose override is used.

Use an API response's `X-Request-ID` to find its structured log record. Keep environment secrets, session cookies, authenticator secrets, recovery links, and browser traces out of public issues.

## Startup, browser, and authentication

| Symptom                                                      | Check or resolution                                                                                                                                                                                                                |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Docker daemon or named-pipe connection error                 | Start Docker Desktop in Linux-container mode, or your Docker Engine service. `docker version` must show a server.                                                                                                                  |
| Address already in use                                       | Change the corresponding port in `.env`; preserve services belonging to other projects. `DATABASE_PORT` applies only to `docker-compose.dev.yml`.                                                                                  |
| Compose reports a missing variable                           | Run `node scripts/setup-local.mjs`, then review the ignored `.env`. Existing configuration is preserved.                                                                                                                           |
| Database authentication fails after editing `.env`           | The persistent PostgreSQL volume retains its original credentials. Restore the original value or intentionally update the database credential. Editing an environment variable does not change an existing database password.      |
| Backend remains unhealthy                                    | Read backend logs for migration, database, or MFA-key errors. Check readiness through the configured API port; frontend startup depends on backend readiness.                                                                      |
| Frontend returns HTTP but its internal health check fails    | The container health URL is `http://127.0.0.1:8080/health`. Using `localhost` can select IPv6 while Nginx listens on IPv4.                                                                                                         |
| API requests fail after replacing the backend container      | Restart the frontend so Nginx resolves the new backend address. Compose's backend dependency restart setting handles coordinated replacements.                                                                                     |
| A browser refresh on `/budgets` or another route returns 404 | Preserve Nginx's Angular SPA fallback. `/api` errors must remain API errors with their original status, rather than returning the Angular HTML page.                                                                               |
| Production page loads without styling                        | Keep Angular `inlineCritical: false`. Its inline stylesheet-loader handler conflicts with the strict script CSP. Rebuild the frontend and check actual computed styles through the Compose browser tests.                          |
| Browser shows an old version after source changes            | Rebuild with `docker compose up --build --wait --wait-timeout 300`. Building Angular locally does not replace the running frontend image.                                                                                          |
| Native Angular development reaches the wrong API             | Align `frontend/proxy.conf.json` with the native backend or configured Compose API port. Angular normally runs on 4200; the native Spring Boot server normally runs on 8080.                                                       |
| Windows blocks `npm.ps1`                                     | Use `npm.cmd` and `npx.cmd`; changing PowerShell execution policy is unnecessary.                                                                                                                                                  |
| HTTP 401                                                     | Sign in again. Check credentials and, if enabled, the authenticator code. An MFA-enabled login requires both the password and current code.                                                                                        |
| HTTP 403 on a mutation                                       | Retain cookies, fetch `/api/auth/csrf`, and send the returned token under the returned header name. Obtain a fresh token after registration, login, logout, or recovery. Check user ownership and administrator role requirements. |
| HTTP 429 or repeated failed logins                           | Wait for the retry/lockout interval. Per-instance throttling and durable account lockout protect authentication.                                                                                                                   |
| MFA enrollment cannot decrypt its secret                     | `MFA_ENCRYPTION_KEY` must decode to 32 bytes and remain stable for an enrolled database. A different random key cannot decrypt existing secrets. Preserve the key with encrypted backups.                                          |
| A valid-looking MFA code is rejected                         | Check the authenticator and server clocks. Recently used codes cannot be replayed. Password recovery preserves enabled MFA rather than bypassing it.                                                                               |

## Password recovery and local email

Recovery always returns a generic message so it does not reveal whether an email is registered. In the local stack, look for the message in Mailpit at the configured email-viewer port. Mailpit captures email locally and has no outbound relay configured.

| Symptom                             | Check or resolution                                                                                                                                                                                    |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| No recovery email appears           | Check that Mailpit is healthy and inspect bounded backend logs. Use the registered email address and allow for request throttling. The generic success message alone does not prove an account exists. |
| Recovery link opens the wrong port  | Set `PUBLIC_BASE_URL` to the browser's app URL and request a new link. Previously issued email links retain their original URL.                                                                        |
| Reset link is invalid or expired    | Request a new link. Tokens expire after 30 minutes, work once, and newer requests invalidate older tokens.                                                                                             |
| Sign-in stops working after a reset | Use the new password. A reset revokes existing sessions; an enabled authenticator is still required.                                                                                                   |

## Financial records and business rules

Monelytics records the information entered into the application. Opening balances are not income, savings-goal contributions are not bank transfers, and bill payments are records of expenses already made.

| Symptom                                                      | Check or resolution                                                                                                                                                                                                |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Checking, savings, or IRA rejects a negative balance         | These accounts cannot be overdrawn in the ledger. Only credit-card balances may be negative; a negative credit-card balance represents debt.                                                                       |
| Account deletion or type change is rejected                  | Deletion requires a zero balance and no transaction history. Account type cannot change after transactions have been posted; renaming remains available.                                                           |
| Transaction editing/deletion is rejected                     | Reversing the old entry may violate a non-credit account's balance rule. Correct dependent records deliberately. Transfers update both recorded accounts atomically.                                               |
| Income/expense category is rejected                          | Use an owned category of the matching type. Transfers and IRA activity do not take income/expense categories.                                                                                                      |
| Money or date validation fails                               | Enter amounts with at most two decimal places. Transactions, recorded payments, and savings contributions require dates on or before today; goal target dates must be in the future.                               |
| Budget summary differs from the sum of cards                 | An overall monthly budget overlaps category budgets. When an overall budget exists, summary totals use that record to avoid double counting. Without one, the summary covers the listed category budgets.          |
| A category cannot be deleted                                 | Reassign or remove its transaction, budget, or recurring-item references first. The application preserves linked records.                                                                                          |
| Recording a bill payment changes its due date                | One expense is recorded and the next recurrence advances. A retry with the same due-date snapshot returns the existing payment instead of creating another expense. Removing that payment restores the occurrence. |
| Savings contribution changes goal progress but not net worth | This is expected: a goal contribution tracks money earmarked for a purpose. Record actual account activity separately. A contribution note may be empty.                                                           |
| Monthly reports differ from account balance changes          | Reports count income and expense records for the selected month. Opening balances, transfers, and IRA activity are excluded from cashflow. Net worth uses current signed account balances.                         |
| Alerts reappear after signing out or reloading               | Notifications are derived from current records. “Dismiss for this visit” hides an alert in the current UI; it does not resolve a budget limit or bill due date.                                                    |
| CSV shows fewer records than expected                        | Check the current search, account, category, type, and date filters. Export includes every matching record, beyond the current page. Spreadsheet-formula-leading text is escaped deliberately.                     |
| IRA contribution or beneficiary update is rejected           | Contributions share the configured annual policy across the user's IRAs. Beneficiary allocations must total 100%; an empty beneficiary list clears assignments.                                                    |

## Assistant behavior and the zero-spend policy

The default assistant uses the MOCK provider without an API key or a paid API call. Its factors and affordability decisions come from the signed-in user's actual records. No purchase assessment creates a transaction or changes a balance.

| Symptom                                                  | Check or resolution                                                                                                                                                                                                  |
| -------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Assistant reports insufficient information               | Record current income and expenses. An opening balance alone is not recorded income.                                                                                                                                 |
| A purchase receives caution or appears unaffordable      | Review the displayed cashflow, liquid balances, card debt, unpaid bills, savings commitments, and applicable budget room. A historical month or missing category can also produce caution.                           |
| Categories are unavailable                               | Retry the category request or check the current session. General questions can omit a purchase category.                                                                                                             |
| Optional local model still shows “Demo assistant”        | Blank keys select mock. Local opt-in needs `AI_PROVIDER=local`, `AI_API_KEY=local-only`, a supported local URL, and an already-downloaded model. Invalid output, unavailable models, and timeouts fall back to mock. |
| A remote model endpoint or paid-provider key is rejected | This is intentional. The supported optional provider is local Ollama; remote URLs, paid providers, and cloud-routed models are blocked.                                                                              |
| AWS deployment command exits without deploying           | This is expected. AWS provisioning is disabled, and `--deploy` fails before credentials or AWS execution. Local Compose is the supported $0 runtime.                                                                 |
| CDK synthesis fails                                      | Install the locked infrastructure dependencies and use Node 24.x. Run `npm test --prefix infrastructure` and `npm run synth --prefix infrastructure`. Synthesis creates an offline reference, not cloud resources.   |

The AWS reference describes EC2, EBS, VPC, IAM, Systems Manager, and CloudFormation. It has no inbound rules, does not start the application, and places every resource behind a constant-false provisioning condition. Tests and CI make no deployment calls. See [AWS readiness and deployment guards](AWS-DEPLOYMENT.md) for the reference's scope and the separate read-only account-plan checker. There is no CloudFront, ECS, RDS, ALB, or CloudWatch runtime to troubleshoot in this project.

## Development checks and data preservation

Java development needs Java 21 and the repository Maven wrapper. Docker builds supply their own toolchain. PostgreSQL integration tests need a running Docker Engine; use the `postgres-it` Maven profile. Frontend checks run with the locked Angular/TypeScript dependencies and Node 24.x. Browser tests need the healthy Compose stack, including Mailpit for recovery flows.

```sh
npm run lint --prefix frontend
npm run format:check --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
```

Use `npm.cmd` on Windows when needed. See [testing instructions](TESTING.md) for the full Java, infrastructure, and browser commands.

Back up the database and stable MFA key before migrations, and verify restoration. Flyway validates applied migration checksums; preserve existing migrations and keep Hibernate schema validation enabled. `docker compose stop` or `docker compose down` stops the local stack without deleting its named database volume. Removing volumes is a deliberate reset of disposable data, not a routine fix for a configuration or migration error.
