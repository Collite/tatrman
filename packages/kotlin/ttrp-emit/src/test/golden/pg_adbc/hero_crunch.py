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
    # temp table: sales_2026 (from CSV)
    _cur.execute("""
CREATE TEMP TABLE "sales_2026" ("customer" TEXT, "region" TEXT, "amount" NUMERIC)
""")
    _t_sales_2026 = _pacsv.read_csv("files/sales_2026.csv", convert_options=_pacsv.ConvertOptions(column_types={"customer": _pa.string(), "region": _pa.string(), "amount": _pa.decimal128(19, 2)}))
    _cur.adbc_ingest("sales_2026", _t_sales_2026, mode="append")
    # output → out/main_result.arrow
    _cur.execute("""
WITH "sales_2" AS (
  SELECT *
  FROM "sales_2026"
  WHERE "amount" > 0 AND "customer" IS NOT NULL
),
"j_1" AS (
  SELECT "accounts"."account_id", "accounts"."branch_code", "accounts"."region", "sales_2"."amount"
  FROM "accounts"
      INNER JOIN "sales_2" ON "accounts"."account_id" = "sales_2"."customer" AND "accounts"."branch_code" = "sales_2"."region"
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
WITH "sales_2" AS (
  SELECT *
  FROM "sales_2026"
  WHERE "amount" > 0 AND "customer" IS NOT NULL
),
"j_1" AS (
  SELECT "accounts"."account_id", "accounts"."branch_code", "accounts"."region", "sales_2"."amount"
  FROM "accounts"
      INNER JOIN "sales_2" ON "accounts"."account_id" = "sales_2"."customer" AND "accounts"."branch_code" = "sales_2"."region"
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
