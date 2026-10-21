-- Copied from coffer-docs data-model.md §3.6 (Sprint 10, S10-DB01). REQ-PRE-03 (SRS 1.2): fixed payment (French) or
-- fixed principal, chosen per loan; existing rows keep the plan the SRS had until then.
ALTER TABLE loan_details ADD COLUMN amortization VARCHAR(15) NOT NULL DEFAULT 'FIXED_PRINCIPAL';
ALTER TABLE loan_details ADD CONSTRAINT ck_loan_amortization CHECK (amortization IN ('FIXED_PAYMENT', 'FIXED_PRINCIPAL'));
