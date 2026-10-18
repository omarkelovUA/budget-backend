# Family Budget — Backend

REST API for Family Budget: private and family-shared accounts, categories, transactions
and monthly budgets. Spring Boot 4.1 / Java 25 modular monolith on PostgreSQL 18.

- Frontend: [`budget-front`](https://github.com/omarkelovUA/budget-front) (Angular 22)
- Design: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

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
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | placeholder | Needed to sign in at all: Google is the only login. The tests don't need them. |
| `GOOGLE_PLAY_PACKAGE_NAME` | `app.familybudget` | The Android app whose subscriptions count. |
| `GOOGLE_PLAY_CREDENTIALS` | empty | Path to the Play service account's JSON key. Empty: purchases can't be checked (`503 google-play-unavailable`); manual grants still work. |
| `GOOGLE_PLAY_PUSH_AUDIENCE` / `GOOGLE_PLAY_PUSH_SERVICE_ACCOUNT` | placeholder | The OIDC audience and service account of the Pub/Sub push subscription that delivers Google Play's notifications. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | none | Set to `native` behind a reverse proxy, so Google's redirect URI uses the proxy's `https` scheme. |

### Google login (required)

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
The topology is in ARCHITECTURE.md §6.4.

### Setting up the VM (once)
1. **VM:** Azure Ubuntu 24.04, `Standard_B2s` (2 GB is too tight), SSH keys only, a
   static public IP. Open 22, 80, 443 (optionally 443/udp); keep 5432 and 8080 closed.
   ```bash
   az group create -n familybudget-rg -l westeurope
   az vm create -g familybudget-rg -n familybudget-vm --image Ubuntu2404 --size Standard_B2s \
     --admin-username azureuser --ssh-key-values ~/.ssh/id_ed25519.pub \
     --public-ip-sku Standard --public-ip-address-dns-name familybudget
   az vm open-port -g familybudget-rg -n familybudget-vm --port 80,443 --priority 1010
   ```
2. **DNS:** an `A` record for `<domain>` → the VM's IP (or the free
   `familybudget.<region>.cloudapp.azure.com`). `dig +short <domain>` must return the IP
   before Caddy's first start.
3. **Server:** `apt full-upgrade`, `unattended-upgrades`, `fail2ban`; Docker from
   `curl -fsSL https://get.docker.com | sudo sh`; `/etc/docker/daemon.json` =
   `{ "log-driver": "local" }`. A `deploy` user in the `docker` group with its own SSH key
   owning `/opt/familybudget/backups`. As `deploy`, `docker login ghcr.io` with a classic
   PAT that has only `read:packages`.
4. **Secrets:** copy `deploy/*` and `deploy/.env.example` to `/opt/familybudget`, then
   `.env.example` → `.env` (`chmod 600`). `POSTGRES_PASSWORD` from
   `openssl rand -base64 32`, `JWT_SECRET` from `openssl rand -base64 48`. Changing the
   DB password later needs `ALTER USER` too.
5. **Google:** in the OAuth client add the origin `https://<domain>` and the redirect URI
   `https://<domain>/login/oauth2/code/google`; add `<domain>` to the consent screen's
   authorized domains and publish the app (in *Testing* only test users can sign in).
6. **GitHub (both repos):** environment `production`, secrets `DEPLOY_SSH_KEY` and
   `DEPLOY_KNOWN_HOSTS` (`ssh-keyscan -t ed25519 <domain>`), variable `DOMAIN`.
7. **First deploy:** push backend `main` (its smoke test fails once, since `web` doesn't
   exist yet), then frontend `main`, then re-run the backend's `deploy` job. Check:
   `http://` redirects to `https://`, `/actuator/health` is `UP`, a deep link reloads,
   Google sign-in works, the `refresh_token` cookie is `Secure; HttpOnly; SameSite=Strict`.
8. **Backups:** as `deploy`, cron
   `15 3 * * * bash /opt/familybudget/backup.sh >> /opt/familybudget/backups/backup.log 2>&1`,
   and set `BACKUP_CONTAINER_URL` (an Azure Blob container SAS with create + write) so
   dumps leave the VM. Test a restore once into a scratch DB with `pg_restore`. After a
   real restore, bump `sync_generation` so the phones download everything again.

Releases: backend `main` first, wait for its deploy, then frontend `main`
(`git checkout main && git merge --ff-only dev && git push && git checkout dev`).
Rollback: `bash /opt/familybudget/deploy.sh api <previous-sha>`; across a Flyway
migration it needs a restore. Logs: `docker compose logs -f --tail=200 api`.

| Symptom | Fix |
|---|---|
| Google `redirect_uri_mismatch` with `http://...` | `SERVER_FORWARD_HEADERS_STRATEGY=native` is missing on `api` |
| Google `redirect_uri_mismatch` with `https://...` | That exact URI isn't in the OAuth client (trailing slash, `www`) |
| Logged out on every reload | Not on HTTPS, or the SPA is opened from another origin |
| Caddy `challenge failed` | DNS or ports 80/443 not ready; fix, then `docker compose restart web` (don't loop: rate limits) |
| `denied` pulling images | The VM's `docker login ghcr.io` token is missing or expired |

## API overview

All under `/api/v1`, JSON, errors as RFC 9457 `ProblemDetail` with a stable `type` (see
ARCHITECTURE.md §5.4). Everything except `/auth/*`, `/webhooks/google-play` (Google's
own token) and `/actuator/health` needs `Authorization: Bearer <access token>`.

| Area | Endpoints |
|---|---|
| Auth | Google only: `/oauth2/authorization/google` (web), `POST /auth/google/mobile` (app); `POST /auth/refresh`, `/auth/logout`, `/auth/mobile/refresh`, `/auth/mobile/logout` |
| User | `GET /users/me`, `GET`/`PUT /users/me/settings` |
| Subscriptions | `GET /subscriptions/me`, `POST /subscriptions/google-play`; `POST /webhooks/google-play` (Pub/Sub) |
| Family transfers | `GET`/`POST /family-transfers`, `POST /family-transfers/{id}/collect`, `POST /family-transfers/{id}/take-back` |
| Family | `POST /families`, `GET /families/current`, `POST`/`GET /families/current/invitations`, `DELETE /families/current/invitations/{id}`, `GET /invitations`, `POST /invitations/{id}/accept`, `POST /invitations/{id}/decline`, `POST /families/current/leave`, `PATCH`/`DELETE /families/current/members/{userId}` |
| Accounts | `GET`/`POST /accounts` (`?closed=true` for your closed ones), `GET`/`PUT`/`DELETE /accounts/{id}`, `POST /accounts/{id}/close` |
| Categories | `GET`/`POST /categories`, `PUT`/`DELETE /categories/{id}` |
| Transactions | `GET /transactions?page&size&accountId`, `GET /transactions/summary?period=YYYY-MM` (per-currency month totals), `POST /transactions`, `DELETE /transactions/{id}` |
| Budgets | `GET /budgets?period=YYYY-MM`, `POST /budgets`, `PUT`/`DELETE /budgets/{id}` |

## Layout

Package-by-feature (`com.familybudget`), one Spring Modulith module each: `common`,
`security`, `user`, `family`, `account`, `category`, `transaction`, `budget`. Inside a
module, classes sit in layer packages: `controller`, `service`, `repository`, `entity`,
`model`, `event`. Only `service`, `model` and `event` are visible to other modules.
