WITH "checked_guard" AS (
  SELECT "customer", "region", "amount", ("customer" IS NULL OR (btrim("customer", E' \t\n\r\f\x0B') ~ '^[+-]?[0-9]+$' AND btrim("customer", E' \t\n\r\f\x0B')::numeric BETWEEN -9223372036854775808 AND 9223372036854775807)) AS "_ttrp_v1", ("amount" IS NULL OR ("amount") <> 0) AS "_ttrp_v2"
  FROM "sales_2026"
),
"checked_branch_f" AS (
  SELECT *
  FROM "checked_guard"
  WHERE NOT COALESCE("_ttrp_v1" AND "_ttrp_v2", FALSE)
)
SELECT "customer", "region", "amount", CASE WHEN NOT "_ttrp_v1" THEN 'TTRP-RJ-001' WHEN NOT "_ttrp_v2" THEN 'TTRP-RJ-007' ELSE NULL END AS "_ttrp_reject_code", CASE WHEN NOT "_ttrp_v1" THEN 'qty' WHEN NOT "_ttrp_v2" THEN 'share' ELSE NULL END AS "_ttrp_reject_expr"
FROM "checked_branch_f"
