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
