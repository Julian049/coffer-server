# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Multi-stage `Dockerfile` (Temurin 21 JRE, non-root) and `.dockerignore`
- GitHub Actions CI: `./mvnw -B verify` with JUnit and JaCoCo reports as artifacts, plus OWASP Dependency-Check (fails on CVSS >= 7)
- Render Blueprint (`render.yaml`) for `coffer-server-staging` (`develop`) and `coffer-server` (`main`) with `/actuator/health` checks
- Dependabot for Maven, GitHub Actions and Docker
- Spring profiles `local` (default), `staging` and `prod`

### Changed

- Database connection configured with a single `DB_URL`; Hikari pool size from `DB_POOL_SIZE` (default 4)
- Hibernate `ddl-auto=validate`, Flyway `clean-disabled`, virtual threads enabled; HTTP port from `PORT` (Render)
- Context test runs against PostgreSQL 16 with Testcontainers, so it no longer needs a local database
- Version set to `0.2.0-SNAPSHOT`

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