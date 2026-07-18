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
  SELECT "customer", "region", "amount", (CAST("customer" AS integer)) AS "returned_qty"
  FROM "checked_branch_t"
)
SELECT *
FROM "checked_1"
WHERE "amount" > 0
