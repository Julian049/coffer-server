-- 2.3 (roadmap-v2.3 2.3-01): the readers decide the source, so a new provider needs no migration (REQ-COR-13)
ALTER TABLE transaction_drafts DROP CONSTRAINT ck_drafts_source;
ALTER TABLE transaction_drafts ALTER COLUMN source TYPE VARCHAR(30);
