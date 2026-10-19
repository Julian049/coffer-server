-- Copied from coffer-docs data-model.md §4.3 (Sprint 7, S7-DB01)
-- Referencias de transactions hacia tablas creadas después de V6.
ALTER TABLE transactions
    ADD CONSTRAINT fk_tx_installment FOREIGN KEY (installment_id, user_id)
        REFERENCES installments (id, user_id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_tx_recurring FOREIGN KEY (recurring_id, user_id)
        REFERENCES recurring_expenses (id, user_id) DEFERRABLE INITIALLY DEFERRED;

-- Pull incremental: una por tabla sincronizable.
CREATE INDEX ix_user_settings_user_version       ON user_settings       (user_id, version);
CREATE INDEX ix_accounts_user_version            ON accounts            (user_id, version);
CREATE INDEX ix_credit_card_details_user_version ON credit_card_details (user_id, version);
CREATE INDEX ix_loan_details_user_version        ON loan_details        (user_id, version);
CREATE INDEX ix_categories_user_version          ON categories          (user_id, version);
CREATE INDEX ix_tags_user_version                ON tags                (user_id, version);
CREATE INDEX ix_transactions_user_version        ON transactions        (user_id, version);
CREATE INDEX ix_deferred_purchases_user_version  ON deferred_purchases  (user_id, version);
CREATE INDEX ix_installments_user_version        ON installments        (user_id, version);
CREATE INDEX ix_recurring_expenses_user_version  ON recurring_expenses  (user_id, version);
CREATE INDEX ix_change_log_user_version          ON change_log          (user_id, version);

-- Consultas de negocio y purga.
CREATE INDEX ix_tx_user_occurred ON transactions (user_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_tx_user_account  ON transactions (user_id, account_id, occurred_at DESC);
CREATE INDEX ix_inst_user_due    ON installments (user_id, due_date) WHERE status = 'SCHEDULED';
CREATE INDEX ix_cl_user_changed  ON change_log   (user_id, changed_at);
