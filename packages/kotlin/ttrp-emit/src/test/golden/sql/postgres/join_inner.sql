SELECT "accounts"."account_id", "accounts"."branch_code", "accounts"."region", "sales"."amount"
FROM "erp"."accounts"
    INNER JOIN "files"."sales" ON "accounts"."account_id" = "sales"."customer" AND "accounts"."branch_code" = "sales"."region"
