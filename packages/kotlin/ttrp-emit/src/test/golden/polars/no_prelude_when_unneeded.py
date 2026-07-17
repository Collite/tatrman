import polars as pl
# --- island: plain ---
t = pl.read_csv("/data/t.csv", schema={"x": pl.Int64})
kept = t.filter((pl.col("x") > pl.lit(0)))
