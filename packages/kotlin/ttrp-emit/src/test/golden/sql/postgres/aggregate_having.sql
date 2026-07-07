WITH "sums" AS (
  SELECT "region", SUM("amount") AS "total"
  FROM "sales"
  GROUP BY "region"
)
SELECT *
FROM "sums"
WHERE "total" > 100000
