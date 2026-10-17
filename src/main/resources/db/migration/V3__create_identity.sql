-- Copied from coffer-docs data-model.md §4.3 (Sprint 2, S2-DB01)
CREATE TABLE users (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    username        VARCHAR(40)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(100),
    google_subject  VARCHAR(255),
    currency        CHAR(3)      NOT NULL DEFAULT 'COP',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_username   CHECK (username ~ '^[a-z0-9._]{3,40}$'),
    CONSTRAINT ck_users_currency   CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_users_credential CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL)
);
CREATE UNIQUE INDEX uq_users_username       ON users (lower(username));
CREATE UNIQUE INDEX uq_users_email          ON users (lower(email));
CREATE UNIQUE INDEX uq_users_google_subject ON users (google_subject) WHERE google_subject IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    family_id   UUID        NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    device_id   VARCHAR(64) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_rt_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_rt_user_family ON refresh_tokens (user_id, family_id);

-- Dueño de los datos del MVP. Solo se crea si hay transacciones que migrar.
-- No puede iniciar sesión ('!' no es un hash BCrypt válido) hasta reclamarlo (ver §4.4).
INSERT INTO users (id, username, email, display_name, password_hash)
SELECT '00000000-0000-0000-0000-000000000001', 'owner', 'owner@finance.local', 'Propietario (MVP)', '!'
WHERE EXISTS (SELECT 1 FROM transactions);
