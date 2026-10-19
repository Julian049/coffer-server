-- Copied from coffer-docs data-model.md §4.3 (Sprint 7, S7-DB01); loan_details is synced from S10
CREATE TABLE credit_card_details (
    id                              UUID          NOT NULL,
    user_id                         UUID          NOT NULL,
    credit_limit                    NUMERIC(19,4) NOT NULL,
    cut_off_day                     SMALLINT      NOT NULL,
    payment_day                     SMALLINT      NOT NULL,
    rate                            NUMERIC(9,6)  NOT NULL,
    rate_type                       CHAR(2)       NOT NULL,
    first_installment_interest_free BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ   NOT NULL,
    updated_at                      TIMESTAMPTZ   NOT NULL,
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_credit_card_details PRIMARY KEY (id),
    CONSTRAINT fk_ccd_account FOREIGN KEY (id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_ccd_limit     CHECK (credit_limit > 0),
    CONSTRAINT ck_ccd_cut_off   CHECK (cut_off_day BETWEEN 1 AND 31),
    CONSTRAINT ck_ccd_payment   CHECK (payment_day BETWEEN 1 AND 31),
    CONSTRAINT ck_ccd_rate      CHECK (rate >= 0),
    CONSTRAINT ck_ccd_rate_type CHECK (rate_type IN ('EA', 'MV'))
);

CREATE TABLE loan_details (
    id                      UUID          NOT NULL,
    user_id                 UUID          NOT NULL,
    mode                    VARCHAR(8)    NOT NULL,
    disbursement_account_id UUID,
    principal               NUMERIC(19,4),
    installments_count      SMALLINT,
    rate                    NUMERIC(9,6),
    rate_type               CHAR(2),
    first_due_date          DATE,
    payment_day             SMALLINT      NOT NULL,
    monthly_payment         NUMERIC(19,4),
    life_insurance_monthly  NUMERIC(19,4) NOT NULL DEFAULT 0,
    other_costs_monthly     NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_loan_details PRIMARY KEY (id),
    CONSTRAINT fk_ld_account FOREIGN KEY (id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_ld_disbursement FOREIGN KEY (disbursement_account_id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_ld_mode        CHECK (mode IN ('DETAILED', 'SIMPLE')),
    CONSTRAINT ck_ld_payment_day CHECK (payment_day BETWEEN 1 AND 31),
    CONSTRAINT ck_ld_rate_type   CHECK (rate_type IS NULL OR rate_type IN ('EA', 'MV')),
    CONSTRAINT ck_ld_amounts     CHECK (coalesce(principal, 1) > 0 AND coalesce(monthly_payment, 1) > 0
                                        AND coalesce(rate, 0) >= 0
                                        AND life_insurance_monthly >= 0 AND other_costs_monthly >= 0),
    CONSTRAINT ck_ld_count       CHECK (installments_count IS NULL OR installments_count BETWEEN 1 AND 600),
    CONSTRAINT ck_ld_by_mode     CHECK (
        (mode = 'DETAILED' AND principal IS NOT NULL AND installments_count IS NOT NULL
             AND rate IS NOT NULL AND rate_type IS NOT NULL AND first_due_date IS NOT NULL)
        OR (mode = 'SIMPLE' AND monthly_payment IS NOT NULL))
);

CREATE TABLE deferred_purchases (
    id                              UUID          NOT NULL,
    user_id                         UUID          NOT NULL,
    card_account_id                 UUID          NOT NULL,
    purchase_transaction_id         UUID          NOT NULL,
    amount                          NUMERIC(19,4) NOT NULL,
    installments_count              SMALLINT      NOT NULL,
    rate                            NUMERIC(9,6)  NOT NULL,
    rate_type                       CHAR(2)       NOT NULL,
    first_installment_interest_free BOOLEAN       NOT NULL,
    first_due_date                  DATE          NOT NULL,
    created_at                      TIMESTAMPTZ   NOT NULL,
    updated_at                      TIMESTAMPTZ   NOT NULL,
    deleted_at                      TIMESTAMPTZ,
    version                         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_deferred_purchases PRIMARY KEY (id),
    CONSTRAINT uq_deferred_purchases_id_user UNIQUE (id, user_id),
    CONSTRAINT uq_dp_purchase UNIQUE (purchase_transaction_id),
    CONSTRAINT fk_dp_card FOREIGN KEY (card_account_id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_dp_transaction FOREIGN KEY (purchase_transaction_id, user_id)
        REFERENCES transactions (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_dp_amount    CHECK (amount > 0),
    CONSTRAINT ck_dp_count     CHECK (installments_count BETWEEN 2 AND 72),
    CONSTRAINT ck_dp_rate      CHECK (rate >= 0),
    CONSTRAINT ck_dp_rate_type CHECK (rate_type IN ('EA', 'MV'))
);

CREATE TABLE installments (
    id                   UUID          NOT NULL,
    user_id              UUID          NOT NULL,
    deferred_purchase_id UUID,
    loan_account_id      UUID,
    number               SMALLINT      NOT NULL,
    due_date             DATE          NOT NULL,
    principal            NUMERIC(19,4) NOT NULL,
    interest             NUMERIC(19,4) NOT NULL DEFAULT 0,
    insurance            NUMERIC(19,4) NOT NULL DEFAULT 0,
    other_costs          NUMERIC(19,4) NOT NULL DEFAULT 0,
    status               VARCHAR(10)   NOT NULL DEFAULT 'SCHEDULED',
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    deleted_at           TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_installments PRIMARY KEY (id),
    CONSTRAINT uq_installments_id_user UNIQUE (id, user_id),
    CONSTRAINT fk_inst_purchase FOREIGN KEY (deferred_purchase_id, user_id)
        REFERENCES deferred_purchases (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_inst_loan FOREIGN KEY (loan_account_id, user_id)
        REFERENCES accounts (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_inst_owner   CHECK (num_nonnulls(deferred_purchase_id, loan_account_id) = 1),
    CONSTRAINT ck_inst_number  CHECK (number >= 1),
    CONSTRAINT ck_inst_amounts CHECK (principal >= 0 AND interest >= 0 AND insurance >= 0 AND other_costs >= 0),
    CONSTRAINT ck_inst_status  CHECK (status IN ('SCHEDULED', 'PAID', 'CANCELLED'))
);
CREATE UNIQUE INDEX uq_inst_number ON installments
    (coalesce(deferred_purchase_id, loan_account_id), number)
    WHERE deleted_at IS NULL AND status <> 'CANCELLED';

CREATE TRIGGER trg_credit_card_details_version BEFORE INSERT OR UPDATE ON credit_card_details
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_loan_details_version BEFORE INSERT OR UPDATE ON loan_details
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_deferred_purchases_version BEFORE INSERT OR UPDATE ON deferred_purchases
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
CREATE TRIGGER trg_installments_version BEFORE INSERT OR UPDATE ON installments
    FOR EACH ROW EXECUTE FUNCTION set_sync_version();
