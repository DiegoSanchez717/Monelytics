# Local containers

Install Docker Desktop (Linux containers) or Docker Engine with Compose v2. Copy `.env.example` to `.env`, then use:

```sh
docker compose up --build --wait
```

Open `http://localhost:8080`. The Angular nginx container forwards `/api` to Spring Boot; the database has no host port. Spring Boot is also bound to `127.0.0.1:8081` for local health/API documentation and debugging. Cookies are deliberately not marked `Secure` in this HTTP-only local configuration. AWS deployment marks them `Secure` and disables demo seeding.

If port 8081 is already in use, set `BACKEND_PORT=8082` in `.env`. Likewise, `FRONTEND_PORT` changes the local website port without changing the internal container ports.

The example environment contains public local demo credentials and a demonstration encryption key. Replace passwords and generate your own stable key if other people can access your installation. Never reuse demo credentials or this key on a public instance. `MFA_ENCRYPTION_KEY` must be a base64-encoded 32-byte key. Changing the key makes already encrypted MFA secrets unreadable. Seed passwords are applied when demo accounts are first created, so changing `.env` does not reset existing user passwords.

```sh
docker compose --env-file .env.example config --quiet # Validate configuration
docker compose logs --follow backend
docker compose ps
docker compose stop                                # Keep PostgreSQL data
docker compose down                                # Remove containers, keep data
```

PostgreSQL data is stored in the named `wealthpath_postgres-data` volume. `docker compose down --volumes` permanently removes local data and should only be used when you intend to reset the demo. Changing `DATABASE_PASSWORD` after initializing this volume does not change PostgreSQL's stored password; use an authorized PostgreSQL password change or reset the demonstration volume.

The application and database start with health checks and dependency ordering. Java migrations run before readiness succeeds. Frontend and backend production Dockerfiles use unprivileged users; both applications listen on 8080. Run the repository's tests independently of a running demo installation; PostgreSQL integration tests start disposable Testcontainers databases.

For a Java/Angular development process running outside containers, explicitly expose PostgreSQL on loopback:

```sh
docker compose -f docker-compose.yml -f docker-compose.dev.yml up database --wait
```

Set `DATABASE_PORT` in `.env` to change the default 5432 host port. The primary configuration keeps database access inside Docker.
