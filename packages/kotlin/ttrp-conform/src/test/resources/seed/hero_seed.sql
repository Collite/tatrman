-- Hero conformance seed (T3.4.4): accounts + sales with the A4 error-path shapes —
-- NULL keys, negative amounts (filtered by `amount > 0`), decimal money, UTC-µs timestamps.
-- Loaded into the dockerized Postgres before the gated live hero conform run.

CREATE SCHEMA IF NOT EXISTS erp;

DROP TABLE IF EXISTS erp.accounts;
CREATE TABLE erp.accounts (
    account_id  integer NOT NULL,
    branch_code text    NOT NULL,
    region      text    NOT NULL,
    status      text    NOT NULL
);
INSERT INTO erp.accounts (account_id, branch_code, region, status) VALUES
    (1, 'B01', 'north', 'ACTIVE'),
    (2, 'B02', 'south', 'ACTIVE'),
    (3, 'B01', 'north', 'ACTIVE'),
    (4, 'B03', 'east',  'CLOSED');   -- filtered by status = 'ACTIVE'

-- The sales side is CSV-hosted (files storage) in the hero; this table mirrors it for any
-- PG-placement variant and for staging round-trips. customer NULL + negative amount exercise
-- the filter (`amount > 0 and customer is not null`) and the rejects path.
DROP TABLE IF EXISTS erp.sales_txn;
CREATE TABLE erp.sales_txn (
    customer integer,
    region   text,
    amount   numeric(19,2),
    event_ts timestamptz
);
INSERT INTO erp.sales_txn (customer, region, amount, event_ts) VALUES
    (1,    'north',  120000.00, '2026-01-01T00:00:00Z'),
    (2,    'south',   50000.00, '2026-01-02T00:00:00Z'),
    (3,    'north',   30000.50, '2026-01-03T00:00:00Z'),
    (NULL, 'north',   10000.00, '2026-01-04T00:00:00Z'),  -- NULL key → rejects
    (2,    'south',    -500.00, '2026-01-05T00:00:00Z');  -- negative → filtered
