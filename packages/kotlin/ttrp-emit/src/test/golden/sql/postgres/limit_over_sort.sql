WITH "ranked" AS (
  SELECT *
  FROM "agg"."sums"
  ORDER BY "total" DESC NULLS LAST
)
SELECT *
FROM "ranked"
FETCH NEXT 10 ROWS ONLY
