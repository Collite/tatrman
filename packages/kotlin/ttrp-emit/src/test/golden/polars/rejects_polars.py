import polars as pl
# --- island: returns_ingest ---
raw_1 = pl.read_csv("files/sales_2026.csv", schema={"customer": pl.String, "region": pl.String, "amount": pl.Float64})
checked_guard = raw_1.with_columns([(pl.col("customer").is_null() | (pl.col("customer").str.strip_chars(" \t\n\r\x0c\x0b").str.contains(r"^[+-]?[0-9]+$") & pl.col("customer").str.strip_chars(" \t\n\r\x0c\x0b").cast(pl.Int64, strict=False).is_not_null())).alias("_ttrp_v1")])
checked_branch_t = checked_guard.filter(pl.col("_ttrp_v1"))
checked_branch_f = checked_guard.filter((~pl.coalesce([pl.col("_ttrp_v1"), pl.lit(False)])))
checked_1 = checked_branch_t.with_columns([pl.col("customer").str.strip_chars(" \t\n\r\x0c\x0b").cast(pl.Int64, strict=False).alias("returned_qty")]).select(pl.all().exclude("^_ttrp_v[0-9]+$"))
checked_reject = checked_branch_f.with_columns([pl.when((~pl.col("_ttrp_v1"))).then(pl.lit("TTRP-RJ-001")).otherwise(None).alias("_ttrp_reject_code"), pl.when((~pl.col("_ttrp_v1"))).then(pl.lit("returned_qty")).otherwise(None).alias("_ttrp_reject_expr")]).select(pl.all().exclude("^_ttrp_v[0-9]+$"))
b_1_t = checked_1.filter((pl.col("amount") > pl.lit(0)))
b_1_f = checked_1.filter((~pl.coalesce([(pl.col("amount") > pl.lit(0)), pl.lit(False)])))
b_1_t.write_ipc("out/clean_result.arrow", compat_level=pl.CompatLevel.oldest())
print(f"display clean_result: out/clean_result.arrow")
b_1_f.write_ipc("staging/bad.arrow", compat_level=pl.CompatLevel.oldest())
checked_reject.write_ipc("staging/rejects.arrow", compat_level=pl.CompatLevel.oldest())
# --- ttrp partition counts (RJ-P5 eighth conform point) ---
import json
_ttrp_counts = {"sites": [
    {"site": "checked", "in": raw_1.height, "processed": checked_1.height, "rejects": checked_reject.height}
]}
with open("counts.json", "w") as _ttrp_f:
    json.dump(_ttrp_counts, _ttrp_f, indent=2)
