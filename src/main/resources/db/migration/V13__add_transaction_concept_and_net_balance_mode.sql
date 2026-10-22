-- Copied from coffer-docs data-model.md §3.7 (Sprint 12, S12-DB01 and S12-DB02).
-- A transfer keeps a concept apart from its title (note); the net balance subtracts the period's dues or the whole debt.
ALTER TABLE transactions ADD COLUMN concept VARCHAR(200);
ALTER TABLE user_settings ADD COLUMN net_balance_mode VARCHAR(12) NOT NULL DEFAULT 'PERIOD'
    CONSTRAINT ck_settings_net_balance_mode CHECK (net_balance_mode IN ('PERIOD', 'TOTAL_DEBT'));
