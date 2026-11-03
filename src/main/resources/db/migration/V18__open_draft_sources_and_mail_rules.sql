-- 2.3 (roadmap-v2.3 2.3-01): the readers decide the source, so a new provider needs no migration (REQ-COR-13)
ALTER TABLE transaction_drafts DROP CONSTRAINT ck_drafts_source;
ALTER TABLE transaction_drafts ALTER COLUMN source TYPE VARCHAR(30);

-- 2.3-02 (REQ-COR-11): a provider, or a provider and its last 4 digits, sent to one account; applied on the phone
CREATE TABLE mail_rules (
    id          UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    source      VARCHAR(30) NOT NULL,
    last_digits CHAR(4),
    account_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_mail_rules PRIMARY KEY (id),
    CONSTRAINT fk_mail_rules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_rules_account FOREIGN KEY (account_id, user_id) REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_mail_rules_last_digits CHECK (last_digits IS NULL OR last_digits ~ '^[0-9]{4}$')
);
CREATE TRIGGER trg_mail_rules_version BEFORE INSERT OR UPDATE ON mail_rules
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE INDEX ix_mail_rules_user_version ON mail_rules (user_id, version);
