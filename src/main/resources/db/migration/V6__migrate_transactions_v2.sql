-- Copied from coffer-docs data-model.md §4.3 (Sprint 3, S3-DB01)
-- Expand: se conserva la tabla del MVP renombrada hasta V10.
ALTER TABLE transactions RENAME TO transactions_mvp;
ALTER TABLE transactions_mvp RENAME CONSTRAINT transactions_pkey TO pk_transactions_mvp;
ALTER TABLE transactions_mvp RENAME CONSTRAINT uq_transactions_local_id TO uq_transactions_mvp_local_id;

CREATE TABLE transactions (
    id                     UUID          NOT NULL,
    user_id                UUID          NOT NULL,
    type                   VARCHAR(8)    NOT NULL,
    amount                 NUMERIC(19,4) NOT NULL,
    occurred_at            TIMESTAMPTZ   NOT NULL,
    account_id             UUID          NOT NULL,
    destination_account_id UUID,
    category_id            UUID,
    tag_id                 UUID,
    note                   VARCHAR(500),
    installments           SMALLINT      NOT NULL DEFAULT 1,
    principal              NUMERIC(19,4),
    interest               NUMERIC(19,4),
    fees                   NUMERIC(19,4),
    installment_id         UUID,
    recurring_id           UUID,
    payment_kind           VARCHAR(12),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    deleted_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uq_transactions_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_tx_destination FOREIGN KEY (destination_account_id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id, user_id)
        REFERENCES categories (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_tx_tag FOREIGN KEY (tag_id, user_id)
        REFERENCES tags (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_tx_type         CHECK (type IN ('EXPENSE', 'INCOME', 'TRANSFER')),
    CONSTRAINT ck_tx_amount       CHECK (amount > 0),
    CONSTRAINT ck_tx_installments CHECK (installments BETWEEN 1 AND 72),
    CONSTRAINT ck_tx_transfer     CHECK ((type = 'TRANSFER') = (destination_account_id IS NOT NULL)
                                         AND destination_account_id IS DISTINCT FROM account_id),
    CONSTRAINT ck_tx_category     CHECK ((type = 'TRANSFER') = (category_id IS NULL)),
    CONSTRAINT ck_tx_breakdown_nonneg CHECK (coalesce(principal, 0) >= 0 AND coalesce(interest, 0) >= 0
                                             AND coalesce(fees, 0) >= 0),
    CONSTRAINT ck_tx_breakdown    CHECK ((principal IS NULL AND interest IS NULL AND fees IS NULL)
                                         OR coalesce(principal, 0) + coalesce(interest, 0) + coalesce(fees, 0) = amount),
    CONSTRAINT ck_tx_payment_kind CHECK (payment_kind IS NULL
                                         OR payment_kind IN ('CARD_MIN', 'CARD_TOTAL', 'LOAN_INSTALL', 'LOAN_EXTRA'))
);
CREATE TRIGGER trg_transactions_version BEFORE INSERT OR UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();

-- Migración de los datos del MVP al usuario owner (solo si existen).
INSERT INTO accounts (id, user_id, name, type, initial_balance, created_at, updated_at)
SELECT '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001',
       'Efectivo (migrada)', 'CASH', 0, now(), now()
WHERE EXISTS (SELECT 1 FROM transactions_mvp);

INSERT INTO categories (id, user_id, name, kind, is_base, created_at, updated_at)
SELECT c.id, '00000000-0000-0000-0000-000000000001', 'Sin categoría', c.kind, TRUE, now(), now()
FROM (VALUES ('00000000-0000-0000-0000-0000000000b1'::uuid, 'EXPENSE'),
             ('00000000-0000-0000-0000-0000000000b2'::uuid, 'INCOME')) AS c(id, kind)
WHERE EXISTS (SELECT 1 FROM transactions_mvp);

INSERT INTO transactions (id, user_id, type, amount, occurred_at, account_id, category_id, note,
                          created_at, updated_at)
SELECT m.local_id,
       '00000000-0000-0000-0000-000000000001',
       CASE WHEN m.value < 0 THEN 'EXPENSE' ELSE 'INCOME' END,
       abs(m.value),
       m.date,
       '00000000-0000-0000-0000-0000000000a1',
       CASE WHEN m.value < 0 THEN '00000000-0000-0000-0000-0000000000b1'::uuid
                             ELSE '00000000-0000-0000-0000-0000000000b2'::uuid END,
       left(m.title || coalesce(' · ' || nullif(m.description, ''), ''), 500),
       m.date,
       now()
FROM transactions_mvp m
WHERE m.value <> 0;
