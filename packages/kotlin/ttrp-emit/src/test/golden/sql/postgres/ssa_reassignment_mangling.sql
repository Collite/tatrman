WITH "accounts" AS (
  SELECT *
  FROM "accounts"
  WHERE "account_id" > 0
),
"accounts_2" AS (
  SELECT *
  FROM "accounts"
  WHERE "status" = 'ACTIVE'
)
SELECT "account_id", "region"
FROM "accounts_2"
