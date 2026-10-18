-- Copied from coffer-docs data-model.md §4.3 (Sprint 5, S5-DB01); recurring_expenses is used from S6
CREATE TABLE recurring_expenses (
    id               UUID          NOT NULL,
    user_id          UUID          NOT NULL,
    name             VARCHAR(60)   NOT NULL,
    estimated_amount NUMERIC(19,4) NOT NULL,
    last_paid_amount NUMERIC(19,4),
    account_id       UUID          NOT NULL,
    category_id      UUID          NOT NULL,
    start_date       DATE          NOT NULL,
    frequency        VARCHAR(10)   NOT NULL,
    interval_count   SMALLINT,
    interval_unit    VARCHAR(5),
    next_due_date    DATE          NOT NULL,
    last_notified_on DATE,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_recurring_expenses PRIMARY KEY (id),
    CONSTRAINT uq_recurring_expenses_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_rec_account FOREIGN KEY (account_id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_rec_category FOREIGN KEY (category_id, user_id)
        REFERENCES categories (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_rec_amounts   CHECK (estimated_amount > 0 AND coalesce(last_paid_amount, 1) > 0),
    CONSTRAINT ck_rec_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'CUSTOM')),
    CONSTRAINT ck_rec_custom    CHECK ((frequency = 'CUSTOM') = (interval_count IS NOT NULL AND interval_unit IS NOT NULL)),
    CONSTRAINT ck_rec_interval  CHECK (interval_count IS NULL OR interval_count BETWEEN 1 AND 365),
    CONSTRAINT ck_rec_unit      CHECK (interval_unit IS NULL OR interval_unit IN ('DAY', 'WEEK', 'MONTH'))
);

CREATE TABLE change_log (
    id             UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    action         VARCHAR(7)  NOT NULL,
    before_value   JSONB,
    after_value    JSONB,
    changed_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_change_log PRIMARY KEY (id),
    CONSTRAINT fk_cl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_cl_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE'))
);

CREATE TRIGGER trg_recurring_expenses_version BEFORE INSERT OR UPDATE ON recurring_expenses
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_change_log_version BEFORE INSERT OR UPDATE ON change_log
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
