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
| **Physical schema, data dictionary and the exact Flyway scripts V3–V10** | `../docs/03-design/data-model.md` |
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

The code below is still the **MVP (`v0.1.0`)**. Sprint 1 (`v0.2.0`) restructures it into the packages `config`/`shared`/`identity`/`ledger`/`sync`, adds Dockerfile, CI, ProblemDetail errors and springdoc; Sprint 3 replaces the MVP `/transactions` API with `/api/v1/sync/*`. When the code changes, update this file accordingly.

## Commands

```bash
docker compose up -d                 # start PostgreSQL 16 (reads .env for DB_* vars)
./mvnw spring-boot:run               # run server on 0.0.0.0:8080; Flyway migrates on startup
./mvnw clean package                 # build
./mvnw verify                        # all tests (Testcontainers, needs Docker) + JaCoCo report
./mvnw test -Dtest=ClassName#method  # run a single test
```

Configuration lives in `src/main/resources/application.yaml` plus one file per profile: `local` (default when no profile is set), `staging` and `prod` (`SPRING_PROFILES_ACTIVE` on Render). The datasource is a single `DB_URL` with `DB_USER`/`DB_PASSWORD` and `DB_POOL_SIZE` (default 4); the `local` profile builds a default `DB_URL` from `DB_PORT_EXTERNAL`/`DB_NAME` so the docker compose `.env` still works. Docker Compose reads `.env` automatically, but Spring does not — export the variables before `spring-boot:run`. Tests do **not** need a local database: `FinanceServerApplicationTests` imports `TestcontainersConfiguration` (PostgreSQL 16 via Testcontainers), so Docker must be running. Hibernate runs with `ddl-auto=validate`, so entities must match the Flyway schema. The HTTP port comes from `PORT` (Render) and defaults to 8080.

Delivery: `Dockerfile` (multi-stage, Temurin 21 JRE), `.github/workflows/ci.yml` (`./mvnw -B verify` with JaCoCo), `.github/workflows/dependency-check.yml` (OWASP, `./mvnw -Powasp dependency-check:check`, only when `pom.xml` changes, weekly and on demand; needs the `NVD_API_KEY` secret), `render.yaml` (Render Blueprint: `coffer-server-staging` from `develop`, `coffer-server` from `main`) and Dependabot.

## Architecture (MVP)

Layered: `controller` → `service` (interface + `service/impl`) → `repository` (Spring Data JPA), with `mapper` (MapStruct) converting between `model/dto` and `model/entity`.

**Sync identity model.** Each transaction has two IDs: `localId` (UUID generated on the Android device, unique constraint) and `serverId` (BIGINT identity, the primary key). The DTO exposes only `localId` (as a `String`); the mapper parses it to `UUID` and ignores `serverId`. Returned DTOs always have `synced = true` (constant in the mapper). The DB column for `synced` is named `synchronized`.

**Business rules in `TransactionServiceImpl.save`:** the client-supplied `type` is overridden from the sign of `value` (`value < 0` → `false`/expense, otherwise `true`/income), and `synced` is forced to `true`.

**Schema changes** go through Flyway only: add a new `V{n}__description.sql` in `src/main/resources/db/migration/`; never edit applied migrations. V2 rewrote the V1 schema (renamed `device_id` → `local_id`, moved PK to `server_id`), so read both to understand the current table. Keep the `Transaction` entity's `@Column` names in sync with the migrations.

**Error handling.** Domain exceptions extend `exception/core/ApiException` (carrying an `ErrorCode` enum and an `HttpStatus`); `GlobalExceptionHandler` turns them into a JSON body `{timestamp, status, error, message}`. Codes: `ERR-300` invalid values (400), `ERR-400` duplicate record (409), `ERR-500` internal (500). An `ApiException` without a dedicated handler is reported as an internal error, so a new exception type needs its own `@ExceptionHandler` to keep its status. These exceptions are not thrown anywhere in the service layer yet (e.g. a duplicate `localId` currently surfaces as a generic 500).

**Annotation processing.** Lombok and the MapStruct processor are configured explicitly in `maven-compiler-plugin` (`annotationProcessorPaths`); the test-compile execution only includes Lombok. Add any new annotation processor there.

## Conventions

Commit messages follow Conventional Commits (`feat:`, `chore:`, `docs:`, `feat(scope):`). CHANGELOG.md follows Keep a Changelog + SemVer.
