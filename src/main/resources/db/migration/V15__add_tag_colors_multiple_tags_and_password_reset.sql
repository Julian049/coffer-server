-- Sprint 18 · 2.0 (ADR-0015, REQ-ETQ-02, REQ-ETQ-05, REQ-AUT-09, REQ-AUT-10).
-- Expand: tag_id stays for 1.6 clients until 2.1; tag_ids is the list the 2.0 app sends.
ALTER TABLE tags ADD COLUMN color CHAR(7);
ALTER TABLE tags ADD CONSTRAINT ck_tags_color CHECK (color IS NULL OR color ~ '^#[0-9A-F]{6}$');

ALTER TABLE transactions ADD COLUMN tag_ids UUID[] NOT NULL DEFAULT '{}';
UPDATE transactions SET tag_ids = ARRAY[tag_id] WHERE tag_id IS NOT NULL;
CREATE INDEX ix_transactions_tag_ids ON transactions USING GIN (tag_ids);

CREATE TABLE password_reset_codes (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    attempts   SMALLINT     NOT NULL DEFAULT 0,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_password_reset_codes PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_password_reset_user ON password_reset_codes (user_id, created_at DESC);
