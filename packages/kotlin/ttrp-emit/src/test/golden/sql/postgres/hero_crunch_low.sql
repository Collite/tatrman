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
