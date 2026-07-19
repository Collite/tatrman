import os
import adbc_driver_postgresql.dbapi as _dbapi
import pyarrow as _pa
import pyarrow.csv as _pacsv
import pyarrow.ipc as _paipc

def _write_ipc(_table, _path):
    with _pa.OSFile(_path, "wb") as _f:
        with _paipc.new_file(_f, _table.schema) as _w:
            _w.write_table(_table)

_conn = _dbapi.connect(os.environ["TTR_CONN_ERP_PG"])
try:
    _cur = _conn.cursor()
    # temp table: accounts (from SQL)
    _cur.execute("""
CREATE TEMP TABLE "accounts" AS
SELECT account_id, branch_code, region
FROM erp.accounts
WHERE status = 'ACTIVE'
""")
    # temp table: sales_2026 (typed CSV → adbc_ingest COPY, temporary)
    _t_sales_2026 = _pacsv.read_csv("files/sales_2026.csv", convert_options=_pacsv.ConvertOptions(column_types={"customer": _pa.string(), "region": _pa.string(), "amount": _pa.float64()}, strings_can_be_null=True))
    _cur.adbc_ingest("sales_2026", _t_sales_2026, mode="create", temporary=True)
    # output → out/main_result.arrow
    _cur.execute("""
WITH "checked_guard" AS (
  SELECT "customer", "region", "amount", ("customer" IS NULL OR (btrim("customer", E' \t\n\r\f\x0B') ~ '^[+-]?[0-9]+$' AND btrim("customer", E' \t\n\r\f\x0B')::numeric BETWEEN -9223372036854775808 AND 9223372036854775807)) AS "_ttrp_v1"
  FROM "sales_2026"
),
"checked_branch_t" AS (
  SELECT *
  FROM "checked_guard"
  WHERE "_ttrp_v1"
),
"checked_1" AS (
  SELECT "customer", "region", "amount", (CAST("customer" AS bigint)) AS "customer_id"
  FROM "checked_branch_t"
),
"sales_1" AS (
  SELECT *
  FROM "checked_1"
  WHERE "amount" > 0 AND "customer" IS NOT NULL
),
"j_1" AS (
  SELECT "accounts"."account_id", "accounts"."branch_code", "accounts"."region", "sales_1"."amount", "sales_1"."customer_id"
  FROM "accounts"
      INNER JOIN "sales_1" ON "accounts"."account_id" = "sales_1"."customer" AND "accounts"."branch_code" = "sales_1"."region"
),
"sums_1" AS (
  SELECT "region", SUM("amount") AS "total", AVG("amount") AS "avg_amt"
  FROM "j_1"
  GROUP BY "region"
)
SELECT *
FROM "sums_1"
WHERE "total" > 100000
""")
    _write_ipc(_cur.fetch_arrow_table(), "out/main_result.arrow")
    # output → staging/low.arrow
    _cur.execute("""
WITH "checked_guard" AS (
  SELECT "customer", "region", "amount", ("customer" IS NULL OR (btrim("customer", E' \t\n\r\f\x0B') ~ '^[+-]?[0-9]+$' AND btrim("customer", E' \t\n\r\f\x0B')::numeric BETWEEN -9223372036854775808 AND 9223372036854775807)) AS "_ttrp_v1"
  FROM "sales_2026"
),
"checked_branch_t" AS (
  SELECT *
  FROM "checked_guard"
  WHERE "_ttrp_v1"
),
"checked_1" AS (
  SELECT "customer", "region", "amount", (CAST("customer" AS bigint)) AS "customer_id"
  FROM "checked_branch_t"
),
"sales_1" AS (
  SELECT *
  FROM "checked_1"
  WHERE "amount" > 0 AND "customer" IS NOT NULL
),
"j_1" AS (
  SELECT "accounts"."account_id", "accounts"."branch_code", "accounts"."region", "sales_1"."amount", "sales_1"."customer_id"
  FROM "accounts"
      INNER JOIN "sales_1" ON "accounts"."account_id" = "sales_1"."customer" AND "accounts"."branch_code" = "sales_1"."region"
),
"sums_1" AS (
  SELECT "region", SUM("amount") AS "total", AVG("amount") AS "avg_amt"
  FROM "j_1"
  GROUP BY "region"
)
SELECT *
FROM "sums_1"
WHERE NOT COALESCE("total" > 100000, FALSE)
""")
    _write_ipc(_cur.fetch_arrow_table(), "staging/low.arrow")
    _conn.commit()
finally:
    _conn.close()
