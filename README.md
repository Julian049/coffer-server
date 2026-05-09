# FinanceServer

API REST backend para la aplicación FinanceApp. Recibe y persiste las transacciones financieras sincronizadas desde el cliente Android, con soporte de migraciones de base de datos y health check integrado.

---

## Características

- Endpoint REST para crear y consultar transacciones
- Persistencia en PostgreSQL con Spring Data JPA
- Migraciones de base de datos gestionadas con Flyway
- Manejo global de excepciones con códigos de error estructurados
- Health check expuesto vía Spring Boot Actuator
- Mapeo entre entidades y DTOs con MapStruct
- Contenerización de la base de datos con Docker Compose

---

## Arquitectura

El proyecto sigue una arquitectura en capas estándar de Spring Boot:

* `controller/`: Definición de los puntos de entrada de la API.
* `service/`: Lógica de negocio y procesamiento de datos.
* `repository/`: Interfaces de comunicación con la base de datos (Spring Data JPA).
* `model/`: Entidades JPA y Objetos de Transferencia de Datos (DTOs).
* `exception/`: Lógica para el manejo centralizado de errores.
* `resources/db/migration/`: Scripts SQL para la evolución de la base de datos.

---

## API

Base URL: `http://localhost:8080`

| Método | Endpoint              | Descripción                                                     |
|--------|-----------------------|-----------------------------------------------------------------|
| GET    | `/transactions`       | Retorna todas las transacciones ordenadas por fecha descendente |
| POST   | `/transactions`       | Crea y guarda una nueva transacción                             |
| GET    | `/actuator/health`    | Health check del servidor                                       |

### Ejemplo de body (POST `/transactions`)

```json
{
  "localId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Sueldo",
  "value": 1000.0,
  "type": true,
  "description": "Pago mensual",
  "date": "2026-04-28T10:00:00+00:00",
  "synced": false
}
```

### Códigos de error

| Código   | HTTP | Descripción                      |
|----------|------|----------------------------------|
| ERR-300  | 400  | Datos enviados inválidos         |
| ERR-400  | 409  | Registro duplicado               |
| ERR-500  | 500  | Error interno del servidor       |

---

## Stack tecnológico

| Tecnología | Uso |
|---|---|
| Java | Lenguaje principal |
| Spring Boot | Framework base |
| Spring Data JPA | Acceso a datos |
| Spring Boot Actuator | Health check |
| PostgreSQL | Base de datos |
| Flyway | Migraciones de esquema |
| MapStruct | Mapeo DTO ↔ Entity |
| Lombok | Reducción de boilerplate |
| Docker Compose | Contenerización de la BD |
| Maven | Gestión de dependencias y build |

---

## Configuración y ejecución

### Requisitos previos

- Java 21
- Docker y Docker Compose
- Maven

### Variables de entorno

Crea un archivo `.env` en la raíz del proyecto con las siguientes variables:

```env
DB_NAME=financedb
DB_USER=postgres
DB_PASSWORD=supersecret
DB_HOST=localhost
DB_PORT_EXTERNAL=5432
DB_PORT_INTERNAL=5432
```

### Levantar la base de datos

```bash
docker compose up -d
```

### Ejecutar el servidor

```bash
./mvnw spring-boot:run
```

El servidor arranca en `http://0.0.0.0:8080`. Flyway aplicará las migraciones automáticamente al iniciar.

---

## Migraciones de base de datos

Las migraciones se encuentran en `src/main/resources/db/migration/` y son gestionadas por Flyway:

| Versión | Archivo | Descripción |
|---|---|---|
| V1 | `V1__first_schema.sql` | Creación inicial de la tabla `transactions` |
| V2 | `V2__fix_schema.sql` | Corrección del esquema: renombrado de columnas y ajuste de PK/UK |

---

## Autor

[**Julian David Bocanegra Segura**](https://github.com/Julian049) - Desarrollador
