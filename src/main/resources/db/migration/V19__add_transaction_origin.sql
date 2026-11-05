-- Sprint 26 · 2.4. Where a movement came from: the email's merchant, the draft it was applied from, and the group of a split.
ALTER TABLE transactions ADD COLUMN source_merchant VARCHAR(200);
ALTER TABLE transactions ADD COLUMN draft_id UUID;
ALTER TABLE transactions ADD COLUMN split_group_id UUID;
