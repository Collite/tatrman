WITH "cleaned" AS (
  SELECT *
  FROM "erp"."accounts"
  WHERE "status" = 'ACTIVE' AND "region" IS NOT NULL
)
SELECT "account_id", "region"
FROM "cleaned"
