# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-10-19

### Removed

- `V10`: drops `transactions_mvp` and the MVP `owner` user with its rows; the MVP data is not claimed, the history arrives with the CSV import (S8-OPS03)

## [0.8.0] - 2026-10-19

### Added

- `V8` (credit cards, loans, deferred purchases, installments) and `V9` (foreign keys from transactions to installments and recurring expenses, sync and business indexes), copied from data-model §4.3
- Sync of `creditCardDetails`, `deferredPurchase` and `installment` (S7-SRV01): card terms only for a `CREDIT_CARD` account; a deferred purchase is an expense of that card for the same amount; an installment belongs to a deferred purchase or a loan, never both, and the live installments of a purchase never add up to more principal than it
- A transaction's `installmentId` must point to an installment of the same user (`FIN-REFERENCE`)

### Fixed

- Pull keyed loaded rows by id only, so a card's terms (same id as its account) replaced the account in the page

## [0.7.0] - 2026-10-18

### Added

- Sync of `recurringExpense` (S6-SRV01): push and pull with last-write-wins; the category must be an expense one, the account and category must exist, and only `CUSTOM` carries `intervalCount` and `intervalUnit`
- A transaction's `recurringId` must point to a recurring expense of the same user (`FIN-REFERENCE`)

## [0.6.0] - 2026-10-18

### Added

- `V7`: `change_log` (history of every transaction) and `recurring_expenses` (used from S6)
- Sync of `changeLog`: push and pull with last-write-wins like the other tables; a JSON null before or after value is stored as SQL NULL

## [0.5.0] - 2026-10-18

### Changed

- Push rejects changing the type of an existing account and turning a category that has subcategories into a subcategory (`FIN-VALIDATION`)

## [0.4.0] - 2026-10-17

### Added

- Ledger schema: `V4` (global `sync_version` sequence and trigger), `V5` (`user_settings`, `accounts`, `categories`, `tags`), `V6` (new `transactions`; the MVP table is kept as `transactions_mvp` and its rows move to the `owner` user)
- `GET /api/v1/sync/pull`: changes of every synced table ordered by the global version, deletions included, `remaining` on the initial download
- Ledger JPA entities and repositories; `FlywayMigrationIT` (empty database and database with MVP data) and `PullServiceIT`
- `POST /api/v1/sync/push`: batches of up to 200 changes, one result per change (`APPLIED` with version, `STALE`, `REJECTED` with `FIN-VALIDATION`/`FIN-NOT-OWNER`/`FIN-REFERENCE`), last-write-wins by `updatedAt`, 413 above 200
- Sync handlers for settings, accounts, categories, tags and transactions; `LedgerValidators` checks references before writing so one bad change never fails the batch; each write runs under a savepoint
- `PushServiceIT` (dependency order, resend without duplicates, last change wins, deletions, another user's id, isolated failures, 413)

### Removed

- MVP `/transactions` API, Lombok and MapStruct

## [0.3.0] - 2026-10-16

### Added

- Authentication API (`/api/v1/auth/register`, `login`, `refresh`, `logout`) and `GET /api/v1/me`, as in `openapi.yml`
- `V3__create_identity.sql`: `users` and `refresh_tokens`
- RS256 access tokens (15 min) signed with `JWT_PRIVATE_KEY`; rotating refresh tokens (SHA-256, 180-day sliding expiry) with family revocation on reuse
- BCrypt (cost 12), case-insensitive uniqueness of username and email, same error for unknown user and wrong password
- Login rate limit (5 per minute per IP and identifier) with `429 FIN-RATE-LIMIT` and `Retry-After`
- Daily purge of expired refresh tokens; `CurrentUser` controller parameter

### Changed

- Every route except auth, health, info and API docs requires a bearer token; the MVP `/transactions` API is no longer public
- `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` are required in staging and prod (Render Blueprint updated)
- Spring Boot 4.0.8 -> 4.1.1; Flyway version managed by Spring Boot (12.4.0) instead of a manual pin
- Dependabot groups minor/patch Maven bumps and all Actions bumps; never proposes a new Java major for the Docker image

## [0.2.0] - 2026-10-16

### Added

- Multi-stage `Dockerfile` (Temurin 21 JRE, non-root) and `.dockerignore`
- GitHub Actions CI: `./mvnw -B verify` with JUnit and JaCoCo reports as artifacts, plus a separate OWASP Dependency-Check workflow (fails on CVSS >= 7; weekly and on demand, 60 min limit)
- Render Blueprint (`render.yaml`) for `coffer-server-staging` (`develop`) and `coffer-server` (`main`) with `/actuator/health` checks
- Dependabot for Maven, GitHub Actions and Docker
- Spring profiles `local` (default), `staging` and `prod`
- Module packages `config`, `shared`, `identity`, `ledger`, `sync`; the MVP code moves to `legacy`
- RFC 9457 errors: `ProblemDetailsAdvice`, `ApiException` and `FIN-*` error codes; every error is `application/problem+json`
- `ClockConfig` (injectable UTC clock) and `JacksonConfig` (`BigDecimal` as JSON string, ISO-8601 dates)
- springdoc-openapi at `/v3/api-docs`; Actuator `info` shows the `pom.xml` version (build-info)
- Tests: `ArchitectureTest` (ArchUnit module rules, no `double`/`float` in `ledger`), `ProblemDetailsAdviceTest` and a contract test of `/actuator/health` against `openapi.yml`

### Removed

- `GlobalExceptionHandler` and the `ERR-*` error format, replaced by RFC 9457 problems

### Changed

- A duplicate `localId` returns 409 `FIN-CONFLICT` instead of 500
- MVP transaction amounts are serialized as JSON strings (`"28000.0000"`)
- Database connection configured with a single `DB_URL`; Hikari pool size from `DB_POOL_SIZE` (default 4)
- Hibernate `ddl-auto=validate`, Flyway `clean-disabled`, virtual threads enabled; HTTP port from `PORT` (Render)
- Context test runs against PostgreSQL 16 with Testcontainers, so it no longer needs a local database
- Version set to `0.2.0-SNAPSHOT`
- Spring Boot 4.0.6 -> 4.0.8 to pick up fixed Jackson, PostgreSQL JDBC, Tomcat and Spring Framework versions (CVSS >= 7 reported by Dependency-Check)
- `CLAUDE.md`: sibling repositories referenced by relative path (`../docs/`) instead of an absolute WSL path

## [0.1.0] - 2026-05-09

### Added

- `GET /transactions` endpoint to retrieve all transactions sorted in descending order by date
- `POST /transactions` endpoint to create and persist new transactions
- Health check exposed at `GET /actuator/health` via Spring Boot Actuator
- Persistence in PostgreSQL using Spring Data JPA
- Database migrations managed with Flyway (V1 and V2)
- Automatic mapping between `TransactionDTO` and `Transaction` using MapStruct
- Global exception handling with `GlobalExceptionHandler` and structured error codes (`ERR-300`, `ERR-400`, `ERR-500`)
- Database containerization with Docker Compose (PostgreSQL 16 Alpine)
- Connection configuration via environment variables