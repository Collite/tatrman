#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
#
# RJ-P0 / Stage 0.1.3 — Polars divergence runner (SPIKE; throwaway, kept for reproducibility).
#
# Sweeps the shared corpus (corpus.yaml) and records, per (probe, type):
#   * polars_nonstrict — Series([<probe>]).cast(dtype, strict=False)  -> accept | reject | null
#   * polars_strict    — .cast(dtype, strict=True)                     -> accept | error:<Exc> | null
# into results/polars.csv, plus the Polars version (R-Q11) to results/polars_version.txt and
# pl.Decimal(18,4) handled explicitly (R-Q12).
#
# probe_id / probe_json are derived EXACTLY as in PgDivergenceSpike.kt so merge.py joins on
# identity (never on CSV-escaped raw text). The corpus token "<<NULL>>" means a real None.
#
# Run:  python3 packages/kotlin/ttrp-conform/src/test/spike/polars_probe.py
# (from the repo root, or from this dir — paths resolve relative to this file).

import csv
import json
import pathlib
import sys

import polars as pl
import yaml

HERE = pathlib.Path(__file__).resolve().parent
NULL_TOKEN = "<<NULL>>"

# Corpus type name -> Polars dtype. Decimal/temporal cover R-Q12 and the date/timestamp probes.
DTYPES = {
    "int64": pl.Int64,
    "decimal(18,4)": pl.Decimal(precision=18, scale=4),
    "float64": pl.Float64,
    "date": pl.Date,
    "timestamp": pl.Datetime,
    "bool": pl.Boolean,
}


def probe_json(raw: str) -> str:
    return json.dumps(raw, ensure_ascii=True)


def nonstrict(dtype, raw: str):
    """Return (verdict, value_str). None input -> ('null',''); coerced-to-null -> ('reject','')."""
    value = None if raw == NULL_TOKEN else raw
    s = pl.Series([value], dtype=pl.String)
    try:
        out = s.cast(dtype, strict=False)[0]
    except Exception as e:  # a non-strict cast that still throws is itself a data point
        return f"error:{type(e).__name__}", ""
    if out is None:
        return ("null", "") if value is None else ("reject", "")
    return "accept", str(out)


def strict(dtype, raw: str) -> str:
    value = None if raw == NULL_TOKEN else raw
    s = pl.Series([value], dtype=pl.String)
    try:
        out = s.cast(dtype, strict=True)[0]
    except Exception as e:
        return f"error:{type(e).__name__}"
    if out is None:
        return "null"  # only reachable for a genuine None input (3VL)
    return "accept"


def main() -> int:
    corpus = yaml.safe_load((HERE / "corpus.yaml").read_text(encoding="utf-8"))
    results_dir = HERE / "results"
    results_dir.mkdir(exist_ok=True)

    (results_dir / "polars_version.txt").write_text(pl.__version__ + "\n", encoding="utf-8")

    rows = 0
    with (results_dir / "polars.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["type", "probe_json", "polars_nonstrict", "polars_strict", "polars_value"])
        for type_name, spec in corpus["types"].items():
            dtype = DTYPES[type_name]
            for raw in spec["probes"]:
                raw = NULL_TOKEN if raw is None else str(raw)
                ns_verdict, ns_value = nonstrict(dtype, raw)
                st_verdict = strict(dtype, raw)
                w.writerow([type_name, probe_json(raw), ns_verdict, st_verdict, ns_value])
                rows += 1

    expected = sum(len(s["probes"]) for s in corpus["types"].values())
    assert rows == expected, f"wrote {rows}, expected {expected}"
    print(f"[spike] polars {pl.__version__}; wrote {rows} rows -> {results_dir / 'polars.csv'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
