-- Copied from coffer-docs data-model.md §4.3 (Sprint 8, S8-OPS03). The MVP data is not claimed (PO, 2026-10-19):
-- the history arrives with the CSV import, so the old table and the owner user go, with the owner's rows (ON DELETE CASCADE).
DROP TABLE IF EXISTS transactions_mvp;
DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000001';
