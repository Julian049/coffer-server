-- 2.5 (roadmap-v2.5, coffer-server#141): a merchant (and maybe an exact amount and/or an account) registered with a title
-- and a category; created by the user or learned by the phone, which applies it (ADR-0010). No unique on the conditions:
-- the phone keeps one active rule per condition, and a unique here would break last-write-wins sync.
CREATE TABLE draft_rules (
    id            UUID          NOT NULL,
    user_id       UUID          NOT NULL,
    merchant      VARCHAR(100)  NOT NULL,
    merchant_key  VARCHAR(100)  NOT NULL,
    amount        NUMERIC(19,4),
    account_id    UUID,
    note          VARCHAR(500)  NOT NULL,
    category_id   UUID          NOT NULL,
    level         VARCHAR(10)   NOT NULL,
    origin        VARCHAR(10)   NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    learned_since TIMESTAMPTZ,
    position      INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    deleted_at    TIMESTAMPTZ,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_draft_rules PRIMARY KEY (id),
    CONSTRAINT fk_draft_rules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_draft_rules_account FOREIGN KEY (account_id, user_id) REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_draft_rules_category FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_draft_rules_amount CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT ck_draft_rules_level CHECK (level IN ('SURE', 'PROBABLE')),
    CONSTRAINT ck_draft_rules_origin CHECK (origin IN ('USER', 'LEARNED')),
    CONSTRAINT ck_draft_rules_position CHECK (position >= 0)
);
CREATE TRIGGER trg_draft_rules_version BEFORE INSERT OR UPDATE ON draft_rules
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE INDEX ix_draft_rules_user_version ON draft_rules (user_id, version);
