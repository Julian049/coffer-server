# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

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