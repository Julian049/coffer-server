# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Product and documentation

This repository is the cloud service of **Coffer** (formerly "Finance App"), called *Coffer Server*: a single Spring Boot service (modular monolith) that the offline-first Android app uses for authentication and backup/sync. GitHub: `Julian049/coffer-server`. Sibling repositories: `coffer-app` (Android) and `coffer-docs` (documentation). Locally they live side by side in the same parent folder (`finance-server/`, `finance-app/`, `docs/`), so the docs are at `../docs/`.

**The documentation is the source of truth — read it before changing anything structural:**

| What | Where |
| --- | --- |
| Requirements (SRS v1.0) | `../docs/01-requirements/` |
| Architecture (arc42) and ADRs (Spring Boot, Flyway, sync, auth…) | `../docs/02-architecture/architecture.md`, `../docs/02-architecture/adr/` |
| Detailed design: packages, classes, algorithms, config properties | `../docs/03-design/detailed-design.md` |
| **Physical schema, data dictionary and the exact Flyway scripts V3–V11** | `../docs/03-design/data-model.md` |
| Sequence diagrams (push/pull, auth…) | `../docs/03-design/sequences/` |
| **API contract** (implement exactly this) | `../docs/04-api/openapi.yml` |
| Test plan | `../docs/05-test/test-plan.md` |
| Branching, versioning, releases, environments | `../docs/06-settings/manage-config.md` |
| Scrum rules and **sprint plan** | `../docs/07-management/scrum.md`, `../docs/07-management/sprint-plan.md` |

## How work is done here

- Work proceeds **sprint by sprint** following `sprint-plan.md`; only implement the current sprint's stories. One story = one issue, one branch `feature/<issue>-<slug>` from `develop`, one PR to `develop` with "Cómo probar". When a story spans server and app, the server PR is merged first.
- Never push to `main` or `develop` directly, never force-push shared branches, never commit secrets (`.env`, JWT keys, DB passwords).
- Flyway migrations are **forward-only and immutable once applied**; copy them from `data-model.md` §4.3 and keep that document in sync if a migration changes before being applied.
- Ask the product owner (@Julian) when the docs are ambiguous; structural decisions get an ADR.
- Conventional Commits; CHANGELOG updated under `[Unreleased]` in every PR. Commits produced with AI assistance carry a `Co-Authored-By` trailer.

## Environments

| Env | Git branch | Render service | Neon branch (project `coffer`, database `coffer`) |
| --- | --- | --- | --- |
| Local | any | `./mvnw spring-boot:run` | `docker compose` PostgreSQL 16 |
| Staging | `develop` | `coffer-server-staging` | `staging` |
| Production | `main` | `coffer-server` | `production` |

Services are created in Sprint 1 from a `render.yaml` blueprint. Env vars: `SPRING_PROFILES_ACTIVE`, `DB_URL` (`jdbc:postgresql://<host>/coffer?sslmode=require`, direct host without `-pooler`), `DB_USER`, `DB_PASSWORD`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `GOOGLE_CLIENT_ID`, `DB_POOL_SIZE`. Values live in the owner's password manager and in Render, never in the repo.

## Current state

`v1.0.0` is in production (BL-3, 2026-10-19): identity (password and Google sign-in), the ledger tables V3–V9 and `/api/v1/sync/*`; V10 dropped the MVP table. The app's `v1.0.1` hotfix needed no server change. After `v1.0.0` development is paused (PO, 2026-10-19) until the PO's findings in daily use are triaged; `v1.1.0` (Sprint 9, 2026-10-20) is in production; `develop` is `1.2.0-SNAPSHOT` for Sprint 10 (loans). V11 adds `categories.exclude_from_reports`, optional in the sync (a missing field means false; record components that old clients may omit must be boxed, Jackson 3 rejects a missing primitive). When the code changes, update this file accordingly.

## Commands

```bash
docker compose up -d                 # start PostgreSQL 16 (reads .env for DB_* vars)
./mvnw spring-boot:run               # run server on 0.0.0.0:8080; Flyway migrates on startup
./mvnw clean package                 # build
./mvnw verify                        # all tests (Testcontainers, needs Docker) + JaCoCo report
./mvnw test -Dtest=ClassName#method  # run a single test
```

Configuration lives in `src/main/resources/application.yaml` plus one file per profile: `local` (default when no profile is set), `staging` and `prod` (`SPRING_PROFILES_ACTIVE` on Render). The datasource is a single `DB_URL` with `DB_USER`/`DB_PASSWORD` and `DB_POOL_SIZE` (default 4); the `local` profile builds a default `DB_URL` from `DB_PORT_EXTERNAL`/`DB_NAME` so the docker compose `.env` still works. Docker Compose reads `.env` automatically, but Spring does not — export the variables before `spring-boot:run`. Tests do **not** need a local database: `FinanceServerApplicationTests` imports `TestcontainersConfiguration` (PostgreSQL 16 via Testcontainers), so Docker must be running. Hibernate runs with `ddl-auto=validate`, so entities must match the Flyway schema. The HTTP port comes from `PORT` (Render) and defaults to 8080.

Delivery: `Dockerfile` (multi-stage, Temurin 21 JRE), `.github/workflows/ci.yml` (`./mvnw -B verify` with JaCoCo), `.github/workflows/dependency-check.yml` (OWASP, `./mvnw -Powasp dependency-check:check`, weekly and on demand only because the NVD API is slow; needs the `NVD_API_KEY` secret). Day-to-day vulnerability alerts and security PRs come from Dependabot (enabled in the repo settings), `render.yaml` (Render Blueprint: `coffer-server-staging` from `develop`, `coffer-server` from `main`) and Dependabot.

## Architecture

Modular monolith (detailed-design §6.2.1), base package `com.dev.julian09.financeserver`:

- `config/`: `ClockConfig` (inject `Clock`, never call `now()` directly), `JacksonConfig` (`BigDecimal` serialized as a JSON string; dates ISO-8601), `OpenApiConfig` (springdoc at `/v3/api-docs`, version from `build-info`).
- `shared/error/`: `ErrorCode` (`FIN-*` codes of the contract, each with HTTP status, `type` URI and title), `ApiException(ErrorCode, detail)` and `ProblemDetailsAdvice`, which turns **every** error into `application/problem+json` (RFC 9457) with a `code` member: domain errors keep their code; bean validation adds `errors[{field, message}]`; Spring's own errors (bad JSON, unknown route…) get a code from `ErrorCode.forStatus`; `DataIntegrityViolationException` → `FIN-CONFLICT`; anything else → `FIN-INTERNAL` without details (RNF-07). To fail from a service, throw `ApiException`.
- `identity/` (ADR-0007, SEQ-01/03): `AuthController` (`/api/v1/auth/register|login|refresh|logout`), `MeController` (`/api/v1/me`); `AuthService` orchestrates `UserService` (BCrypt 12, case-insensitive uniqueness with the same `lower()` as the V3 indexes, a dummy hash so unknown users take as long as wrong passwords), `GoogleTokenVerifier` (Google ID tokens: JWKS signature, issuer, audience = `finance.google.client-id` from `GOOGLE_CLIENT_ID`, expiry, verified email; refuses everything without a client id; `POST /api/v1/auth/google` finds by `google_subject`, else links by email, else creates the user), `TokenService` (RS256 JWT 15 min with `sub` = user id; refresh = 256 random bits stored as SHA-256, rotated on every use under a row lock, reuse or another device revokes the whole family, 180-day sliding expiry) and `LoginRateLimiter` (in-memory sliding window, 5/min per IP + identifier → `RateLimitException` → 429 with `Retry-After`). `RefreshTokenPurgeJob` runs daily (03:30 UTC). `JwtProperties` (`finance.jwt.*`) lives here because `config` depends on modules, not the reverse.
- `config/SecurityConfig`: stateless resource server; public: `POST /api/v1/auth/{register,login,google,refresh}`, `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`; everything else needs `Authorization: Bearer`. Missing/invalid tokens go through the `HandlerExceptionResolver` so they become the same RFC 9457 401 (`FIN-AUTH-INVALID`). `JwtConfig` reads PEM keys from `JWT_PRIVATE_KEY` (PKCS#8) / `JWT_PUBLIC_KEY` (X.509); **without them it generates a temporary pair (local and tests only)**; staging/prod profiles declare them without default, so the service refuses to start if they are missing. `server.forward-headers-strategy: native` makes `getRemoteAddr()` the client IP behind Render.
- `shared/web/CurrentUser`: declare it as a controller parameter to get the authenticated user id; every query must filter by it (RNF-17).
- `ledger/` (V5, V6): JPA entities extending `SyncableEntity` (`id` from the client, `user_id`, `created_at`, `updated_at` decides last-write-wins, `deleted_at` soft delete, `version` set by the V4 trigger and never written by JPA) for `user_settings` (id = user id), `accounts`, `categories`, `tags`, `transactions`, `change_log`, `recurring_expenses` (V7), `credit_card_details`, `deferred_purchases`, `installments` (V8; `loan_details` is synced from S10), plus one `JpaRepository` each. Amounts are `BigDecimal` `NUMERIC(19,4)`, always positive (the type gives the direction).
- `sync/` (ADR-0006): `SyncController` `GET /api/v1/sync/pull?since&limit` → `PullService`: a `UNION ALL` over the synced tables pages `(entity, id, version)` by the global cursor (add one line per new table), then JPA loads the rows and maps them to `PulledChange` + `EntityData` records (the contract's `*Data`), keyed by entity and id because a card's terms share its account's id. `remaining` only when `since=0`. `POST /api/v1/sync/push` → `PushService` (one transaction per batch, max 200 → 413): parses each change's `data` with the handler of its entity (`SyncEntityRegistry` → `SyncHandler` subclasses for settings, account, creditCardDetails, category, tag, recurringExpense, transaction, deferredPurchase, installment, changeLog; `loanDetails` is REJECTED until S10), validates it with the Bean Validation constraints on the `EntityData` records, runs `check` (cross-field rules and references via `ledger/validation/LedgerValidators`, so deferred FKs never fail at commit), sorts by `order()` (settings → accounts → card terms and root categories → subcategories → tags and recurring expenses → transactions → deferred purchases and change log → installments) and writes each one under a JDBC savepoint with `INSERT … ON CONFLICT (id) DO UPDATE … WHERE same user AND newer updated_at RETURNING version`. No row back: `STALE` if the row is ours, `REJECTED FIN-NOT-OWNER` otherwise. Results keep the request order. To sync a new table: a migration with the version trigger, an entity, an `EntityData` record, a handler, and one line in `PullService.CHANGES`.
- `legacy/`: removed (the MVP notes below are history only).

Rules enforced by `ArchitectureTest` (ArchUnit): modules (`shared`, `identity`, `ledger`, `sync`) never depend on `config`; `ledger` does not depend on `sync`/`identity`; `identity` does not depend on `ledger`/`sync`; `shared` depends on no module; new modules never depend on `legacy`; `@RestController`s do not use Spring Data repositories; no `double`/`float` fields in `ledger` (ADR-0004).

Tests: `AuthControllerIT` and `TokenServiceTest` (Testcontainers) cover the auth flows; use `Contract.matches()` to validate any MockMvc call against the contract (strict: no undeclared properties), or `Contract.matchesComposed()` for `/sync`, whose `oneOf` + discriminator over `allOf` schemas the validator cannot handle strictly (it relaxes only `additionalProperties` and the discriminator mapping; types, enums, consts and formats are still checked). `FinanceServerApplicationTests` (Testcontainers) also validates `/actuator/health` against `src/test/resources/contract/openapi.yml` with swagger-request-validator. That file is a **copy** of `../docs/04-api/openapi.yml`; refresh it when the contract changes. Actuator exposes `health` and `info` (`build.version` = `pom.xml` version).

## Legacy MVP (removed)


Layered: `controller` → `service` (interface + `service/impl`) → `repository` (Spring Data JPA), with `mapper` (MapStruct) converting between `model/dto` and `model/entity`.

**MVP (removed in v0.4.0).** The old `/transactions` API and its `legacy` package were deleted in S3-H01a; V6 renamed its table to `transactions_mvp` (dropped in V10). The notes below describe the v0.1.0 MVP only for history.

**Sync identity model.** Each transaction has two IDs: `localId` (UUID generated on the Android device, unique constraint) and `serverId` (BIGINT identity, the primary key). The DTO exposes only `localId` (as a `String`); the mapper parses it to `UUID` and ignores `serverId`. Returned DTOs always have `synced = true` (constant in the mapper). The DB column for `synced` is named `synchronized`.

**Business rules in `TransactionServiceImpl.save`:** the client-supplied `type` is overridden from the sign of `value` (`value < 0` → `false`/expense, otherwise `true`/income), and `synced` is forced to `true`.

**Schema changes** go through Flyway only: add a new `V{n}__description.sql` in `src/main/resources/db/migration/`; never edit applied migrations. V2 rewrote the V1 schema (renamed `device_id` → `local_id`, moved PK to `server_id`), so read both to understand the current table. Keep the `Transaction` entity's `@Column` names in sync with the migrations.

**Money.** `value` is `BigDecimal` (column `NUMERIC(19,4)`) and is serialized as a JSON string by `JacksonConfig`; the MVP app (Gson, `Double`) still parses it. A duplicate `localId` now returns 409 `FIN-CONFLICT`.

## Conventions

Commit messages follow Conventional Commits (`feat:`, `chore:`, `docs:`, `feat(scope):`). CHANGELOG.md follows Keep a Changelog + SemVer.
