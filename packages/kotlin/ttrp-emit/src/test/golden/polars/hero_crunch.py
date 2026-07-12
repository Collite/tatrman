# SPDX-License-Identifier: Apache-2.0
import polars as pl
# --- island: crunch ---
accounts = pl.read_ipc("staging/accounts.arrow")
sales_1 = pl.read_csv("files/sales_2026.csv", schema={"customer": pl.String, "region": pl.String, "amount": pl.Float64})
sales_2 = sales_1.filter(((pl.col("amount") > pl.lit(0)) & pl.col("customer").is_not_null()))
j_1 = accounts.join(sales_2, left_on=["account_id", "branch_code"], right_on=["customer", "region"], how="inner")
sums_1 = j_1.group_by(["region"]).agg([pl.col("amount").sum().alias("total"), pl.col("amount").mean().alias("avg_amt")])
b_1_t = sums_1.filter((pl.col("total") > pl.lit(100000)))
b_1_f = sums_1.filter((~pl.coalesce([(pl.col("total") > pl.lit(100000)), pl.lit(False)])))
b_1_t.write_ipc("out/main_result.arrow", compat_level=pl.CompatLevel.oldest())
print(f"display main_result: out/main_result.arrow")
b_1_f.write_ipc("staging/low.arrow", compat_level=pl.CompatLevel.oldest())
