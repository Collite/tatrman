import polars as pl
# --- island: srt ---
sums = pl.read_ipc("staging/sums.arrow")
ranked = sums.sort(by=["total", "region"], descending=[True, False], nulls_last=True)
