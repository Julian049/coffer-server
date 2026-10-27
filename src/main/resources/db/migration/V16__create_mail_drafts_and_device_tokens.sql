-- Sprints 19 and 20 · 2.0 (ADR-0014, ADR-0016, REQ-COR-01 to 10).
-- Each user forwards bank receipts to cofferapp1+<mail_code>@gmail.com; forwarding_confirmation is Gmail's code to accept it.
ALTER TABLE users ADD COLUMN mail_code VARCHAR(12) NOT NULL DEFAULT substr(md5(random()::text || clock_timestamp()::text), 1, 8);
ALTER TABLE users ADD CONSTRAINT uq_users_mail_code UNIQUE (mail_code);
ALTER TABLE users ADD COLUMN forwarding_confirmation VARCHAR(30);

ALTER TABLE accounts ADD COLUMN last_digits CHAR(4);
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_last_digits CHECK (last_digits IS NULL OR last_digits ~ '^[0-9]{4}$');

ALTER TABLE user_settings ADD COLUMN notify_drafts BOOLEAN NOT NULL DEFAULT TRUE;

-- The queue: one row per received email, the Message-ID stops a second copy (REQ-COR-03); the body is kept only while it fails
CREATE TABLE inbound_messages (
    id           UUID         NOT NULL,
    message_id   VARCHAR(300) NOT NULL,
    user_id      UUID,
    status       VARCHAR(10)  NOT NULL,
    attempts     SMALLINT     NOT NULL DEFAULT 0,
    subject      VARCHAR(300),
    body         TEXT,
    error        VARCHAR(300),
    received_at  TIMESTAMPTZ  NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT pk_inbound_messages PRIMARY KEY (id),
    CONSTRAINT uq_inbound_messages_message_id UNIQUE (message_id),
    CONSTRAINT fk_inbound_messages_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_inbound_messages_status CHECK (status IN ('RECEIVED', 'PARSED', 'FAILED', 'IGNORED'))
);

-- A transaction to review: synced to the phone, never counted until applied there (REQ-COR-04)
CREATE TABLE transaction_drafts (
    id          UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    source      VARCHAR(12)   NOT NULL,
    type        VARCHAR(8)    NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    occurred_at TIMESTAMPTZ   NOT NULL,
    merchant    VARCHAR(200),
    last_digits CHAR(4),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_transaction_drafts PRIMARY KEY (id),
    CONSTRAINT uq_transaction_drafts_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_transaction_drafts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_drafts_source CHECK (source IN ('BANCOLOMBIA', 'RAPPICARD')),
    CONSTRAINT ck_drafts_type CHECK (type IN ('EXPENSE', 'INCOME')),
    CONSTRAINT ck_drafts_amount CHECK (amount > 0),
    CONSTRAINT ck_drafts_last_digits CHECK (last_digits IS NULL OR last_digits ~ '^[0-9]{4}$')
);
CREATE TRIGGER trg_transaction_drafts_version BEFORE INSERT OR UPDATE ON transaction_drafts
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE INDEX ix_transaction_drafts_user_version ON transaction_drafts (user_id, version);

-- ADR-0016: the FCM token of each signed-in phone
CREATE TABLE device_tokens (
    token      VARCHAR(300) NOT NULL,
    user_id    UUID         NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_device_tokens PRIMARY KEY (token),
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
