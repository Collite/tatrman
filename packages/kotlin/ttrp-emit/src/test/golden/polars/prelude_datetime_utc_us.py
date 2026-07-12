# SPDX-License-Identifier: Apache-2.0
import polars as pl
# --- ttrp prelude (generated; only helpers this program needs) ---
def _ttrp_dt_utc_us(df, cols):
    return df.with_columns([pl.col(c).cast(pl.Datetime("us", "UTC")) for c in cols])
# --- island: dt ---
t = pl.read_csv("/data/t.csv", schema={"ts": pl.String})
cast = t.select([pl.col("ts").cast(pl.Datetime("us", "UTC"))])
