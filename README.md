# Family Budget — Backend

REST API for Family Budget: private and family-shared accounts, categories, transactions
and monthly budgets. Spring Boot 4.1 / Java 25 modular monolith on PostgreSQL 18.

- Frontend: [`budget-front`](https://github.com/omarkelovUA/budget-front) (Angular 22)
- Design: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md); roadmap: `.docs/PLAN.md` in the
  workspace folder that holds both repos (not versioned)

## Quick start (Docker only)

Needs only Docker. Starts Postgres and the backend on <http://localhost:8080>:

```bash
docker compose --profile app up --build
```

Check it: <http://localhost:8080/actuator/health> → `{"status":"UP"}`. Then start the
frontend (see its README) and register a user in the UI.

Stop with `Ctrl+C`; `docker compose --profile app down` removes the containers but keeps
the database volume (`down -v` wipes it).

## Developing

Prerequisites: JDK 25+ and Docker (runnable without `sudo`).

```bash
./mvnw spring-boot:run
```

`spring-boot-docker-compose` starts only Postgres from `compose.yaml` (the `app` service
is behind a profile) and wires the datasource. Flyway applies
`src/main/resources/db/migration` on startup.

```bash
./mvnw test      # unit + integration tests (Testcontainers starts a throwaway Postgres)
./mvnw verify    # what CI runs
```

`ModularityTests` fails the build if a module reaches into another module's internals,
and writes module diagrams to `target/spring-modulith-docs`.

## Configuration

All optional for local use; the defaults work out of the box.

| Env var | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev-only value | HMAC key for access tokens (≥ 32 bytes). **Set it anywhere but your machine.** |
| `FRONTEND_URL` | `http://localhost:4200` | Where the Google login redirects back to. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Comma-separated origins allowed to call the API directly (not needed behind the frontend's dev proxy). |
| `REFRESH_COOKIE_SECURE` | `true` | `Secure` flag on the refresh cookie. Chrome accepts it on `http://localhost`; set `false` if your browser drops it. |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | placeholder | Enables "Login with Google". Without them everything else works. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | none | Set to `native` behind a reverse proxy, so Google's redirect URI uses the proxy's `https` scheme. |

### Google login (optional)

1. In Google Cloud Console create an OAuth client ID of type *Web application*.
2. Add the redirect URI `http://localhost:4200/login/oauth2/code/google` (the frontend's
   dev server proxies it here). Use `http://localhost:8080/login/oauth2/code/google`
   only if you open the backend directly.
3. Copy `.env.example` to `.env` and fill in `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
   `.env` is git-ignored, and `docker compose` reads it, so
   `docker compose --profile app up -d` picks the values up. When running with `./mvnw`,
   export them instead.

## Production

`deploy/` holds the production stack for one Docker host: `compose.yaml` (Caddy + SPA,
this API, Postgres), `.env.example`, `deploy.sh` and `backup.sh`. A push to `main` runs
the tests, pushes the image to GHCR and deploys it over SSH (`.github/workflows/ci.yml`).
The VM setup is in the workspace's `.docs/prod_deployment.md`; the topology is in
ARCHITECTURE.md §6.4.

## API overview

All under `/api/v1`, JSON, errors as RFC 9457 `ProblemDetail` with a stable `type` (see
ARCHITECTURE.md §5.4). Everything except `/auth/*` and `/actuator/health` needs
`Authorization: Bearer <access token>`.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; Google via `/oauth2/authorization/google` |
| User | `GET /users/me`, `GET`/`PUT /users/me/settings` |
| Family transfers | `GET`/`POST /family-transfers`, `POST /family-transfers/{id}/collect`, `POST /family-transfers/{id}/take-back` |
| Family | `POST /families`, `GET /families/current`, `POST`/`GET /families/current/invitations`, `DELETE /families/current/invitations/{id}`, `GET /invitations`, `POST /invitations/{id}/accept`, `POST /invitations/{id}/decline`, `POST /families/current/leave`, `PATCH`/`DELETE /families/current/members/{userId}` |
| Accounts | `GET`/`POST /accounts` (`?closed=true` for your closed ones), `GET`/`PUT`/`DELETE /accounts/{id}`, `POST /accounts/{id}/close` |
| Categories | `GET`/`POST /categories`, `PUT`/`DELETE /categories/{id}` |
| Transactions | `GET /transactions?page&size&accountId`, `GET /transactions/summary?period=YYYY-MM` (per-currency month totals), `POST /transactions`, `DELETE /transactions/{id}` |
| Budgets | `GET /budgets?period=YYYY-MM`, `POST /budgets`, `PUT`/`DELETE /budgets/{id}` |

## Layout

Package-by-feature (`com.familybudget`), one Spring Modulith module each: `common`,
`security`, `user`, `family`, `account`, `category`, `transaction`, `budget`. Only a
module's `*Service` and view records are public; repositories and entities are
package-private.
