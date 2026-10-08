# Local containers at zero cloud cost

Use open-source [Docker Engine](https://docs.docker.com/engine/install/) with Compose v2. Docker Desktop is also available without a subscription for eligible personal, educational and small-business use; its [license conditions](https://docs.docker.com/subscription-billing/desktop-license/) differ from Docker Engine. Monelytics needs no AWS account, paid database, SMTP subscription or AI API key.

```sh
node scripts/run-local.mjs
```

The helper creates an ignored `.env` with random database and MFA secrets if none exists, then starts the four-container application. It preserves an existing `.env`. Alternatively run `node scripts/setup-local.mjs`, followed by `docker compose up --build --wait`.

Open the application at `http://localhost:8080` and the local email inbox at `http://localhost:8025`. Angular's nginx container forwards `/api` to Spring Boot. PostgreSQL has no host port in the primary configuration. All published ports bind to `127.0.0.1`.

| Service | Default localhost port | Purpose |
| --- | ---: | --- |
| Frontend | 8080 | Application and same-origin API |
| Backend | 8081 | Direct health, Swagger and debugging |
| Mailpit UI | 8025 | Captured password-reset messages |
| Mailpit SMTP | 1025 | Local SMTP testing and native Java development |
| PostgreSQL | None | Container access only; optional development override uses 5432 |

Set `BACKEND_PORT=8082` if 8081 is occupied. `FRONTEND_PORT`, `MAILPIT_UI_PORT` and `MAILPIT_SMTP_PORT` change the other host ports. If you change the frontend port, update `PUBLIC_BASE_URL` to match so reset links return to the correct application. Native Java development uses `SMTP_HOST=localhost` and the configured Mailpit host SMTP port; the container uses `mailpit:1025`.

Mailpit captures email locally and does not deliver messages to external addresses. No SMTP relay is configured. Its UI and API expose reset tokens for local testing, so keep the inbox local. Captured messages are ephemeral and limited to 200; PostgreSQL application data persists separately. Cookies intentionally omit `Secure` for the localhost HTTP workflow; sessions still use HttpOnly and SameSite protection. Cloud provisioning remains disabled.

AI defaults to `AI_PROVIDER=mock` with a blank `AI_API_KEY`, which makes no model API requests. Optional free local Ollama requires both `AI_PROVIDER=local` and `AI_API_KEY=local-only`; Compose defaults its URL to `http://host.docker.internal:11434`. You must install Ollama and a local model yourself. The backend rejects paid providers, remote hosts, cloud models and redirects. Blank keys always select mock. No transactions or account data are sent to the local model; its limited topic classification does not replace the curated financial explanations.

The example environment contains public local demo credentials: `Monelytics!2026` for the regular demo and `AdminDemo!2026` for the administrator. Seed passwords apply when accounts are first created; changing `.env` does not reset existing account passwords. Replace public credentials if others can access your installation. Never share `.env` or `.local-secrets`. `MFA_ENCRYPTION_KEY` must be a stable base64-encoded 32-byte key; changing it makes existing encrypted MFA secrets unreadable.

```sh
docker compose --env-file .env.example config --quiet
docker compose logs --follow backend
docker compose ps
docker compose stop # Keep PostgreSQL data
docker compose down # Remove containers, keep PostgreSQL data
```

The named `monelytics_postgres-data` volume stores PostgreSQL data. `docker compose down --volumes` permanently removes it and is only appropriate for an intentional demo reset. Changing `DATABASE_PASSWORD` after volume initialization does not change PostgreSQL's stored password; use an authorized password change or an intentional reset instead.

Health checks order database and Mailpit startup before the backend, and backend readiness before nginx. Flyway migrations run before readiness succeeds. The application images and Mailpit run as unprivileged users. PostgreSQL integration tests use disposable Testcontainers databases rather than the demo volume.

For Java and Angular running outside Docker, expose PostgreSQL on loopback explicitly:

```sh
docker compose -f docker-compose.yml -f docker-compose.dev.yml up database mailpit --wait
```

`DATABASE_PORT` controls the optional 5432 host port. Keep this override for local development. AWS architecture inspection is described in [AWS-DEPLOYMENT.md](AWS-DEPLOYMENT.md); synthesis is offline and provisioning commands fail under the zero-spend policy.
