# Family Budget — Architecture

Status: draft v0.2 · 2026-10-06
Companion doc: [PLAN.md](./PLAN.md) (roadmap, phases, open decisions)

## 1. Purpose

Family Budget lets a household track money together while each member keeps some of it
private. A user can:

- manage **private** accounts, categories, transactions and budgets visible only to them,
- manage **shared** (family-wide) accounts, categories, transactions and budgets visible to
  every member of their family,
- belong to exactly one family at a time (v1 simplification, see §7).

This document describes the target system shape. It is intentionally stack-accurate but
implementation-light — concrete code arrives phase by phase per [PLAN.md](./PLAN.md).

## 2. High-level system

```mermaid
flowchart LR
    subgraph Client["Browser"]
        FE["Angular 22 SPA\n(signals, signal forms)"]
    end

    subgraph Server["Spring Boot API"]
        SEC["Security filter chain\n(JWT + OAuth2 login)"]
        API["REST controllers (/api/v1)"]
        SVC["Service layer"]
        REPO["Spring Data JPA repositories"]
    end

    DB[("PostgreSQL")]
    IDP["OAuth2 provider\n(Google)"]

    FE -- "HTTPS / JSON\nBearer JWT" --> SEC --> API --> SVC --> REPO --> DB
    FE -. "OAuth2 Authorization Code\n+ PKCE (redirect)" .-> IDP
    IDP -. "id_token / code" .-> SEC
```

- **Frontend** is a single Angular SPA. No server-side rendering requirement for v1.
- **Backend** is a single Spring Boot modular-monolith (not microservices — scope doesn't
  justify the operational cost yet; see §8 for the tradeoff).
- **Auth** has two entry points that both resolve to the same backend-issued JWT session:
  password login (JWT) and OAuth2/OIDC login (Google first). See §6.
- **Database** is one PostgreSQL instance; one schema, Flyway-versioned.

## 3. Domain model

### 3.1 Entities

```mermaid
erDiagram
    USER ||--o{ FAMILY_MEMBERSHIP : has
    FAMILY ||--o{ FAMILY_MEMBERSHIP : has
    USER ||--o{ ACCOUNT : "owns (private)"
    FAMILY ||--o{ ACCOUNT : "owns (shared)"
    USER ||--o{ CATEGORY : "owns (private, optional)"
    FAMILY ||--o{ CATEGORY : "owns (shared, optional)"
    ACCOUNT ||--o{ TRANSACTION : "source/destination"
    CATEGORY ||--o{ TRANSACTION : classifies
    USER ||--o{ BUDGET : "owns (private)"
    FAMILY ||--o{ BUDGET : "owns (shared)"
    CATEGORY ||--o{ BUDGET : limits
    USER ||--o{ TRANSACTION : "recorded by"

    USER {
        uuid id
        string email
        string displayName
        string passwordHash "null if OAuth-only"
        uuid activeFamilyId "nullable"
    }
    FAMILY {
        uuid id
        string name
        uuid createdBy
    }
    FAMILY_MEMBERSHIP {
        uuid userId
        uuid familyId
        enum role "ADMIN | MEMBER"
        timestamp joinedAt
    }
    ACCOUNT {
        uuid id
        enum ownerType "USER | FAMILY"
        uuid ownerId
        string name
        decimal balance
        string currency
        string icon
        string color
    }
    CATEGORY {
        uuid id
        enum ownerType "USER | FAMILY | SYSTEM"
        uuid ownerId "null for SYSTEM"
        enum type "INCOME | EXPENSE"
        string name
        string icon
        string color
    }
    TRANSACTION {
        uuid id
        enum kind "PAYMENT | TRANSFER"
        decimal amount
        date occurredOn
        uuid recordedByUserId
        uuid accountId "payment"
        uuid categoryId "payment"
        uuid fromAccountId "transfer"
        uuid toAccountId "transfer"
        string note
    }
    BUDGET {
        uuid id
        enum ownerType "USER | FAMILY"
        uuid ownerId
        uuid categoryId
        string period "YYYY-MM"
        decimal plannedAmount
        decimal factAmount "cached, recomputed from transactions"
    }
```

### 3.2 Ownership / sharing rule

Every shareable aggregate (`Account`, `Category`, `Budget`) carries `ownerType` +
`ownerId` instead of a boolean "shared" flag:

- `ownerType = USER, ownerId = <userId>` → private, visible only to that user.
- `ownerType = FAMILY, ownerId = <familyId>` → shared, visible to every member of that
  family.

This single polymorphic-owner pattern avoids duplicating each table into
`*_private` / `*_shared` variants and keeps authorization logic in one place
(see §6.3). `Transaction` has no owner of its own — its visibility is derived from the
account(s) it touches.

A user has exactly one `activeFamilyId` in v1. Creating/joining a family is a deliberate
action (invite code or email invite); users who don't belong to one only ever see private
data. Multi-family-per-user is listed as a v2 idea, not built now (§7).

### 3.3 Relationship to current frontend types

`src/app/model/types.ts` already has `IAccount`, `ICategory`, `Transaction`. They map
almost directly onto §3.1 but currently have no owner/visibility field. Phase F2 in the
plan extends them with `ownerType`/`ownerId` (or a simpler `visibility: 'private' | 'shared'`
derived client-side) before the sharing UI is built — see
[PLAN.md](./PLAN.md).

## 4. Frontend architecture (Angular)

### 4.1 Stack

- Angular 22, standalone components only (no NgModules).
- **Signals** for all local/component state; `signal()`, `computed()`, `effect()`,
  `linkedSignal()` where appropriate.
- **Signal Forms** (`@angular/forms/signals`) replacing the current `ReactiveFormsModule`
  usage in `account-form.ts` once the project is on v22 — tracked as its own phase so the
  migration doesn't get entangled with new features.
- `rxResource` (already in use in `account-list.ts`) for request-backed state; plain
  `resource()` where a request is driven by a signal instead of an `Observable`.
- No NgRx / global store library. Domain state lives in per-domain **signal-based
  services** (`providedIn: 'root'`), each exposing readonly signals + methods — the same
  shape a lightweight SignalStore would have, without the extra dependency. Revisit only
  if cross-feature state coordination actually gets painful.

### 4.2 Folder structure (target)

```
src/app/
  core/                     # app-wide singletons, no UI
    auth/                   # auth state service, login/refresh flows, route guards
    http/                   # interceptors (auth header, error mapping, retry)
    config/                 # environment/config access
  shared/                   # dumb, reusable, no domain knowledge
    ui/                     # buttons, icon-picker, color-picker, modal, form controls
    pipes/ directives/
  model/                    # shared TypeScript interfaces/types (today: types.ts)
  features/
    accounts/
      data/                 # AccountRepository interface + mock & http implementations
      account-list/ account-form/ ...
    categories/
    transactions/
    budgets/
    family/                 # family creation, invites, member list, role management
    dashboard/              # aggregated overview (balances, month summary)
  app.routes.ts             # lazy-loaded per feature
```

Today's `services/account-service.ts` and `components/account-list|account-form/*` map
onto `features/accounts/*` — a rename/move, not a rewrite, happens when that feature
area is revisited (see PLAN M2/M6).

### 4.3 Mock-first data layer (the "dumb hardcoded service" strategy)

Goal: build and visually/behaviorally validate every screen before the backend exists,
then swap in real HTTP without touching components.

```mermaid
flowchart LR
    Component --> Repo["XRepository (abstract class / injection token)"]
    Repo -. "provided today" .-> Mock["MockXRepository\n(in-memory array + rxjs delay)"]
    Repo -. "provided from Phase F6 onward" .-> Http["HttpXRepository\n(HttpClient calls to Spring API)"]
```

- Each domain defines a repository **contract** (abstract class, so it's a valid DI
  token) returning `Observable<T>` — matching today's `AccountService` method shapes
  (`getAccounts/create/update/delete`) so the contract barely has to change.
- Two implementations: `Mock*Repository` (current style: in-memory arrays + `delay()` to
  simulate latency, including simulated validation errors) and, later, `Http*Repository`.
- Components/pages inject the **abstract contract**, never a concrete implementation —
  swapping is a one-line change in `app.config.ts` providers (or per-environment
  provider), not a component rewrite. This is the one structural rule worth adopting now,
  before more features are built on top of today's concrete `AccountService`.
- Mock data should intentionally include edge cases (zero balances, multiple currencies,
  a shared + a private account, an over-budget category) so UI states aren't discovered
  late.

### 4.4 Auth on the frontend (mocked until backend exists)

- `core/auth` exposes a signal-based `AuthStateService` (current user, tokens, family
  membership) behind the same mock-then-real swap as §4.3.
- Route guards (`canActivate` functional guards) check auth state signal; family-scoped
  routes also check `activeFamilyId`.
- Real implementation later: store access token in memory (not localStorage) + refresh
  token flow via httpOnly cookie, see §6.2.

## 5. Backend architecture (Spring Boot)

### 5.1 Stack

- Spring Boot 4.1.x (Spring Framework 7, Spring Security 7, Hibernate 7 / JPA 3.2,
  Jakarta EE 11), Java 25 (LTS). Maven.
- Spring Web MVC (REST), Spring Security (OAuth2 Resource Server for JWT validation,
  OAuth2 Client for Google login), Spring Data JPA, Flyway, Bean Validation, Actuator.
- Spring Modulith 2.x to verify module boundaries in tests (§5.2).
- PostgreSQL 18 (Docker Compose for local dev, auto-started via
  `spring-boot-docker-compose`).
- MapStruct for entity↔DTO mapping (avoids hand-written boilerplate mappers) — added with
  the first DTOs in M7.
- Jackson 3 (`tools.jackson.*`) is Boot 4's default; any custom (de)serializers target it.
- JSpecify `@Nullable`/`@NonNull` for null-safety in our own code, matching Spring 7.
- Testing: JUnit 5 + Testcontainers 2.x (real Postgres in integration tests, not H2).
- Boot 4 split auto-configuration into finer-grained modules: each technology needs its
  own starter (e.g. `spring-boot-starter-flyway`, not just `flyway-core`), and several
  classes moved packages (e.g. `EndpointRequest` →
  `org.springframework.boot.security.autoconfigure.actuate.web.servlet`) — older Boot 3
  examples won't compile as-is.

### 5.2 Module / package layout

Package-by-feature, not package-by-layer, to keep each domain's controller/service/repo/
entity/dto together:

```
com.familybudget
  common/            # ProblemDetail error handling, pagination, auditing base entity
  security/          # JWT filter, OAuth2 success handler, JWT issuing/validation, roles
  user/              # User entity, registration, profile
  family/            # Family, FamilyMembership, invites
  account/
  category/
  transaction/
  budget/
```

Each feature package: `*Controller`, `*Service`, `*Repository`, `*Entity`, `dto/`,
`*Mapper`. Cross-feature calls go through the other feature's `*Service`, never its
repository — keeps future extraction into separate services possible without a rewrite.

Each top-level package is a Spring Modulith application module. `ModularityTests` runs
`ApplicationModules.verify()` so a module reaching into another's internals (e.g. its
repository or entity) fails the build instead of relying on review discipline. `common`
is declared an *open* module since every feature uses it.

### 5.3 API conventions

- Base path `/api/v1`, resource-oriented REST (`GET/POST/PATCH/DELETE /accounts`, etc.).
  Spring Framework 7's built-in API versioning (path-segment strategy) can back the `v1`
  segment instead of hard-coding it into every `@RequestMapping` — decide when the first
  controller lands (M7); the URL shape is the same either way.
- All list endpoints return only what the caller is authorized to see (own private data
  ∪ their active family's shared data) — enforced in the service layer, not trusted from
  query params.
- Errors as RFC 7807 `ProblemDetail` with a stable `type`/`code` field the frontend can
  switch on (not just an HTTP status).
- Pagination via `page`/`size` query params + `Page<T>` response wrapper for
  transaction lists.

### 5.4 Error handling

A single `@RestControllerAdvice` translates domain exceptions (`NotFoundException`,
`ForbiddenException`, `ValidationException`, Spring Security exceptions) into consistent
`ProblemDetail` responses. Frontend's HTTP error interceptor (§4.4) depends on this shape
being stable from day one.

## 6. Security design

### 6.1 Two login paths, one session model

```mermaid
sequenceDiagram
    participant FE as Angular SPA
    participant API as Spring Boot
    participant IDP as Google OAuth2

    alt Password login
        FE->>API: POST /api/v1/auth/login {email, password}
        API->>API: verify password, issue access+refresh JWT
        API-->>FE: access token (body) + refresh token (httpOnly cookie)
    else OAuth2 login (server-side, Spring oauth2Login)
        FE->>API: navigate to /oauth2/authorization/google
        API->>IDP: redirect, Authorization Code + PKCE
        IDP->>API: redirect back to /login/oauth2/code/google?code=...
        API->>IDP: exchange code for id_token (confidential client)
        API->>API: find-or-create User from verified id_token claims
        API->>API: set refresh token (httpOnly cookie), same as password path
        API-->>FE: redirect back to the SPA
        FE->>API: POST /api/v1/auth/refresh
        API-->>FE: access token (body)
    end

    FE->>API: subsequent requests with "Authorization: Bearer <access>"
    API->>API: resource-server JWT support validates signature+expiry, loads auth context
```

Both paths converge on the same backend-issued JWT — the rest of the API never has to
know whether a session started via password or Google. This is the key simplification:
OAuth2 is a *login method*, not a parallel authorization system.

The Google flow runs entirely on the backend (Spring's `oauth2Login`, backend as a
confidential client): the SPA never sees the authorization code or Google's tokens. After
login the backend only sets the refresh cookie and redirects to the SPA, which then
obtains its access token through the normal `/auth/refresh` call — so the access token
never travels in a URL.

No hand-written JWT filter: tokens are issued with Spring Security's `NimbusJwtEncoder`
and validated by its OAuth2 Resource Server support (`NimbusJwtDecoder`), signed with an
HMAC key from configuration (`JWT_SECRET`). Switching to an asymmetric key pair later
only changes those two beans.

### 6.2 Token handling

- Short-lived **access token** (JWT, ~15 min), returned in the response body, held in
  frontend memory only (never localStorage, to limit XSS blast radius).
- Longer-lived **refresh token**, httpOnly + Secure + SameSite=Strict cookie, used only
  against a `/auth/refresh` endpoint; rotated on every use.
- Logout clears the refresh cookie server-side (denylist or short-lived token family
  invalidation — exact mechanism decided in the backend security phase, not blocking the
  architecture now).

### 6.3 Authorization (private vs. shared data)

- Every request carries the authenticated `userId` (from JWT) resolved server-side to
  their current `activeFamilyId`.
- Repository queries for `Account`/`Category`/`Budget`/`Transaction` are always scoped to
  `ownerType=USER AND ownerId=:userId OR ownerType=FAMILY AND ownerId=:activeFamilyId` —
  centralized in the service layer (§5.2) so no controller can accidentally leak
  cross-family data.
- Mutating a `FAMILY`-owned resource additionally checks the caller is a member (any
  role) of that family; family-management actions (invite, remove member, change role)
  require `ADMIN`.

### 6.4 Transport/infra

- HTTPS everywhere (even local dev via mkcert, to catch cookie/`Secure` issues early).
- CORS restricted to the known frontend origin(s) per environment.

## 7. Explicit v1 simplifications (revisit later, not now)

- One active family per user (no multi-family switching).
- Single currency assumed per account; no cross-currency conversion/FX.
- Budgets are monthly (`period = YYYY-MM`); no custom date ranges yet.
- No recurring-transaction scheduler.
- `Budget.factAmount` is a cached/recomputed value, not transactionally maintained on
  every write — acceptable staleness window, revisit if it causes visible lag.

## 8. Key decisions & why

| Decision | Choice | Why |
|---|---|---|
| Repos | Two repos (`budget-app-front` existing, new `budget-app-backend`) | Different stacks/lifecycles/CI; a monorepo buys nothing here since there's no shared build tooling between Angular and Spring Boot. Keep this doc + PLAN.md in the frontend repo for now (it exists today); mirror or move to the backend repo once that exists — whichever the user prefers at that point. |
| Backend topology | Modular monolith | Family Budget's scope (a handful of bounded contexts, one team) doesn't justify microservice operational cost. Package-by-feature keeps a future split possible. |
| Frontend state | Per-domain signal services, no NgRx | Matches Angular's current direction (signals as the primitive); avoids a global-store dependency the app's complexity doesn't need yet. |
| Mock-to-real swap | Abstract repository contract + DI token/provider swap | Lets UI work start immediately (per user's request) without a later rewrite; only the provider wiring changes. |
| Sharing model | Polymorphic `ownerType/ownerId` on Account/Category/Budget | One mental model and one authorization check path instead of duplicated private/shared tables or scattered boolean flags. |
| Auth | JWT access token (body) + refresh token (httpOnly cookie); OAuth2 as alternate login only | Standard, well-understood SPA pattern; OAuth2 complexity isolated to the login step instead of leaking into every authorization decision. |
| Backend version | Spring Boot 4.1 / Java 25 | The whole 3.x line left OSS support on 2026-06-30 (3.5 was the last); starting a new project on it would mean starting unsupported. Java 25 is the current LTS. |
| JWT handling | Spring Security resource server + `NimbusJwtEncoder`, not a custom filter | Less security code of our own to get wrong; key rotation/asymmetric keys become a bean swap. |
| Module boundaries | Spring Modulith verification in tests | Turns the "only call another feature's service" rule (§5.2) into a failing build instead of a convention. |

## 9. Open questions

Tracked with owners/status in [PLAN.md §Open decisions](./PLAN.md#open-decisions) —
not repeated here to avoid the two docs drifting out of sync.
