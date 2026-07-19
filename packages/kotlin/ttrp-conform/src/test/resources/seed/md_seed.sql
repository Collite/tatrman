-- MD dot-path read-conformance seed (S4-B). Idempotent DROP/CREATE/INSERT, mirroring hero_seed.sql.
-- Physical fact/dimension tables the sales-model `md2db_*` bindings target (db.dbo.<t> unparses to a
-- bare, public-schema table). Rows are small and hand-checkable so MdConformLiveTest can assert exact
-- values; the "excluded" rows prove the read view discriminates (journaling flag, measure code,
-- coordinate filters, calc drill) rather than summing everything.

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
-- grain = Customer.name (customer_name) × Time.day (sale_date). Rows chosen so the day/all/year/month
-- reads are all distinct values.
DROP TABLE IF EXISTS f_sales;
CREATE TABLE f_sales (
    customer_name text,
    sale_date     date,
    net           numeric,
    gross         numeric
);
INSERT INTO f_sales (customer_name, sale_date, net, gross) VALUES
    ('Kaufland', DATE '2025-06-20',  60.00,  80.00),  -- day 2025-06-20, month 6, year 2025
    ('Kaufland', DATE '2025-06-20',  25.00,  30.00),  -- day 2025-06-20 → SUM(day) = 85.00
    ('Kaufland', DATE '2025-06-21', 500.00, 600.00),  -- month 6, year 2025 (not day 06-20)
    ('Kaufland', DATE '2025-03-01', 300.00, 350.00),  -- year 2025, month 3
    ('Kaufland', DATE '2024-11-10', 1000.00, 1100.00),-- year 2024, month 11
    ('Lidl',     DATE '2025-06-20',  70.00,  90.00);  -- excluded: customer_name coordinate
-- Expected: sales.name.Kaufland.day."2025-06-20".net  = 85.00   (60+25)
--           sales.name.Kaufland.year.2025.net.sum      = 885.00  (2025 only: 60+25+500+300; inline EXTRACT)
--           sales.name.Kaufland.month.6.net.sum        = 585.00  (June via d_calendar: 60+25+500)

-- d_calendar: the date→month calc-map case table (date_to_month, columns cal_date/cal_month). Backs the
-- table-backed viaCalc drill for `sales.…month.<m>` (grain Time.day is coarsened to Time.month here).
DROP TABLE IF EXISTS d_calendar;
CREATE TABLE d_calendar (
    cal_date  date,
    cal_month integer
);
INSERT INTO d_calendar (cal_date, cal_month) VALUES
    (DATE '2025-06-20', 6),
    (DATE '2025-06-21', 6),
    (DATE '2025-03-01', 3),
    (DATE '2024-11-10', 11);
