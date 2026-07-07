SELECT "accounts"."account_id", "accounts"."region", "sales"."amount"
FROM "erp"."accounts"
    LEFT JOIN "files"."sales" ON "accounts"."account_id" = "sales"."customer"
