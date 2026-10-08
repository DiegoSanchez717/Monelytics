# Operations and troubleshooting

## First checks

Run `docker compose ps`. The database, backend, and frontend should be healthy. Inspect bounded logs with `docker compose logs --tail=100 backend` or `frontend`. The API readiness endpoint includes database availability; the public health response does not expose internal connection details. Use the response `X-Request-ID` to find the corresponding structured log record.

Do not post `.env`, cookies, authenticator keys, or complete browser traces to public issues. Audit events provide action history without credential payloads. CloudWatch retention and IAM access are defined by the AWS stack.

| Symptom | Resolution |
|---|---|
| Docker named-pipe/daemon connection error | Start Docker Desktop in Linux-container mode and wait for its engine. `docker version` must show a server. |
| Address already in use | Change `FRONTEND_PORT`, `BACKEND_PORT`, or development `DATABASE_PORT` in `.env`. Do not stop unrelated services. |
| Missing configuration at Compose startup | Run `node scripts/setup-local.mjs`. Existing `.env` is preserved. |
| MFA key must contain 32 bytes | Generate a 32-byte base64 key for a new database; keep an existing enrollment key stable. A new unrelated key cannot decrypt existing MFA secrets. |
| Database authentication fails after changing `.env` | The persistent PostgreSQL volume retains its original database password. Update the database credential intentionally or restore the original environment value. |
| HTTP 401 | Sign in again. The session expired, credentials failed, or MFA is required. |
| HTTP 403 on a mutation | Fetch a fresh CSRF token after a session boundary; preserve cookies and send the returned header. Check role requirements. |
| HTTP 429 / repeated login failures | Per-instance throttling or durable account lockout is active. Wait for the stated interval; do not weaken protection to bypass it. |
| Contribution rejected | Contributions share the configurable annual policy across all the user's IRAs. Rollovers and opening balances are recorded separately. |
| Account removal rejected | Only a zero-balance account without posted transactions can be removed. The UI preserves account history. |
| Transaction deletion rejected | Reversing that transaction would make the account balance negative. Correct the dependent records rather than allowing an overdraft. |
| Windows blocks `npm.ps1` | Use `npm.cmd` / `npx.cmd`; changing PowerShell security policy is unnecessary. |
| Java/Maven missing | Install Java 21, set `JAVA_HOME`, and use the repository Maven wrapper. Docker builds supply their own Java/Maven toolchain. |
| Testcontainers cannot find Docker | Verify Docker Engine availability and supported API negotiation; see testing prerequisites. |
| CDK synthesis fails | Install locked dependencies, verify Node 24.x, run infrastructure tests, and review required deployment context. Synthesis does not require paid resources. |
| CloudFront returns origin TLS errors | The API origin DNS name must route to the ALB and match its regional ACM certificate. The CloudFront viewer certificate must be in us-east-1. |
| Browser route reload returns 404 | Nginx fallback or CloudFront static-route rewrite must be enabled. API errors must retain JSON/status rather than falling back to Angular HTML. |
| Production UI loads without styling | Keep Angular `inlineCritical: false`; its default inline stylesheet loader conflicts with the strict script policy. Verify computed styles through the Compose browser tests. |
| Frontend serves HTTP but its health check fails | Use `127.0.0.1` in the internal health URL to match Nginx's IPv4 listener. `localhost` can resolve to IPv6. |

## Database and rollout operations

Back up the database before schema changes and verify restoration. Flyway validates migration checksums; do not modify already-applied migrations. Deploy additive migrations before removing application fields. ECS uses health checks and rollback-enabled circuit breaking. Watch readiness, target 5xx counts, RDS capacity, task restarts, and deployment status.

The local stack uses a persistent named volume. `docker compose down` stops containers without deleting data. Removing volumes is a deliberate destructive reset and should be limited to disposable development databases.

AWS teardown retains critical state by design: RDS deletion protection and snapshots, S3 versions, secrets, and logs may continue billing after application removal. Follow [AWS-DEPLOYMENT.md](AWS-DEPLOYMENT.md), verify target account/region, and review retained resources before deletion.
