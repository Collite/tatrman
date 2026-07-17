import polars as pl
# --- ttrp prelude (generated; only helpers this program needs) ---
def _ttrp_decimal(df, col, precision, scale):
    return df.with_columns(pl.col(col).cast(pl.Decimal(precision, scale)))
# --- island: dec ---
t = pl.read_csv("/data/t.csv", schema={"amount": pl.Float64})
cast = t.select([pl.col("amount").cast(pl.Decimal(19, 2))])
