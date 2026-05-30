# CampAlert

Kotlin/Spring Boot service that monitors Recreation.gov for campground availability and sends Pushover push notifications when sites open up. Search requests are stored in PostgreSQL; a scheduler polls every 12 seconds and calls the Recreation.gov API for each pending request.

## Stack

- Kotlin 1.9.25 / Spring Boot 3.3.3 / Gradle (Groovy DSL) / Java 21
- PostgreSQL — schema managed by Atlas (`db/schema.sql` is the source of truth)
- OpenAPI spec-first — `src/main/resources/openapi/campfinder-api.yaml` drives code generation via `openApiGenerate`
- Retrofit2 — internal client for Recreation.gov API only (not used for the app's own API)

## Running locally

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Requires `src/main/resources/application-local.properties` (gitignored) with DB, Recreation.gov base URL, and Pushover credentials.

## Where things live

| Thing | Location |
|---|---|
| OpenAPI spec | `src/main/resources/openapi/campfinder-api.yaml` |
| Generated interfaces + models | `build/generated/openapi/` (never edit directly) |
| Delegate implementations | `src/main/kotlin/.../delegate/` |
| Database schema | `db/schema.sql` |
| Atlas config | `db/atlas.hcl` |
| Atlas + build scripts | `scripts/` |

## Schema change workflow

When making any schema change:

1. Edit `db/schema.sql` to reflect the desired state
2. `./scripts/atlas.sh diff` — review the DDL Atlas would execute
3. `./scripts/atlas.sh apply` — apply to local DB
4. Run the app — Hibernate `validate` on startup confirms the entity mappings still match
5. Commit `db/schema.sql` in the same commit as any entity changes

## API change workflow

When adding or changing an endpoint:

1. Edit `src/main/resources/openapi/campfinder-api.yaml`
2. `./gradlew openApiGenerate` — regenerates interfaces and models into `build/generated/openapi/`
3. Update or create the delegate in `src/main/kotlin/.../delegate/`
4. `./gradlew compileKotlin` — verify no type errors before running

## Critical constraints

**ObjectMapper must not be a `@Bean`**
The global Spring `ObjectMapper` must stay camelCase for the generated API models. The Retrofit client for Recreation.gov needs snake_case — it builds its own `ObjectMapper` inline inside `RecreationConfiguration.getRecreationClient()`. Do not extract it back to a `@Bean`.

**`ddl-auto=validate` — Hibernate validates, never mutates**
Atlas owns all DDL. Do not change `ddl-auto` to `update` or `create-drop` in the main `application.properties`.

**`loops` column uses `@JdbcTypeCode(SqlTypes.JSON)`**
Hibernate 6 binds `AttributeConverter<List<String>, String>` as `VARCHAR`, which PostgreSQL rejects for `json` columns. The `@JdbcTypeCode(SqlTypes.JSON)` annotation is required. Do not replace it with an `AttributeConverter`.

**Never edit files under `build/generated/openapi/`**
They are regenerated on every `compileKotlin`. The `org.openapitools.Application` entry point is suppressed via `.openapi-generator-ignore` — do not remove that file.

## Tests

`SearchRequestRepositoryTest` is `@Disabled` — the Testcontainers setup connects correctly but the schema isn't being created in the fresh container. Needs investigation before enabling.

Run the suite:
```bash
./gradlew test
```
