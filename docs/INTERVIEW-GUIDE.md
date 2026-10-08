# WealthPath interview guide

## Two-minute summary

“I built WealthPath, a retirement planning application, with Angular and TypeScript, Java 21 and Spring Boot, PostgreSQL, and Docker. Users can register, sign in, optionally enable MFA, manage IRA accounts and beneficiary allocations, record and search retirement activity, and compare retirement scenarios. A dashboard derives balances and contribution progress from their records.

The project emphasizes secure application boundaries: HTTP-only server sessions, CSRF protection, BCrypt passwords, role checks, and ownership validation. Ledger changes update balances and audit events in a single database transaction. Automated tests cover financial calculations, forms, authentication, authorization, and live browser workflows.

For deployment, I defined an AWS architecture with CloudFront and S3 for Angular, ECS Fargate and ECR for the API, private RDS PostgreSQL, Secrets Manager, and CloudWatch. GitHub Actions runs checks before deployment. It uses synthetic data; I would add reviewed recovery, compliance, and operational controls before handling customer financial information.”

## Five-minute walkthrough

1. Show the dashboard, account allocation, transaction search, and calculator. Describe which figures come from saved records and which are modeled scenarios.
2. Open `app.routes.ts` and the typed API service. Explain lazy loading, reactive forms, guards, interceptors, and why server authorization remains necessary.
3. Trace a contribution from DTO validation to ownership checks, database locking, exact money arithmetic, and atomic persistence.
4. Show the migrations and indexes, then explain shared JDBC sessions and environment-injected configuration.
5. Show tests, CI, Docker Compose, and the AWS CDK stack. State clearly that AWS resources require a separate deployment.

## Ten-minute system design discussion

Start with requirements: private user data, consistent balances, searchable ledger, projections, and operational visibility. Draw the architecture diagram. Explain a same-origin browser/API deployment, then identify the trust boundaries: browser to API, service to database, CI to AWS, and operator to secrets.

Discuss transaction isolation and concurrency. Reversing an edited transaction and applying its replacement must happen together. Account locks protect the balance. For higher throughput, measure hot-account contention rather than immediately adding distributed services. Database connection capacity must account for the number of API replicas.

Discuss scaling: CloudFront handles static delivery, ECS scales API tasks, RDS stores records and sessions, and pagination bounds response size. Current per-process throttling needs a shared edge control at scale. Audit logs support investigation but are not immutable compliance records.

Finish with recovery: retain database backups, define recovery objectives, test restores, rotate secrets through a reviewed process, and monitor readiness, request errors, and deployment health. Infrastructure synthesis and local tests are useful evidence but cannot replace an actual cloud deployment and operational exercise.

## Likely questions

| Question | Answer grounded in this project |
|---|---|
| Why Angular? | Strict TypeScript contracts, reactive forms, routing, DI, HttpClient, and reusable standalone components fit a structured enterprise UI. |
| Why not trust route guards? | The browser is controlled by the user. Spring Security and ownership-scoped services enforce authorization on direct HTTP requests. |
| How do you prevent cross-user reads? | Derive identity from the session, scope queries by owner, and conceal foreign resources. Test with two independently authenticated users. |
| Why BigDecimal? | Binary floating point cannot exactly represent many decimal currency values. The ledger uses exact decimal types through Java and SQL. |
| Why cookie sessions? | HttpOnly cookies keep session identifiers out of JavaScript storage. JDBC sessions support multiple replicas; CSRF protection is mandatory. |
| How do edits affect balance? | Reverse the old ledger effect, validate the new effect, apply it, and commit everything atomically. |
| How are secrets handled? | Ignored environment files locally; injected managed secrets in ECS. Production disables demo data and requires TLS. |
| Is this a tax calculator? | No. The application has a configurable contribution policy and transparent projection assumptions. Eligibility and tax rules are outside its current scope. |
| How do you observe failures? | Health/readiness endpoints, structured logs with request IDs, safe error responses, and CloudWatch-ready stdout. |
| What would you improve next? | Reviewed account recovery, MFA recovery codes, shared throttling, immutable audit export, load tests, restore drills, and compliance review for real data. |

## Practical tradeoffs

One deployable API reduces operational overhead while retaining separate controller, service, repository, DTO, and security responsibilities. A single PostgreSQL system provides strong transactional behavior and shared sessions, with a corresponding dependency on database availability. Goals are independent scenarios so changing planning assumptions cannot silently move or reclassify account money. Native charts avoid a larger UI library but require explicit accessibility summaries.

## Development evidence

Use `docs/TESTING.md` for the final measured verification record and `docs/TROUBLESHOOTING.md` for reproducible operational issues. Do not claim latency, scale, coverage percentages, regulatory certification, or an AWS production deployment unless independently measured or completed.
