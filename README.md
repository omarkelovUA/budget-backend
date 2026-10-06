# Family Budget — Backend

Spring Boot 4.1 / Java 25 REST API for the Family Budget app. Frontend:
[`budget-app-front`](../budget-app-front). Architecture and roadmap live in
`../budget-app-front/docs/` (ARCHITECTURE.md, PLAN.md).

## Prerequisites

- JDK 25+
- Docker (with your user able to run `docker` without sudo) — used for local Postgres and
  for Testcontainers in tests.

## Run locally

```bash
./mvnw spring-boot:run
```

`spring-boot-docker-compose` starts Postgres from `compose.yaml` automatically and wires
the datasource; Flyway applies `src/main/resources/db/migration` on startup.

Health check (public): <http://localhost:8080/actuator/health>. Every other endpoint
requires a `Bearer` JWT.

## Test

```bash
./mvnw test
```

Integration tests start a throwaway Postgres via Testcontainers. `ModularityTests`
verifies module boundaries (Spring Modulith) and writes module docs to
`target/spring-modulith-docs`.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev-only value | HMAC key for access tokens (≥ 32 bytes). Must be set outside local dev. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Comma-separated frontend origins. |

## Layout

Package-by-feature modular monolith (`com.familybudget`): `common`, `security` today;
`user`, `family`, `account`, `category`, `transaction`, `budget` arrive in M7–M8.
