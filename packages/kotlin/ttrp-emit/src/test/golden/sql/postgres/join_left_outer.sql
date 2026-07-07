SELECT "accounts"."account_id", "accounts"."region", "sales"."amount"
FROM "accounts"
    LEFT JOIN "sales" ON "accounts"."account_id" = "sales"."customer"
