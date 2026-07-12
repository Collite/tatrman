# SPDX-License-Identifier: Apache-2.0
import polars as pl
# --- island: prep ---
sales = pl.read_csv("/data/files/sales.csv", schema={"customer": pl.String, "region": pl.String, "amount": pl.Float64})
sales_2 = sales.filter(((pl.col("amount") > pl.lit(0)) & pl.col("customer").is_not_null()))
kept = sales_2.select([pl.col("region"), pl.col("amount")])
