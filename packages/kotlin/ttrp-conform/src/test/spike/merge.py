#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
#
# RJ-P0 / Stage 0.1.5 + 0.1.7 — Merge the per-engine probe CSVs into the divergence table and
# assert the two sanity pins.
#
# Inputs  (results/):  pg.csv, polars.csv, mssql.csv  (mssql may be the SKIPPED-no-env stub)
# Outputs (results/):  divergence.csv   — one row per (type, probe), all engine verdicts + flag
#                      divergence.md    — human pivot, per-type tables + a summary header
#
# Join key is (type, probe_json) — identical across runners by construction (json.dumps /
# jsonEncode). Rows are emitted in corpus.yaml order for readability.
#
# Sanity pins (0.1.7) — a violation aborts with AssertionError, to be investigated before 0.2:
#   PIN-1  NULL input is accept-to-NULL on every engine/form (3VL, design R-A3).
#   PIN-2  empty string != NULL on PG text->int (guards against a driver coercing "" to NULL).
#
# Run:  python3 packages/kotlin/ttrp-conform/src/test/spike/merge.py

import csv
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
RESULTS = HERE / "results"
NULL_TOKEN = "<<NULL>>"


def load(name):
    with (RESULTS / name).open(encoding="utf-8") as fh:
        return {(r["type"], r["probe_json"]): r for r in csv.DictReader(fh)}


def accept_state(verdict: str) -> str:
    """Tri-state acceptance: Y accepted, N rejected/errored, '-' null-in (3VL, not comparable)."""
    if verdict == "accept":
        return "Y"
    if verdict == "null":
        return "-"
    return "N"  # reject | error:*


def main() -> int:
    corpus = __import__("yaml").safe_load((HERE / "corpus.yaml").read_text(encoding="utf-8"))
    pg = load("pg.csv")
    po = load("polars.csv")

    mssql_rows = load("mssql.csv")
    mssql_present = ("SKIPPED-no-env", "") not in mssql_rows and "SKIPPED-no-env" not in {
        k[0] for k in mssql_rows
    }
    polars_version = (RESULTS / "polars_version.txt").read_text(encoding="utf-8").strip()

    out_rows = []
    for type_name, spec in corpus["types"].items():
        for raw in spec["probes"]:
            raw = NULL_TOKEN if raw is None else str(raw)
            key = (type_name, json.dumps(raw, ensure_ascii=True))
            p = pg.get(key, {})
            l = po.get(key, {})
            pg_cast = p.get("pg_cast", "MISSING")
            pg_valid = p.get("pg_input_is_valid", "MISSING")
            pl_ns = l.get("polars_nonstrict", "MISSING")
            pl_st = l.get("polars_strict", "MISSING")
            mssql = mssql_rows.get(key, {}).get("mssql_try_cast", "unknown") if mssql_present else "unknown"

            # DIVERGENT: among the comparable acceptance signals (exclude 3VL nulls and the
            # unknown MSSQL column), do the engines disagree on accept-vs-not?
            signals = {accept_state(pg_cast), accept_state(pg_valid), accept_state(pl_ns)}
            comparable = signals - {"-"}
            divergent = "yes" if {"Y", "N"} <= comparable else ""

            out_rows.append(
                {
                    "type": type_name,
                    "probe_json": key[1],
                    "pg_cast": pg_cast,
                    "pg_input_is_valid": pg_valid,
                    "polars_nonstrict": pl_ns,
                    "polars_strict": pl_st,
                    "mssql_try_cast": mssql,
                    "accept_pg": accept_state(pg_cast),
                    "accept_polars": accept_state(pl_ns),
                    "DIVERGENT": divergent,
                }
            )

    # ---- sanity pins -------------------------------------------------------
    null_json = json.dumps(NULL_TOKEN, ensure_ascii=True)
    for r in out_rows:
        if r["probe_json"] == null_json:
            assert r["pg_cast"] == "null", f"PIN-1 pg_cast on NULL/{r['type']}: {r['pg_cast']}"
            assert r["pg_input_is_valid"] == "null", f"PIN-1 pg_is_valid on NULL/{r['type']}"
            assert r["polars_nonstrict"] == "null", f"PIN-1 polars on NULL/{r['type']}"
    empty = next(r for r in out_rows if r["type"] == "int64" and r["probe_json"] == '""')
    assert empty["pg_cast"] != "null", f"PIN-2 empty '' coerced to NULL on PG int: {empty['pg_cast']}"
    assert empty["pg_cast"].startswith("error"), f"PIN-2 empty '' unexpected on PG int: {empty['pg_cast']}"

    # ---- divergence.csv ----------------------------------------------------
    cols = [
        "type", "probe_json", "pg_cast", "pg_input_is_valid",
        "polars_nonstrict", "polars_strict", "mssql_try_cast",
        "accept_pg", "accept_polars", "DIVERGENT",
    ]
    with (RESULTS / "divergence.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        w.writerows(out_rows)

    # ---- divergence.md -----------------------------------------------------
    total = len(out_rows)
    n_div = sum(1 for r in out_rows if r["DIVERGENT"])
    per_type = {}
    for r in out_rows:
        d = per_type.setdefault(r["type"], [0, 0])
        d[0] += 1
        d[1] += 1 if r["DIVERGENT"] else 0

    def cell(v):
        return v.replace("|", "\\|")

    lines = []
    lines.append("# RJ-P0 divergence table (R-Q9)\n")
    lines.append(
        "> Generated by `merge.py` from the live PG16 + Polars runs. Engine forms: **pg_cast** = "
        "`CAST(text AS T)`; **pg_valid** = `pg_input_is_valid`; **pl(ns)** = "
        "`cast(strict=False)` (null-coerce); **pl(st)** = `cast(strict=True)` (raise); "
        "**mssql** = `TRY_CAST` (parked — no env).\n"
    )
    lines.append(
        f"- Probes: **{total}** across {len(per_type)} types  •  divergent rows: **{n_div}**  "
        f"•  Polars **{polars_version}**  •  MSSQL: **{'present' if mssql_present else 'SKIPPED-no-env'}**\n"
    )
    lines.append("- `DIVERGENT` = the comparable engines disagree accept-vs-reject (3VL nulls excluded).\n")
    lines.append("\n| type | divergent / probes |\n|---|---|")
    for t, (n, d) in per_type.items():
        lines.append(f"| {cell(t)} | {d} / {n} |")
    lines.append("")

    for type_name in per_type:
        lines.append(f"\n## {type_name}\n")
        lines.append("| probe | pg_cast | pg_valid | pl(ns) | pl(st) | mssql | ⚑ |")
        lines.append("|---|---|---|---|---|---|---|")
        for r in out_rows:
            if r["type"] != type_name:
                continue
            flag = "**yes**" if r["DIVERGENT"] else ""
            lines.append(
                f"| `{cell(r['probe_json'])}` | {cell(r['pg_cast'])} | {cell(r['pg_input_is_valid'])} "
                f"| {cell(r['polars_nonstrict'])} | {cell(r['polars_strict'])} | {cell(r['mssql_try_cast'])} | {flag} |"
            )
    (RESULTS / "divergence.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"[merge] {total} probes, {n_div} divergent  ->  results/divergence.{{csv,md}}")
    print("[merge] sanity pins PIN-1 (NULL 3VL) + PIN-2 (empty != NULL) hold.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
