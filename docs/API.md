# REST API

The browser calls same-origin `/api` routes through Nginx. Local defaults are `http://localhost:8080` for the app and `http://localhost:8081` for the direct API; `.env` can change ports. OpenAPI JSON is `/v3/api-docs` and Swagger is `/swagger-ui/index.html` while `API_DOCS_ENABLED=true`. Disable API documentation explicitly before sharing a deployment.

Requests/responses use JSON except CSV export. Identifiers are UUIDs, dates use `YYYY-MM-DD`, and timestamps use ISO 8601 UTC. Money uses exact Java/PostgreSQL decimals; ledger input permits at most two fractional digits. The authenticated user comes from the session, never a request-supplied user ID. New resource POSTs return `201`, normal reads/updates return `200`, and ordinary DELETEs return `204`; payment and goal-contribution operations return `200` with resulting data.

## Authentication, cookies and recovery

| Method | Path | Request and result |
| --- | --- | --- |
| GET | `/api/auth/csrf` | Returns `{token,headerName}` and the `XSRF-TOKEN` cookie |
| POST | `/api/auth/register` | `{firstName,lastName,email,password}`; returns profile and establishes session |
| POST | `/api/auth/login` | `{email,password,code?}`; returns profile and establishes session |
| GET | `/api/auth/me` | Returns authenticated profile |
| POST | `/api/auth/logout` | Invalidates session and clears CSRF token |
| POST | `/api/auth/forgot-password` | `{email}`; generic `{message}` for known, unknown and account-throttled addresses |
| POST | `/api/auth/reset-password` | `{token,password}`; returns `{message}`, consumes links and revokes sessions |
| POST | `/api/auth/mfa/setup` | `{password}`; returns `{secret,otpAuthUri}` |
| POST | `/api/auth/mfa/enable` | `{password,code}`; returns updated profile |
| POST | `/api/auth/mfa/disable` | `{password,code}`; returns updated profile |
| PUT | `/api/settings` | `{firstName,lastName}`; returns updated profile |

First call `GET /api/auth/csrf`, preserve its cookie and send the returned raw value in `X-XSRF-TOKEN` for every POST, PUT and DELETE, including registration, login, recovery and assistant requests. Obtain a fresh token after registration, login or logout. Retain the HttpOnly `MONELYTICS_SESSION` cookie after authentication. Both cookies use SameSite=Lax; `COOKIE_SECURE=true` enables their Secure flag for HTTPS.

A profile is `{id,firstName,lastName,email,role,mfaEnabled}`. Public registration always creates a `USER`; `ADMIN` is assigned through controlled initialization, not a registration field. Registration creates default categories and an empty financial portfolio. Registration/reset passwords require 12-72 characters, upper/lower case letters, a digit and a symbol, with an additional maximum of 72 UTF-8 bytes. MFA codes contain six digits. MFA-required login returns `401` with code `MFA_REQUIRED` until a valid code accompanies the password; replayed codes are rejected.

Recovery links expire after 30 minutes. Recovery does not disable MFA. Local mail is captured at the Mailpit viewer, normally `http://localhost:8025`; tokens are never returned by the API. Treat credential-bearing requests and MFA setup replies as private.

## Accounts and beneficiaries

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/api/accounts` | List or create owned accounts |
| PUT, DELETE | `/api/accounts/{id}` | Rename/change an eligible account or delete an empty unused account |
| PUT | `/api/accounts/{id}/beneficiaries` | Replace IRA beneficiary allocations |

Account creation accepts `{name,type,openingBalance}`; update accepts `{name,type}`. Types are `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `ROTH_IRA` and `TRADITIONAL_IRA`. Responses contain `{id,name,type,balance,annualContributions,beneficiaries}`. A negative credit-card balance represents debt; other account types cannot overdraw. Opening balances record preexisting holdings and do not count as income or contributions. An account with recorded transactions cannot change type. Clear IRA beneficiary allocations before changing to a non-IRA type. Deletion requires zero balance, no source/destination transactions and no recurring plans.

Beneficiary input is `{beneficiaries:[{name,relationship,percentage}]}`. Nonempty allocations are restricted to IRA accounts and must total exactly 100%; `[]` clears an allocation. At most 12 beneficiaries are accepted. The response is the updated account with generated beneficiary IDs.

```json
{"name":"Everyday checking","type":"CHECKING","openingBalance":1750.00}
```

## Transactions and CSV

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/api/transactions` | Search owned ledger entries or create an entry |
| PUT, DELETE | `/api/transactions/{id}` | Correct or reverse an entry atomically |
| GET | `/api/transactions/export.csv` | Stream all matching owned entries as CSV |

Transaction input is `{accountId,type,amount,description,date,categoryId?,destinationAccountId?}`. Amount must be positive and the date cannot be future. General finance types are `INCOME`, `EXPENSE` and `TRANSFER`; retained IRA types are `CONTRIBUTION`, `WITHDRAWAL`, `ROLLOVER` and `RETURN`. Expenses/withdrawals debit the source; income/contributions/rollovers/returns credit it. A transfer debits the source and credits a different owned destination in one transaction, requires `destinationAccountId` and has no category. Other types must not specify a destination. Optional categories must be owned and match the income/expense direction. Contribution entries require an IRA and count across the owner's IRA accounts against a configurable educational policy, not a determination of tax eligibility.

Editing reverses the old effect before applying the new effect inside the same transaction. A posted entry cannot change its source account. Deleting reverses its effect and can be rejected if reversal would overdraw a non-credit account. A recurring payment must remain an expense when edited.

```json
{"accountId":"<owned-checking-uuid>","type":"EXPENSE","amount":91.25,"description":"Weekly groceries","date":"2026-10-07","categoryId":"<owned-expense-category-uuid>"}
```

```json
{"accountId":"<owned-checking-uuid>","type":"TRANSFER","amount":200.00,"description":"Savings transfer","date":"2026-10-07","destinationAccountId":"<owned-savings-uuid>"}
```

Responses contain `{id,accountId,accountName,type,amount,description,date,categoryId,categoryName,destinationAccountId,destinationAccountName}`. Nullable category/destination fields remain null when unused.

Filters: `search`, `accountId`, `categoryId`, `type`, `from`, `to`. Account filtering includes both transfer source and destination. Search matches descriptions, account names and category names, with a maximum of 240 characters. Listing accepts zero-based `page` (0-100000), `size` (1-100; default 10) and `sort` (default `date,desc`). Allowed sort fields are `date`, `amount`, `type`, `description` and `accountName`, with `asc`/`desc`. Responses are `{content,totalElements,totalPages,number,size}`.

Example: `/api/transactions?type=EXPENSE&from=2026-10-01&to=2026-10-31&page=0&size=10&sort=amount,desc`.

CSV export accepts the same filters/sort without pagination parameters. It emits UTF-8 with a BOM and quoted cells, escapes spreadsheet formula prefixes, reads database rows in 500-row batches and records an audit event. It is bounded in memory, not restricted to the current page.

## Categories and monthly budgets

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/api/categories` | List or create owned categories |
| PUT, DELETE | `/api/categories/{id}` | Update or delete an unused category |
| GET, POST | `/api/budgets` | List a month's budgets or create a budget |
| PUT, DELETE | `/api/budgets/{id}` | Update or delete a budget |

Category input is `{name,type,color}`, where type is `INCOME`/`EXPENSE` and color is `#RRGGBB`. Responses add `id`. Names are unique per owner after trimming/case normalization. Categories referenced by entries, budgets or recurring plans cannot be deleted or change direction.

Budget input is `{categoryId,month,limitAmount}`. Use `categoryId:null` for an overall budget or an owned expense category for a category budget. `month` uses `YYYY-MM`, from 1900 through 2100; the limit is positive. Each owner may have one budget per month/scope. GET accepts optional `month`, defaulting to the current UTC month. Actual spending counts only `EXPENSE` entries, including uncategorized entries in the overall total; transfers do not consume budgets.

Budget views are `{id,categoryId,categoryName,month,limitAmount,spentAmount,remainingAmount,percentage,status}`. Status is `ON_TRACK`, `NEAR_LIMIT` at 80% or more, or `OVER_BUDGET` at 100% or more. Remaining amount may be negative. An overall/category budget constrains the same money; affordability uses the tighter remaining amount rather than adding them.

```json
{"categoryId":null,"month":"2026-10","limitAmount":3600.00}
```

## Bills and subscriptions

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/api/recurring` | List/create plans; optional `kind` filter |
| PUT, DELETE | `/api/recurring/{id}` | Edit/archive or remove a plan, retaining posted payments |
| POST | `/api/recurring/{id}/pay` | Record an occurrence once and return its expense |

Plan input is `{name,kind,accountId,categoryId,amount,frequency,nextDueDate,active}`. Kind is `BILL`/`SUBSCRIPTION`, frequency is `MONTHLY`/`YEARLY`, and the owned category must be an expense category. Views add `id`, `accountName` and `categoryName`. Set `active:false` to pause a plan.

Payment input is `{dueDate,date}`: send the displayed `nextDueDate` as `dueDate` and the actual nonfuture payment date as `date`. The server rejects stale occurrences and atomically creates an expense plus advances the plan. Retrying an already-recorded occurrence returns the same transaction without a second debit. Month-end recurrence retains its anchor day (January 31 becomes February 28, then March 31). Deleting a posted recurring expense reverses its balance effect and reopens that occurrence. Removing the plan preserves posted expenses.

## Savings goals and projections

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/api/goals` | List or create savings goals |
| PUT, DELETE | `/api/goals/{id}` | Update or remove a goal |
| GET, POST | `/api/goals/{id}/contributions` | Read allocation history or add an allocation |
| DELETE | `/api/goals/{id}/contributions/{contributionId}` | Reverse an allocation and return updated goal |
| POST | `/api/goals/calculate` | Optional monthly-compounded retirement projection |

Goal input is `{name,targetAmount,currentAmount,targetDate,monthlyContribution,expectedReturn}`; views add `id`. Target is positive, date is future, amounts are nonnegative and expected annual return is 0-20 percent. `currentAmount` tracks an independent savings allocation, not the sum of bank balances. Updating it cannot reduce the amount below recorded contributions. Contribution input is `{amount,date,note?}`; note may be omitted/empty. History contains `{id,amount,date,note}`; create/delete return updated goal. Allocations never invent bank movements or expenses.

Projection input:

```json
{"currentAge":35,"retirementAge":65,"currentSavings":50000,"monthlyContribution":500,"annualReturn":6,"targetAmount":1000000}
```

Response: `{projectedBalance,totalContributions,investmentGrowth,gap,monthlyNeeded,yearlyProjection:[{age,balance,contributions}]}`. Results exclude taxes, fees, inflation, withdrawals and market volatility; they are scenarios, not guaranteed investment performance.

## Dashboard, analytics, notifications and assistant

GET `/api/dashboard`, `/api/analytics`, `/api/notifications` and `/api/budgets` accept optional `month=YYYY-MM`. Trends cover that month and the five preceding months. Dashboard `totalBalance` is the current signed account total even when a historical month is selected; negative card balances reduce it. Monthly income, expenses, cash flow and charts use the selected period. Upcoming-bill notifications and savings progress use current planning records.

Analytics returns `{month,income,expenses,cashFlow,savingsRate,spendingTrends,categoryBreakdown}`. Trends contain `{month,income,expenses,cashFlow}`, categories contain `{categoryId,name,color,value}`. Savings rate is recorded `(income-expenses)/income*100`, or zero without income, and may be negative.

Dashboard adds `totalBalance`, `annualContributions`, `contributionLimit`, `goalProgress`, `monthlyChange`, `accounts`, `recentTransactions`, `balanceHistory`, `allocation`, `budgets`, `goals` and `notifications` to analytics fields. Goal progress is the overall percentage of allocated savings against goal targets. Allocation charts include positive account assets; card debt remains reflected in the signed total.

Notifications contain `{id,type,title,message,severity,resourceId}`. Types are `BUDGET`, `BILL`, `UNUSUAL_SPENDING`, `GOAL_MILESTONE`; severity is `INFO`, `WARNING` or `CRITICAL`. They are derived at request time. Unusual spending requires an expense above $100 and twice the prior three months' category average, with at least three prior entries. Goal milestones report the highest reached 25/50/75/100% threshold. Alerts do not schedule payments or deliver external messages.

POST `/api/assistant/chat` accepts `{question,purchaseAmount?,categoryId?,month?}` and returns `{answer,provider,decision,factors,recommendations,disclaimer,readOnly,asOf}`. Factors are `{label,value,explanation}`. Decisions are `NOT_REQUESTED`, `INSUFFICIENT_DATA`, `NOT_AFFORDABLE`, `CAUTION` or `LIKELY_AFFORDABLE`. Assessment uses owned records, reserves upcoming bills/planned savings and card debt, and considers budget room. It never changes the ledger. Mock is default; optional local Ollama classifies education topics only and receives no financial records. See [README](../README.md#assistant-and-privacy).

## Audit, health and errors

GET `/api/audit?page=0&size=20` returns an administrator-only page of `{id,actorId,action,resourceId,detail,occurredAt}`. Size is 1-100. Important authentication, finance, export and recovery actions are audited. GET `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` are public operational endpoints; readiness includes database availability without exposing private health details.

JSON requests are capped at 256 KiB, including missing Content-Length. Unknown properties are rejected. Errors use safe Problem Details with `status`, `title`, `detail`, `code`, `requestId` and an optional `errors` map keyed by invalid field. `X-Request-ID` correlates structured logs. Statuses include `400` validation/malformed requests, `401` missing/rejected authentication, `403` CSRF/role denial, `404` missing/foreign-owned resources, `405` unsupported methods, `409` conflicts, `413` oversized bodies, `415` unsupported media, `422` business rules and `429` throttling. Responses omit stack traces, database messages and secrets.

See [security](SECURITY.md), [database](DATABASE.md) and generated OpenAPI DTO constraints.
