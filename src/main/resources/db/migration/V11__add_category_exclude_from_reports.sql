-- Copied from coffer-docs data-model.md §3.7 (Sprint 9, S9-DB01). REQ-CAT-04: a category whose movements do not count in
-- Reports, Home or the fox; marked on parents and inherited by their subcategories (the client applies it).
ALTER TABLE categories ADD COLUMN exclude_from_reports BOOLEAN NOT NULL DEFAULT false;
