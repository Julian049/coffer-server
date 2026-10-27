# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.1] - 2026-10-27

### Fixed

- The mail listener runs on a platform thread: on a single CPU its IMAP IDLE pinned the only carrier of the virtual threads, so no request was served and Render restarted the service after its health check timed out

## [2.0.0] - 2026-10-27

### Added

- V15 (S18, 2.0): several tags per transaction (`transactions.tag_ids`, ADR-0015) and a color per tag (`tags.color`); `tagId` stays for 1.6 apps and mirrors the first tag; `openapi.yml` 2.0.0
- V16 (S19, S20, 2.0): reading bank receipts by email (ADR-0014). A `mail` module keeps an IMAP IDLE connection to Coffer's Gmail (`MAIL_IMAP_USER`, `MAIL_IMAP_PASSWORD`; off without them), queues each email in `inbound_messages` with its Message-ID as the guard against copies, reads Bancolombia (payment, transfer received) and RappiCard (purchase) receipts with rules and stores a `transactionDraft` for the user of the `+code`, which syncs to the phone; failed bodies are kept 7 days. `users.mail_code` and Gmail's forwarding confirmation show in `/me` (`forwardingAddress`, `forwardingConfirmation`); `accounts.lastDigits` and `user_settings.notifyDrafts` sync and are optional for older apps
- FCM notices (ADR-0016): `PUT`/`DELETE /api/v1/me/device-token`, and a `notify` module that sends a data message per draft through FCM HTTP v1 with the service account in `FCM_CREDENTIALS` (off without it, or when the user turns it off)
- Password reset with a 6-digit code (REQ-AUT-09, REQ-AUT-10): `POST /api/v1/auth/password-reset` (always 202) and `/password-reset/confirm` (revokes every session); the code is sent through Brevo's HTTP API (`BREVO_API_KEY`), is stored as a BCrypt hash, lasts 15 minutes, allows 5 attempts and at most 3 codes an hour

## [1.6.0] - 2026-10-23

### Added

- V14 (S15, #89): `user_settings.loan_payment_category_id` (the expense category that receives the principal of loan installments) and `accounts.include_in_net` (the custom net balance), both synced and optional for older apps; `openapi.yml` 1.5.0

### Changed

- Version 1.6.0 (Sprint 15)
- The net balance mode `TOTAL_DEBT` becomes `CUSTOM`; apps that still send `TOTAL_DEBT` are stored as `CUSTOM`
- Links to `coffer-docs` removed from the README and the issue template, because it is now private; a Gitleaks workflow scans every PR and push for secrets
- README and `LICENSE` point to the legal pages published from `coffer-app` with GitHub Pages, because `coffer-docs` is now private

## [1.5.0] - 2026-10-22

### Changed

- Version 1.5.0 (Sprint 14)
- Proprietary `LICENSE` (all rights reserved) and the README license section (S14-H03, coffer-app#148)

### Added

- `DELETE /api/v1/me` (S14-H01, #82): deletes the account and everything it owns after the password or, for Google-only accounts, a fresh Google ID token (wrong proof: 403 `FIN-CONFIRMATION`, so the app does not read it as an expired session); limited to 5 attempts per minute. No migration: every user table already cascades from `users` (openapi 1.4.0)

## [1.4.0] - 2026-10-22

### Added

- V13 (S12, #78): `transactions.concept`, a transfer's note apart from its title, and `user_settings.net_balance_mode` (`PERIOD` or `TOTAL_DEBT`, REQ-CTA-08); both sync as `concept` and `netBalanceMode` (openapi 1.3.0). Settings pushed without the mode keep the stored one
- `OPENING_BALANCE` system category key, for the movement that carries an account's opening balance (S12-H03)

### Changed

- Version 1.4.0 (Sprints 12 and 13)

## [1.3.0] - 2026-10-21

### Added

- `ChangeLogPurgeJob`: daily at 03:00 UTC, deletes change history entries older than 90 days (REQ-HIS-04, #72)

### Changed

- Version 1.3.0 (Sprint 11)

## [1.2.0] - 2026-10-20

### Added

- Loans in the sync (S10-SRV01, REQ-PRE-01 to 03, 08): `loanDetails` in push and pull with `LoanDetailsSyncHandler` (a LOAN account; a detailed loan needs amount, installments, rate and first due date, a simple one its monthly payment; the disbursement account must exist); V12 adds `loan_details.amortization` (`FIXED_PAYMENT` or `FIXED_PRINCIPAL`, optional in the contract, `openapi.yml` 1.2.0) (#67)

## [1.1.0] - 2026-10-20

### Added

- `categories.exclude_from_reports` (V11) and `excludeFromReports` in the category sync (S9-H03, REQ-CAT-04, `openapi.yml` 1.1.0): optional, so a `v1.0.x` app that does not send it keeps its categories included (#62)

## [1.0.0] - 2026-10-19

### Added

- `POST /api/v1/auth/google` (REQ-AUT-03, moved from S11 into 1.0.0 by the PO): `GoogleTokenVerifier` checks Google's signature (JWKS), issuer, audience = `GOOGLE_CLIENT_ID`, expiry and verified email; the user is found by Google subject, else linked by email, else created with a username taken from the email. Without `GOOGLE_CLIENT_ID` every token is refused

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