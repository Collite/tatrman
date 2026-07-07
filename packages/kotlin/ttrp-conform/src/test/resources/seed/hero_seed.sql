-- Hero conformance seed (T3.4.4 / S3.5): the shared accounts data both A4 placement variants
-- read — variant A (accounts@PG fragment → Arrow → Polars crunch) and variant B (whole crunch@PG).
-- The sales side is the CSV fixture `sales_2026.csv` (files storage), read by both variants.
--
-- S3.5 fixes (from the live-run findings — the live test was skipped before, so these were never
-- exercised): (1) `account_id` is TEXT (was integer) so it joins the CSV's string `customer`
-- without a type error and matches the world's declared `accounts` staging schema (all string);
-- (2) `branch_code` shares the region domain so the hero's realigned second join key
-- `branch_code = region` actually matches rows — otherwise the join is empty and the proof vacuous.

CREATE SCHEMA IF NOT EXISTS erp;

DROP TABLE IF EXISTS erp.accounts;
CREATE TABLE erp.accounts (
    account_id  text NOT NULL,
    branch_code text NOT NULL,
    region      text NOT NULL,
    status      text NOT NULL
);
INSERT INTO erp.accounts (account_id, branch_code, region, status) VALUES
    ('1', 'north', 'north', 'ACTIVE'),
    ('2', 'south', 'south', 'ACTIVE'),
    ('3', 'north', 'north', 'ACTIVE'),
    ('4', 'east',  'east',  'CLOSED');   -- filtered by status = 'ACTIVE' (acc_prep fragment)
