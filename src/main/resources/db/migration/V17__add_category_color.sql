-- Sprint 24 · 2.2 (REQ-CAT-05). NULL keeps the automatic colour; only main categories use it.
ALTER TABLE categories ADD COLUMN color CHAR(7);
ALTER TABLE categories ADD CONSTRAINT ck_categories_color CHECK (color IS NULL OR color ~ '^#[0-9A-F]{6}$');
