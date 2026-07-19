-- MD dot-path read-conformance seed (S4-B). Idempotent DROP/CREATE/INSERT, mirroring hero_seed.sql.
-- Physical fact tables the sales-model `md2db_*` bindings target (db.dbo.<t> unparses to a bare,
-- public-schema table). Rows are small and hand-checkable so MdConformLiveTest can assert exact
-- values. The "excluded" rows prove the read view discriminates (journaling flag, measure code,
-- coordinate filters) rather than summing everything.

-- plan: LONG shape (measure_code + amount value column), INVALIDATE journaling (is_current flag).
-- grain = Customer.name (customer_name) × Time.month (month_num).
DROP TABLE IF EXISTS f_plan;
CREATE TABLE f_plan (
    customer_name text,
    month_num     integer,
    measure_code  text,
    amount        numeric,
    is_current    boolean
);
INSERT INTO f_plan (customer_name, month_num, measure_code, amount, is_current) VALUES
    ('Kaufland', 6, 'NET',   100.00, true),   -- counted
    ('Kaufland', 6, 'NET',    50.00, true),   -- counted   → SUM = 150.00
    ('Kaufland', 6, 'NET',   999.00, false),  -- excluded: superseded row (is_current = false, R31)
    ('Kaufland', 6, 'GROSS',   7.00, true),   -- excluded: measure code (long-shape NET selection)
    ('Kaufland', 7, 'NET',    30.00, true),   -- excluded: month_num coordinate
    ('Lidl',     6, 'NET',    20.00, true);   -- excluded: customer_name coordinate
-- Expected: plan.name.Kaufland.month.6.net = 150.00

-- sales: WIDE shape (one column per measure), OVERWRITE journaling (no read-view filter).
-- grain = Customer.name (customer_name) × Time.day (sale_date).
DROP TABLE IF EXISTS f_sales;
CREATE TABLE f_sales (
    customer_name text,
    sale_date     date,
    net           numeric,
    gross         numeric
);
INSERT INTO f_sales (customer_name, sale_date, net, gross) VALUES
    ('Kaufland', DATE '2025-06-20', 60.00, 80.00),   -- counted
    ('Kaufland', DATE '2025-06-20', 25.00, 30.00),   -- counted   → SUM = 85.00
    ('Kaufland', DATE '2025-06-21', 500.00, 600.00), -- excluded: sale_date coordinate
    ('Lidl',     DATE '2025-06-20', 70.00, 90.00);   -- excluded: customer_name coordinate
-- Expected: sales.name.Kaufland.day."2025-06-20".net = 85.00
