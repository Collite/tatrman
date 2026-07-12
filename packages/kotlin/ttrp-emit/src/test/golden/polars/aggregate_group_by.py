# SPDX-License-Identifier: Apache-2.0
import polars as pl
# --- island: grp ---
sales = pl.read_ipc("staging/sales.arrow")
sums = sales.group_by(["region"]).agg([pl.col("amount").sum().alias("total"), pl.col("amount").mean().alias("avg_amt"), pl.col("customer").n_unique().alias("n_cust")])
