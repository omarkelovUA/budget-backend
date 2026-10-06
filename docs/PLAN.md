# Family Budget — Plan

Status: draft v0.2 · 2026-10-06 (backend: M6 done, M7 done pending a manual Google login check)
Companion doc: [ARCHITECTURE.md](./ARCHITECTURE.md) (system design this plan builds toward)

## 1. What we're building

A full-stack family budgeting app: private + family-shared accounts, categories,
transactions and budgets. Frontend: Angular 22 (signals, signal forms). Backend: Spring
Boot (JWT + OAuth2 security). Database: PostgreSQL.

## 2. Approach

Frontend-first, as the user requested:

1. Build/extend the Angular UI against **mock, in-memory services** (same style as
   today's `AccountService`) so screens and UX can be iterated on without waiting on the
   backend.
2. Once the UI for a domain feels right, build the matching backend module.
3. Swap that domain's mock service for a real `HttpClient`-backed implementation behind
   the same contract (see [ARCHITECTURE.md §4.3](./ARCHITECTURE.md#43-mock-first-data-layer-the-dumb-hardcoded-service-strategy)).
4. Repeat per domain rather than waiting for the entire backend before wiring anything up
   — keeps each phase shippable/demoable.

Skills and repo rules (CLAUDE.md, lint rules, CI config, etc.) are **explicitly deferred**
per the user's request — added once the architecture settles, not now.

## 3. Current state (as of this plan)

- Repo `budget-app-front`, branch `dev`, already pushed to GitHub.
- Angular 21.2.7 project, standalone components, Vitest for tests.
- Existing code: `IAccount`/`ICategory`/`Transaction` types, a hardcoded `AccountService`
  (in-memory array + `rxjs delay()`), `AccountForm` (reactive forms) and `AccountList`
  (`rxResource`) components. No routes wired yet (`app.routes.ts` is empty), no auth, no
  family concept, no categories/transactions/budget UI yet.
- No backend repo exists yet.

## 4. Angular 21 → 22: upgrade, don't recreate

Recommendation: **upgrade the existing project in place**, not delete-and-recreate.

Why: the existing `AccountService`/`AccountForm`/`AccountList` are small, already follow
the target conventions (standalone, signals, `rxResource`), and are a reasonable first
slice of the `accounts` feature. A 21→22 bump is a routine Angular CLI migration
(`ng update`), not a rewrite trigger. Recreating would throw away working code for no
architectural benefit — the only thing actually new in v22 for this project is signal
forms, which is additive, not a breaking reason to restart.

Deferred until the dedicated migration phase (M1): run `ng update @angular/core@22
@angular/cli@22`, confirm build/tests green, *then* start adding features — so any
breakage is isolated to one commit and easy to bisect.

## 5. Roadmap

Each milestone is independently demoable. "Exit criteria" = what has to be true to call
it done.

### M0 — Architecture & planning (this step)
- Deliverables: `docs/ARCHITECTURE.md`, `docs/PLAN.md` (this file).
- Exit criteria: user has reviewed and agreed with the shape before code changes start.

### M1 — Angular 22 upgrade
- `ng update` to Angular 22 (core, cli, forms, etc.), fix any breakage.
- No new features in this phase — keep it a clean, revertible commit.
- Exit criteria: `npm run build` and `npm test -- --watch=false` green on Angular 22.

### M2 — Frontend foundation restructure
- Introduce `core/`, `shared/`, `features/` folders per
  [ARCHITECTURE.md §4.2](./ARCHITECTURE.md#42-folder-structure-target).
- Move existing `account-*` components/service into `features/accounts/`, introduce the
  `AccountRepository` contract + `MockAccountRepository` (wrapping today's logic almost
  unchanged) wired via provider.
- Add real routing (today's `app.routes.ts` is empty) for a basic shell (dashboard +
  accounts).
- Exit criteria: existing account CRUD still works, now routed, behind the repository
  contract.

### M3 — Domain expansion: categories, transactions, budgets (mocked)
- Extend `model/types.ts` with `ownerType`/`ownerId` (or a simpler client-side
  `visibility` field — pick one, see Open decisions) and the `Budget` type.
- Build `features/categories`, `features/transactions`, `features/budgets` with mock
  repositories, following the same contract pattern as accounts.
- Mock data includes private + shared examples, multiple currencies, an over-budget case
  — so UI edge cases surface now, not during backend integration.
- Exit criteria: a user can, against mock data, create/view accounts, categories,
  transactions and a monthly budget, with private vs. shared visually distinguished.

### M4 — Signal forms migration
- Migrate `AccountForm` (and new M3 forms) from `ReactiveFormsModule` to
  `@angular/forms/signals`.
- Exit criteria: no `ReactiveFormsModule` usage left in feature forms; behavior parity
  confirmed via existing/updated specs.

### M5 — Mock auth & family UI
- `core/auth` signal-based auth state, mocked login/register/OAuth2-button (no real
  backend yet), route guards.
- Family creation/invite/member-list UI against a mock `FamilyRepository`.
- Exit criteria: full click-through demo of the app's shape (auth → family → private vs.
  shared accounts/transactions/budgets) with zero backend, reviewable by the user.

### M6 — Backend skeleton
- New repo `budget-app-backend` (see [ARCHITECTURE.md §8](./ARCHITECTURE.md#8-key-decisions--why)
  for the two-repo rationale): Spring Boot project, Postgres via Docker Compose, Flyway
  baseline migration, `common`/`security` scaffolding (no business logic yet).
- Exit criteria: app boots, connects to Postgres, one health-check endpoint responds.
- **Status: done (2026-10-06).** Verified against the Docker Compose Postgres (Flyway
  applied, `/actuator/health` → `UP`, `/api/v1/*` → 401 without a token) and in
  Testcontainers tests. V2 adds the Spring Modulith event-publication tables Hibernate
  validates against.

### M7 — Backend: user, family, auth
- `user` + `family` modules (entities, migrations, services, controllers).
- JWT issuance/validation + password login; OAuth2 (Google) login wired to the same JWT
  issuance path (per [ARCHITECTURE.md §6.1](./ARCHITECTURE.md#61-two-login-paths-one-session-model)).
- Exit criteria: register, password-login, Google-login and family invite/join all work
  end-to-end via API tests (Postman/HTTP files or integration tests) — frontend not
  wired yet.
- **Status: implemented (2026-10-06).** Integration tests (Testcontainers) cover register,
  password login, refresh rotation + reuse detection, logout, Google login (success
  handler driven with a mocked OIDC user, PKCE redirect checked) and family
  create/invite/join/leave/remove/role change.
  - Remaining for exit: one real Google sign-in. Needs an OAuth client in Google Cloud
    Console (redirect URI `http://localhost:8080/login/oauth2/code/google`) and
    `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` set when starting the app.
  - Decisions taken: invite codes rather than email invites; opaque hashed refresh
    tokens; no `activeFamilyId` column (membership row is the source of truth);
    no MapStruct yet; `/api/v1` written into mappings. Details in ARCHITECTURE.md
    §3.2, §5, §6.2.

### M8 — Backend: account/category/transaction/budget
- Remaining modules, authorization scoping (private vs. family) enforced per
  [ARCHITECTURE.md §6.3](./ARCHITECTURE.md#63-authorization-private-vs-shared-data).
- Integration tests with Testcontainers covering the private/shared visibility rule —
  this is the rule most likely to regress silently, so it gets explicit test coverage.
- Exit criteria: full API surface for all domains, tested.

### M9 — Frontend ↔ backend integration
- Swap each domain's mock repository for its `Http*Repository` implementation, one
  domain at a time (accounts → categories → transactions → budgets → auth/family last,
  since it's the one everything else depends on being correct).
- Real auth: token-in-memory + httpOnly refresh cookie, per
  [ARCHITECTURE.md §6.2](./ARCHITECTURE.md#62-token-handling).
- Exit criteria: mock repositories deleted or clearly marked dev-only; app runs fully
  against the real backend.

### M10 — Hardening pass
- Error states, loading states, empty states, responsive layout, basic a11y pass.
- CI (build+test on both repos), Dockerized backend, a documented local-dev
  `docker-compose up` path.
- Exit criteria: a stranger can clone both repos and get a working local instance from
  the READMEs alone.

## 6. Deferred (by explicit request)

Not part of this plan yet — added once the architecture above is confirmed and some
real code exists to ground them in:

- Project-level CLAUDE.md / AGENTS.md rules beyond what already exists in `.junie/AGENTS.md`.
- Claude Code skills tailored to this project.
- Lint/formatting rule changes beyond current `.prettierrc`/`.editorconfig`.
- CI pipeline definitions.

## 7. Open decisions

Things that need the user's call before (or during) the milestone where they matter —
flagging now so they're not a surprise mid-phase.

| # | Decision | Needed by | Leaning |
|---|---|---|---|
| 1 | ~~Second repo name/location for the backend, and whether these two docs move there too.~~ **Decided:** GitHub `omarkelovUA/budget-backend` (local folder `budget-app-backend`); the docs live in the backend repo. | M6 | Done. |
| 2 | `ownerType/ownerId` on the wire vs. a simpler derived `visibility: 'private'\|'shared'` sent to the frontend (backend still models ownerType/ownerId internally either way). | M3 | Lean simple (`visibility`) on the frontend DTOs; keep the richer model server-side only. |
| 3 | OAuth2 providers beyond Google for v1 (e.g. add Microsoft/Apple) or Google-only to start. | M7 | M7 implemented Google only, per the user's original ask. Adding another provider later is additive (another `oauth2Login` registration + find-or-create path). |
| 4 | Hosting/deployment target (self-hosted VM, Fly.io/Railway, cloud provider) — affects M10 only, not the architecture itself. | M10 | No leaning yet — not urgent. |

## 8. Next step

Once this plan + architecture are confirmed, start **M1 (Angular 22 upgrade)** as the
first concrete code change.
