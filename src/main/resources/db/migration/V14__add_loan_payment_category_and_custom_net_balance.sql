-- Sprint 15 (S15-DB01 and S15-DB02): loan installments count as an expense of a category the user picks, and the net balance
-- subtracts only the debts the user marks.
ALTER TABLE user_settings ADD COLUMN loan_payment_category_id UUID REFERENCES categories (id) ON DELETE SET NULL;
ALTER TABLE accounts ADD COLUMN include_in_net BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_settings DROP CONSTRAINT ck_settings_net_balance_mode;
UPDATE user_settings SET net_balance_mode = 'CUSTOM' WHERE net_balance_mode = 'TOTAL_DEBT';
ALTER TABLE user_settings ADD CONSTRAINT ck_settings_net_balance_mode CHECK (net_balance_mode IN ('PERIOD', 'CUSTOM'));
