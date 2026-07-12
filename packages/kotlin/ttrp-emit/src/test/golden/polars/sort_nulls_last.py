# SPDX-License-Identifier: Apache-2.0
import polars as pl
# --- island: srt ---
sums = pl.read_ipc("staging/sums.arrow")
ranked = sums.sort(by=["total", "region"], descending=[True, False], nulls_last=True)
