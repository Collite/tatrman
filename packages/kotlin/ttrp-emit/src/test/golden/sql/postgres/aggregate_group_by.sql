SELECT "region", SUM("amount") AS "total", AVG("amount") AS "avg_amt", COUNT(DISTINCT "customer_id") AS "n_cust"
FROM "sales"
GROUP BY "region"
