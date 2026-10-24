<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/readme/coffer-logo-horizontal-dark.png">
    <img src=".github/readme/coffer-logo-horizontal.png" alt="Coffer" width="360">
  </picture>
  <br>
  <sub>Coffer Server</sub>
</h1>

<p align="center">
  <em>El respaldo en la nube de Coffer: cuentas de usuario y sincronización para la app Android offline-first.</em>
</p>

<p align="center">
  <a href="https://github.com/Julian049/coffer-server/releases/latest"><img src="https://img.shields.io/github/v/release/Julian049/coffer-server?style=flat-square&color=4A3328&label=versi%C3%B3n" alt="Versión"></a>
  <a href="https://github.com/Julian049/coffer-server/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/Julian049/coffer-server/ci.yml?branch=develop&style=flat-square&label=CI" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java-21-4A3328?style=flat-square" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-4A3328?style=flat-square" alt="Spring Boot 4.1">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4A3328?style=flat-square" alt="PostgreSQL 16">
  <img src="https://img.shields.io/badge/licencia-todos%20los%20derechos%20reservados-4A3328?style=flat-square" alt="Licencia propietaria">
</p>

<p align="center">
  <a href="#qué-hace">Qué hace</a> •
  <a href="#api">API</a> •
  <a href="#cómo-está-hecho">Cómo está hecho</a> •
  <a href="#ejecutar-en-local">Ejecutar en local</a> •
  <a href="#entornos">Entornos</a> •
  <a href="#licencia">Licencia</a>
</p>

---

**Coffer** es una app Android de finanzas personales que funciona sin conexión: los datos viven en una base cifrada en el teléfono. Este servicio es su **copia en la nube**. Autentica al usuario, recibe los cambios que el teléfono no ha subido y le entrega los que le faltan, para que nada se pierda al cambiar o reinstalar el teléfono.

La lógica financiera (cuotas, tasas, saldos) vive en la app. El servidor guarda, valida y sincroniza (ADR-0010). La app está en [coffer-app](https://github.com/Julian049/coffer-app) y la documentación del proyecto está en un repositorio privado.

## Qué hace

- 🔐 **Cuentas de usuario.** Registro e inicio de sesión con contraseña (BCrypt) o con Google. La sesión usa un JWT propio RS256 de 15 minutos y un refresh token rotativo por familia: si se reutiliza uno viejo, se revoca toda la familia.
- 🔄 **Sincronización por lotes.**
  - **Push:** hasta 200 cambios por lote, con el UUID que genera el cliente. Gana el último cambio (`updated_at`) y los borrados son lógicos.
  - **Pull:** trae lo nuevo desde un cursor global de versión.
- 📒 **Todo el libro contable.** Ajustes, cuentas, categorías, etiquetas, movimientos, recurrentes, tarjetas, compras a cuotas, préstamos con su plan de pagos e historial de cambios.
- 🛡️ **Errores claros.** Toda respuesta de error es `application/problem+json` (RFC 9457) con un código `FIN-*`. Además hay un límite de intentos de inicio de sesión y cada consulta se filtra por el usuario autenticado.
- 🧹 **Mantenimiento automático.** Cada día se purgan los refresh tokens vencidos y el historial de cambios de más de 90 días.

## API

El contrato completo está en [`openapi.yml`](src/test/resources/contract/openapi.yml) (OpenAPI 3.1). Las pruebas lo validan en cada llamada.

| Método | Ruta | Para qué |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Crear una cuenta |
| `POST` | `/api/v1/auth/login` | Iniciar sesión con usuario o correo y contraseña |
| `POST` | `/api/v1/auth/google` | Iniciar sesión con un ID token de Google |
| `POST` | `/api/v1/auth/refresh` | Renovar la sesión |
| `POST` | `/api/v1/auth/logout` | Cerrar la sesión del dispositivo |
| `GET` | `/api/v1/me` | Perfil del usuario |
| `POST` | `/api/v1/sync/push` | Subir un lote de cambios |
| `GET` | `/api/v1/sync/pull?since=&limit=` | Traer los cambios desde un cursor |
| `GET` | `/actuator/health`, `/actuator/info` | Estado y versión del servicio |

## Cómo está hecho

| Pieza | Tecnología |
| --- | --- |
| Lenguaje y framework | Java 21, Spring Boot 4.1 (web, security, data JPA, validation, actuator) |
| Base de datos | PostgreSQL 16; montos en `NUMERIC(19,4)` con `BigDecimal` |
| Migraciones | Flyway, de V1 a V12; solo hacia adelante y nunca se editan |
| Autenticación | Spring Security como resource server con llaves RS256 |
| Pruebas | JUnit 5, Testcontainers (PostgreSQL real), ArchUnit y validación contra el contrato |
| Entrega | Docker multi-stage, GitHub Actions y Render |

Es un **monolito modular** con cuatro módulos que ArchUnit mantiene separados:

| Módulo | Qué contiene |
| --- | --- |
| `identity` | Usuarios, tokens y Google |
| `ledger` | Entidades y validaciones del libro contable |
| `sync` | Push, pull y los trabajos de purga |
| `shared` | Errores y utilidades web |

## Ejecutar en local

**Requisitos:** Java 21 y Docker. Las pruebas usan Testcontainers, así que no necesitan una base local.

```bash
docker compose up -d                 # PostgreSQL 16 (lee .env)
./mvnw spring-boot:run               # http://localhost:8080; Flyway migra al arrancar
./mvnw verify                        # todas las pruebas y el reporte de cobertura (JaCoCo)
```

`.env` de ejemplo para Docker Compose. Spring no lee este archivo: exporta las variables antes de `spring-boot:run`.

```env
DB_NAME=coffer
DB_USER=postgres
DB_PASSWORD=postgres
DB_PORT_EXTERNAL=5432
DB_PORT_INTERNAL=5432
```

En local, si no hay llaves JWT, el servicio genera un par temporal. En staging y producción las llaves son obligatorias y el servicio no arranca sin ellas.

## Entornos

| Entorno | Rama | Servicio en Render | Base en Neon |
| --- | --- | --- | --- |
| Local | cualquiera | `./mvnw spring-boot:run` | Docker Compose |
| Staging | `develop` | `coffer-server-staging` | rama `staging` |
| Producción | `main` | `coffer-server` | rama `production` |

Las variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `GOOGLE_CLIENT_ID`, `DB_POOL_SIZE`) viven en Render y en el gestor de contraseñas del dueño, nunca en el repositorio. Antes de una migración en producción se crea una rama de respaldo en Neon. Ramas, versiones y releases siguen la gestión de la configuración del proyecto (documentación privada). Los cambios de cada versión están en el [CHANGELOG](CHANGELOG.md).

## Licencia

Código propietario: **todos los derechos reservados** (ver [`LICENSE`](LICENSE)). Las bibliotecas de terceros conservan sus propias licencias: [avisos de terceros](https://julian049.github.io/coffer-app/third-party-notices.html). [Política de privacidad](https://julian049.github.io/coffer-app/privacy-policy.html) · [Términos de uso](https://julian049.github.io/coffer-app/terms.html).

## Autor

[**Julian David Bocanegra Segura**](https://github.com/Julian049): producto, diseño y desarrollo.
