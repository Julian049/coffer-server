-- Copied from coffer-docs data-model.md §4.3 (Sprint 3, S3-DB01)
CREATE TABLE user_settings (
    id                    UUID        NOT NULL,
    user_id               UUID        NOT NULL,
    reminder_lead_days    SMALLINT    NOT NULL DEFAULT 5,
    notifications_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    home_balance_view     VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE',
    amount_decimals       SMALLINT    NOT NULL DEFAULT 0,
    home_cards            JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    deleted_at            TIMESTAMPTZ,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_settings PRIMARY KEY (id),
    CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_settings_id        CHECK (id = user_id),
    CONSTRAINT ck_settings_lead_days CHECK (reminder_lead_days BETWEEN 0 AND 30),
    CONSTRAINT ck_settings_view      CHECK (home_balance_view IN ('AVAILABLE', 'NET')),
    CONSTRAINT ck_settings_decimals  CHECK (amount_decimals IN (0, 2))
);

CREATE TABLE accounts (
    id               UUID          NOT NULL,
    user_id          UUID          NOT NULL,
    name             VARCHAR(60)   NOT NULL,
    type             VARCHAR(12)   NOT NULL,
    initial_balance  NUMERIC(19,4) NOT NULL DEFAULT 0,
    include_in_total BOOLEAN       NOT NULL DEFAULT TRUE,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    deactivated_at   TIMESTAMPTZ,
    institution      VARCHAR(60),
    icon             VARCHAR(30),
    color            CHAR(7),
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_accounts_type  CHECK (type IN ('CASH', 'DEBIT', 'SAVINGS', 'CREDIT_CARD', 'LOAN')),
    CONSTRAINT ck_accounts_color CHECK (color IS NULL OR color ~ '^#[0-9A-F]{6}$')
);

CREATE TABLE categories (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    parent_id  UUID,
    name       VARCHAR(50) NOT NULL,
    kind       VARCHAR(7)  NOT NULL,
    is_base    BOOLEAN     NOT NULL DEFAULT FALSE,
    system_key VARCHAR(40),
    icon       VARCHAR(30),
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id, user_id)
        REFERENCES categories (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_categories_kind     CHECK (kind IN ('EXPENSE', 'INCOME')),
    CONSTRAINT ck_categories_not_self CHECK (parent_id IS DISTINCT FROM id)
);
CREATE UNIQUE INDEX uq_cat_name ON categories
    (user_id, kind, coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name))
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_cat_system_key ON categories (user_id, system_key)
    WHERE system_key IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE tags (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    name       VARCHAR(50) NOT NULL,
    pinned     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uq_tags_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_tag_name ON tags (user_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TRIGGER trg_user_settings_version BEFORE INSERT OR UPDATE ON user_settings
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_accounts_version BEFORE INSERT OR UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_categories_version BEFORE INSERT OR UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_tags_version BEFORE INSERT OR UPDATE ON tags
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
