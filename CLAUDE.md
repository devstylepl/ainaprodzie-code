# Retrofit

## Project Documentation & Standards

Before writing or changing any code, ground yourself in the project's documentation:

1. Read @.maister/docs/INDEX.md to see what's documented. It is the map to the coding standards by domain and to the product context (`requirements.md`).
2. Then open and read the specific files it points to that are relevant to your task. The index alone is not enough.
3. Follow the standards as you work (they represent team decisions; if one conflicts with the task, ask the user) and use the product context to understand the domain.

## Build and Run

- `./mvnw spring-boot:run`: dev profile, PostgreSQL from compose, dev seed, app on :8080
- `./mvnw -Dskip.frontend spring-boot:run` plus `pnpm -C frontend dev`: hot reload on :5173
- `./mvnw verify`: SPA build, backend tests (Testcontainers, Docker required), contract export
- `cd frontend && pnpm lint && pnpm format:check && pnpm typecheck && pnpm test`: frontend checks (Node 24 on PATH, pinned in `.nvmrc`)
- `pnpm -C frontend generate:api && git diff --exit-code docs/api/openapi.json frontend/src/api/types.gen.ts`: contract drift check (CI runs it too)

## Course Branches

`master` is the app without AI; `lesson-N` branches stack the training's AI features on top. Read `BRANCHES.md` before branching, opening a PR or merging: it says which branch a change targets and how changes flow between branches.

## Architecture Rule

One Maven module, plain feature packages, no Spring Modulith, no multitenancy. New features are sibling packages of `app.retrofit.platform` and `app.retrofit.auth`.
