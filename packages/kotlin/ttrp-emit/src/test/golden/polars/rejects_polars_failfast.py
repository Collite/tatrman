import polars as pl
# --- island: returns_ingest ---
raw_1 = pl.read_csv("files/sales_2026.csv", schema={"customer": pl.String, "region": pl.String, "amount": pl.Float64})
checked_1 = raw_1.with_columns([pl.col("customer").cast(pl.Int64).alias("returned_qty")])
b_1_t = checked_1.filter((pl.col("amount") > pl.lit(0)))
b_1_f = checked_1.filter((~pl.coalesce([(pl.col("amount") > pl.lit(0)), pl.lit(False)])))
b_1_t.write_ipc("out/clean_result.arrow", compat_level=pl.CompatLevel.oldest())
print(f"display clean_result: out/clean_result.arrow")
b_1_f.write_ipc("staging/bad.arrow", compat_level=pl.CompatLevel.oldest())
