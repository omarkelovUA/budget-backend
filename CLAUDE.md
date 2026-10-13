# Family Budget: backend

This is the backend repo. The frontend lives in `../budget-app-front/`, and the plans, which
aren't versioned, are in `../.docs/` (one file per feature in progress). Before you start
work here, load the `backend` skill (`.claude/skills/backend/SKILL.md`).

## Rules
- Write maintainable, fast code that follows current best practices. Follow the
  surrounding code's style, naming and comment density. No dead code, no
  speculative abstractions.
- Every change ships with tests. Both suites must be green before you say it's done.
- Start a feature with its plan in `../.docs/<feature>.md`, as a checkbox list. Tick each box
  once that point is implemented. When it's done, move what lasts into the repos'
  `docs/ARCHITECTURE.md` and delete the plan.
- When behavior or the API changes, update both repos' `docs/ARCHITECTURE.md`.
- Git: both repos work on `dev`. Commit per repo; push only when asked. The `gh` CLI
  isn't installed.
- API changes land in the backend first, then in the frontend. The wire contract:
  JSON under `/api/v1`, errors as `ProblemDetail` with a stable `type` slug.
- UI text is never hardcoded. Every string goes in both translation files (EN + UK).
