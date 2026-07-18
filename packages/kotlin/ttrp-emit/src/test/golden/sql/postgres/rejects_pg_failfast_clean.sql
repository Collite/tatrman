WITH "checked_1" AS (
  SELECT "customer", "region", "amount", (CAST("customer" AS integer)) AS "returned_qty"
  FROM "sales_2026"
)
SELECT *
FROM "checked_1"
WHERE "amount" > 0
