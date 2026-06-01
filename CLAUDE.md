# CampAlert

Kotlin/Spring Boot service that monitors Recreation.gov for campground availability and sends Pushover push notifications when sites open up. Search requests are stored in PostgreSQL; a scheduler polls every 12 seconds and calls the Recreation.gov API for each pending request.

## Repo layout

All backend code lives under `backend/`. Future `frontend/` and `infrastructure/` directories will sit alongside it at the repo root.

## Stack

- Kotlin 1.9.25 / Spring Boot 3.3.3 / Gradle (Groovy DSL) / Java 21
- PostgreSQL — schema managed by Atlas (`backend/db/schema.sql` is the source of truth)
- OpenAPI spec-first — `backend/src/main/resources/openapi/campalert-api.yaml` drives code generation via `openApiGenerate`
- Retrofit2 — internal client for Recreation.gov API only (not used for the app's own API)

## Running locally

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

Requires `backend/src/main/resources/application-local.properties` (gitignored) with DB, Recreation.gov base URL, and Pushover credentials.

## Where things live

| Thing | Location |
|---|---|
| OpenAPI spec | `backend/src/main/resources/openapi/campalert-api.yaml` |
| Generated interfaces + models | `backend/build/generated/openapi/` (never edit directly) |
| Delegate implementations | `backend/src/main/kotlin/.../delegate/` |
| Database schema | `backend/db/schema.sql` |
| Atlas config | `backend/db/atlas.hcl` |
| Atlas + build scripts | `backend/scripts/` |

## Schema change workflow

When making any schema change:

1. Edit `backend/db/schema.sql` to reflect the desired state
2. `./backend/scripts/atlas.sh diff` — review the DDL Atlas would execute
3. `./backend/scripts/atlas.sh apply` — apply to local DB
4. Run the app — Hibernate `validate` on startup confirms the entity mappings still match
5. Commit `backend/db/schema.sql` in the same commit as any entity changes

## API change workflow

When adding or changing an endpoint:

1. Edit `backend/src/main/resources/openapi/campalert-api.yaml`
2. `cd backend && ./gradlew openApiGenerate` — regenerates interfaces and models into `backend/build/generated/openapi/`
3. Update or create the delegate in `backend/src/main/kotlin/.../delegate/`
4. `./gradlew compileKotlin` — verify no type errors before running

## CI

The required branch protection check is `ci` (from `.github/workflows/ci.yml`). It always resolves regardless of which files changed:

- A PR touching only `backend/**` runs `backend-lint` and `backend-test`; `ci` passes if both pass.
- A PR touching only root files (README, CLAUDE.md, etc.) skips all directory jobs; `ci` still passes.

To add a new directory's CI, add a filter output in the `changes` job, add gated jobs for it, and include those jobs in `ci`'s `needs` list.

## Critical constraints

**ObjectMapper must not be a `@Bean`**
The global Spring `ObjectMapper` must stay camelCase for the generated API models. The Retrofit client for Recreation.gov needs snake_case — it builds its own `ObjectMapper` inline inside `RecreationConfiguration.getRecreationClient()`. Do not extract it back to a `@Bean`.

**`ddl-auto=validate` — Hibernate validates, never mutates**
Atlas owns all DDL. Do not change `ddl-auto` to `update` or `create-drop` in the main `application.properties`.

**`loops` column uses `@JdbcTypeCode(SqlTypes.JSON)`**
Hibernate 6 binds `AttributeConverter<List<String>, String>` as `VARCHAR`, which PostgreSQL rejects for `json` columns. The `@JdbcTypeCode(SqlTypes.JSON)` annotation is required. Do not replace it with an `AttributeConverter`.

**Never edit files under `backend/build/generated/openapi/`**
They are regenerated on every `compileKotlin`. The `org.openapitools.Application` entry point is suppressed via `.openapi-generator-ignore` — do not remove that file.

## Branch and PR workflow

Only follow this workflow when explicitly asked to commit, open a PR, or otherwise interact with git.

Before staging or committing anything, run `git diff` and `git diff --cached` to understand what's currently in play — both what's staged and what isn't. Don't assume the working tree is clean.

When asked to commit or open a PR:

1. Pull the latest `main` and create a new branch from it:
   ```bash
   git checkout main && git pull && git checkout -b <branch-name>
   ```
2. Make changes and open a PR targeting `main`.

If the work depends on a branch that hasn't been merged yet, check whether that branch is merged into `main` before branching off it. If it isn't merged, branch off the unmerged branch and open a stacked PR targeting that branch — update the target to `main` once the base branch merges.

PR descriptions should only describe the change itself — what it does and why. Do not include notes about the git process (rebasing, stacking, how it relates to other PRs, etc.).

## Tests

`SearchRequestRepositoryTest` is `@Disabled` — the Testcontainers setup connects correctly but the schema isn't being created in the fresh container. Needs investigation before enabling.

Run the suite:
```bash
cd backend
./gradlew test
```
