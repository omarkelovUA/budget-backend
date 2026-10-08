---
name: backend
description: Essential facts about budget-app-backend (Spring Boot API). Load before reading, changing or testing backend code, so you skip re-exploring the repo.
---

# Backend (`budget-app-backend`)

**Stack:** Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL, Flyway, JPA/Hibernate,
JWT and Google OAuth2. Build with `./mvnw verify`, which runs unit tests plus
Testcontainers integration tests (needs Docker).
**Run:** `docker compose --profile app up --build -d` starts Postgres and the API on :8080.
For http localhost, add `REFRESH_COOKIE_SECURE=false`.

## Layout (`src/main/java/com/familybudget/`)
- **Modules:** `user`, `family`, `account`, `category`, `transaction`, `budget`,
  `security`, `common`. Module boundaries are enforced by `ModularityTests`. Cross-module
  calls go only through each module's public `*Service`. Reverse dependencies use Spring
  events, e.g. `FamilyMembershipEnded`/`Started`.
- **Entities** are package-private `*Entity` classes. They're exposed as `*View` records,
  and requests are records nested in the controller.
- **`common`:**
  - `ApiException(status, slug, msg)` and `NotFoundException`, mapped to a
    `ProblemDetail` with `type = https://familybudget.app/problems/<slug>` by
    `GlobalExceptionHandler`.
  - `@CurrentUserId UUID` resolves the current user in controllers.
- **Detail strings are English.** The frontend translates by slug. A new slug must be
  added to ARCHITECTURE §5.4 and to the frontend's `errors.*` translations.

## Conventions
- Mappings are written as `/api/v1/...`. The wire format uses lower-case enums and
  `visibility: private|shared`.
- **Migrations** live in `src/main/resources/db/migration/V<n>__name.sql`; the latest is
  `V16`. Write each as additive SQL with a backfill. Data migrations get a
  `*MigrationTests` that migrates to `n-1`, inserts data, then migrates to latest.
- **Tests:**
  - Integration tests are annotated `@IntegrationTest` and use `MockMvcTester`, plus the
    helpers `TestApi` (`get`/`put`, `PROBLEMS`) and `TestUsers` (`register`,
    `Session`).
  - Region and other pure logic gets plain JUnit tests.
- **Access rules:**
  - Accounts have one owner, who grants each member VISIBLE or SHARED access.
  - Categories and budgets are private or shared with the family.
  - Transient accounts carry family transfers.
  - Details are in `docs/ARCHITECTURE.md` §3.2 and §6.3.
- **User settings** (`GET`/`PUT /users/me/settings`): `language`, `theme`,
  `defaultCurrency`, `usedCurrencies`. The sign-up region comes from `Accept-Language`
  (`ClientRegion` → `SignUpRegion`). Supported languages are in `SupportedLanguage`.
- **Performance:**
  - Avoid N+1 queries: fetch in bulk (`findAllById`, joins).
  - Balances move through bulk `UPDATE`s; `AccountEntity` is `@DynamicUpdate`.
  - Keep transactions short, and services `@Transactional(readOnly = true)` by default.
