# Local development — running and testing on this laptop

How to start the backend and frontend for development, use the app, and run the tests.
Written for this machine (Linux, repos in `~/Dev/budget/`); the general setup for anyone
else is in each repo's README.

```
~/Dev/budget/
  budget-app-backend/   Spring Boot API   → http://localhost:8080
  budget-app-front/     Angular SPA       → http://localhost:4200  (proxies /api to :8080)
```

## 1. What you need (already installed here)

| Tool | Needed | Check with |
|---|---|---|
| JDK | 25+ (Java 26 is installed — fine) | `java -version` |
| Docker | running, usable without `sudo` | `docker info` |
| Node.js | **22+** via nvm (22.23.3 is installed) | `nvm ls` |

> **Node gotcha on this laptop:** nvm's default here is **Node 16**, which Angular 22
> rejects ("The Angular CLI requires a minimum Node.js version…"). In every new terminal
> for the frontend run `nvm use` inside `budget-app-front` — it reads `.nvmrc` (22). To
> make 22 the default once and for all: `nvm alias default 22`.

## 2. One-time setup

```bash
cd ~/Dev/budget/budget-app-front
nvm use          # switches to Node 22 from .nvmrc
npm ci           # installs exact versions from package-lock.json
```

The backend needs no setup: the Maven wrapper (`./mvnw`) downloads Maven and all
dependencies on first run.

## 3. Start the backend (terminal 1)

```bash
cd ~/Dev/budget/budget-app-backend
./mvnw spring-boot:run
```

- Ready when the log shows `Started BudgetAppBackendApplication`.
- It starts **Postgres in Docker by itself** (from `compose.yaml`) and Flyway applies the
  migrations. When you stop the app (`Ctrl+C`) the Postgres container is stopped too.
- Data survives restarts (Docker volume `budget-app-backend_postgres-data`).
- Check: `curl localhost:8080/actuator/health` → `{"status":"UP",...}`.

From IntelliJ IDEA instead: run `BudgetAppBackendApplication` — same behaviour.
`TestBudgetAppBackendApplication` (in `src/test`) is an alternative that runs against a
throwaway Testcontainers Postgres instead — handy for a clean database every start.

**Alternative — backend fully in Docker** (no JDK/Maven involved, e.g. when only working
on the frontend):

```bash
cd ~/Dev/budget/budget-app-backend
docker compose --profile app up --build      # Ctrl+C to stop
```

Use one or the other, not both — they need the same ports (8080, 5432).

## 4. Start the frontend (terminal 2)

```bash
cd ~/Dev/budget/budget-app-front
nvm use
npm start
```

Open **<http://localhost:4200>**. Code changes reload the page automatically.

`npm start` proxies `/api`, `/oauth2` and `/login/oauth2` to the backend
(`src/proxy.conf.json`), so the browser only ever talks to `localhost:4200`.

> **If an older `ng serve` is still running on 4200** (one started before the proxy
> config existed was running earlier), stop it first — otherwise API calls return the
> app's `index.html` instead of data and nothing loads. Find it with
> `ss -ltnp | grep 4200`, then `Ctrl+C` it in its terminal or `kill <pid>`.
> Changes to `proxy.conf.json` also need an `ng serve` restart.

## 5. Try it out

1. **Register** at <http://localhost:4200/register> (password ≥ 8 characters).
2. You land on **Family** → create a family. Create accounts, categories, transactions
   and budgets; the **Dashboard** sums them up.
3. **Test sharing with a second user:** open a private/incognito window (or another
   browser profile — sessions live in a per-profile cookie) and register a second user.
   As the first user, click *Створити код запрошення* on the Family page; as the second
   user, paste the code into *Код запрошення* → *Приєднатися*. Now both see shared
   (`Сімейний`) accounts, categories, budgets and transactions, but not each other's
   private ones.
4. **Reload the page** — you stay logged in (session restored from the refresh cookie).
   *Вийти* logs out.

### Calling the API directly (optional)

```bash
# register and keep the access token
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"password123","displayName":"Me"}' \
  localhost:8080/api/v1/auth/register | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
# (already registered? use /api/v1/auth/login with email + password instead)

curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/users/me
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/accounts
```

Access tokens expire after 15 minutes. All endpoints are listed in the backend README.

## 6. Run the tests

**Backend** (Docker must be running — integration tests start their own throwaway
Postgres via Testcontainers; your dev database is not touched):

```bash
cd ~/Dev/budget/budget-app-backend
./mvnw test                                   # all tests (~1 min)
./mvnw test -Dtest=TransactionFlowTests       # one test class
./mvnw verify                                 # exactly what CI runs
```

`WARN … violates foreign key constraint` lines in the output are expected — they come
from tests that check deleting in-use data is refused. `ModularityTests` fails if one
module uses another's internals.

**Frontend:**

```bash
cd ~/Dev/budget/budget-app-front
nvm use
npx ng test --watch=false     # run all unit tests once (what CI runs)
npm test                      # watch mode: re-runs on every change
npx ng build                  # production build into dist/ (catches template/type errors)
```

## 7. Optional: Google login

Works only with your own Google OAuth client (everything else works without it):

1. Google Cloud Console → *APIs & Services* → *Credentials* → *Create OAuth client ID*,
   type *Web application*, redirect URI `http://localhost:4200/login/oauth2/code/google`.
2. Start the backend with the credentials:

   ```bash
   GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... ./mvnw spring-boot:run
   ```

3. On the login page click *Увійти через Google*.

On the OAuth consent screen (type *External*, scopes `openid email profile`), add every
Google account you'll sign in with as a **test user**, e.g. a second one for family
invitations; while the app is in *Testing*, Google blocks everyone else
(`access_denied`). `invalid_client` means the backend still has the placeholder client
id; `redirect_uri_mismatch` means the URI isn't registered exactly, with no trailing
slash.

## 8. Stopping and resetting

- Stop: `Ctrl+C` in both terminals (Postgres stops with the backend).
- **Wipe the dev database** (all users and data), with the backend stopped:

  ```bash
  cd ~/Dev/budget/budget-app-backend
  docker compose down -v
  ```

  The next `./mvnw spring-boot:run` starts with an empty database.

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| `The Angular CLI requires a minimum Node.js version…` | `nvm use` in `budget-app-front` (or `nvm alias default 22`). |
| Frontend loads but every list says *Не вдалося завантажити дані* | Backend not running, or an old `ng serve` without the proxy is on 4200 — see §4. Check `curl localhost:4200/api/v1/users/me` returns `401` JSON, not HTML. |
| `Port 8080 / 5432 / 4200 is already in use` | Something else holds it: `ss -ltnp \| grep -E ':8080\|:5432\|:4200'`. Often the Docker variant (§3) and `spring-boot:run` at the same time, or a local Postgres on 5432. |
| Logged out after every page reload | The browser dropped the `Secure` refresh cookie on plain http. Chrome accepts it on localhost; for other browsers start the backend with `REFRESH_COOKIE_SECURE=false ./mvnw spring-boot:run`. |
| Backend fails at startup with a Docker error | Docker isn't running or needs `sudo`: `docker info` must work as your user. |
| Backend tests fail with `Could not find a valid Docker environment` | Same — Testcontainers needs a working Docker. |
| Error `Schema validation: missing table …` after pulling new code | Shouldn't happen (Flyway migrates on start). If an old migration was edited, wipe the dev database (§8). |
