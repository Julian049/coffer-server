# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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