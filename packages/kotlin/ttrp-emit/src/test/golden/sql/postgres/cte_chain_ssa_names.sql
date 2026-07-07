WITH "cleaned" AS (
  SELECT *
  FROM "accounts"
  WHERE "status" = 'ACTIVE' AND "region" IS NOT NULL
)
SELECT "account_id", "region"
FROM "cleaned"
