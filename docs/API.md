# REST API

Local UI: `http://localhost:8080`. Direct API: `http://localhost:8081`. Interactive development documentation: `http://localhost:8081/swagger-ui/index.html`; OpenAPI JSON: `/v3/api-docs`. Production disables public API documentation by default.

Requests and responses use JSON. Monetary fields are decimal values with a maximum of two fractional digits for ledger data. Identifiers are UUIDs. Dates use `YYYY-MM-DD`; timestamps use ISO 8601 UTC. The authenticated identity comes from the session, never from a supplied user ID.

JSON requests are limited to 256 KiB, including chunked requests. Oversized bodies receive `413`; unsupported methods and media types receive `405` and `415` with safe responses.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/csrf` | Obtain mutation token and header name |
| POST | `/api/auth/register` | Register a user |
| POST | `/api/auth/login` | Verify password and optional six-digit TOTP |
| GET | `/api/auth/me` | Read authenticated profile |
| POST | `/api/auth/logout` | Invalidate session |
| POST | `/api/auth/mfa/setup` | Verify password and create authenticator secret |
| POST | `/api/auth/mfa/enable` | Verify password and TOTP before enabling |
| POST | `/api/auth/mfa/disable` | Verify password and TOTP before disabling |
| GET, POST | `/api/accounts` | List or create owned IRA accounts |
| PUT, DELETE | `/api/accounts/{id}` | Update or remove an eligible owned account |
| PUT | `/api/accounts/{id}/beneficiaries` | Replace allocation; nonempty allocation totals 100% |
| GET, POST | `/api/transactions` | Search ledger or create entry |
| PUT, DELETE | `/api/transactions/{id}` | Edit or delete with atomic balance adjustment |
| GET, POST | `/api/goals` | List or create retirement scenarios |
| PUT, DELETE | `/api/goals/{id}` | Update or remove a scenario |
| POST | `/api/goals/calculate` | Calculate monthly-compounded projection |
| GET | `/api/dashboard` | Owned balances, contributions, trends, and recent activity |
| PUT | `/api/settings` | Update profile names |
| GET | `/api/audit` | Paginated audit events; ADMIN only |
| GET | `/actuator/health` | Operational health |

## Cookies and CSRF

First call `GET /api/auth/csrf`, preserving cookies. Read `{token, headerName}` and send that header on every POST, PUT, and DELETE. Fetch a fresh token after login or logout. The session cookie is HttpOnly; browser JavaScript cannot read it. The UI and API share an origin in Compose and AWS.

Registration accepts `{firstName,lastName,email,password}`. Login accepts `{email,password,code?}`. Passwords require 12–72 characters plus the strength requirements shown in the UI. MFA setup accepts `{password}` and returns `{secret,otpAuthUri}`; enable and disable accept `{password,code}`. Never log these payloads.

## Examples

Create an account:

```json
{"name":"Long-term Roth","type":"ROTH_IRA","openingBalance":18000.50}
```

Record activity:

```json
{"accountId":"<owned-account-uuid>","type":"CONTRIBUTION","amount":400.00,"description":"Monthly retirement contribution","date":"2026-10-07"}
```

Transaction types are `CONTRIBUTION`, `WITHDRAWAL`, `ROLLOVER`, and `RETURN`. A withdrawal decreases balance. Other types increase it; only contributions count toward the configurable annual contribution policy. Contributions count across the user's IRA accounts. The configurable threshold is an educational application policy, not a determination of tax eligibility.

Search example: `/api/transactions?search=monthly&type=CONTRIBUTION&from=2026-01-01&to=2026-12-31&page=0&size=10&sort=date,desc`. Supported sort fields are allowlisted by the API. Pagination responds with `{content,totalElements,totalPages,number,size}`.

Goal calculator:

```json
{"currentAge":35,"retirementAge":65,"currentSavings":50000,"monthlyContribution":500,"annualReturn":6,"targetAmount":1000000}
```

The response includes projected balance, contributed principal, modeled growth, target gap, monthly amount needed, and annual projection points. Results exclude taxes, fees, inflation, withdrawals, and market volatility.

## Errors

`400` indicates invalid input; `401` indicates missing/expired authentication or rejected credentials; `403` indicates invalid CSRF or insufficient role; `404` conceals resources not owned by the user; `409` indicates a data conflict; `422` indicates a business rule such as overdraft, contribution policy, or beneficiary allocation; `429` indicates throttling. Field errors help the UI display validation messages. Error responses do not expose stack traces or database details. `X-Request-ID` correlates a request with structured server logs.
