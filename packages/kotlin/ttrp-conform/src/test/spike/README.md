# RJ-P0 divergence spike (throwaway, committed for reproducibility)

This folder is the RJ-P0 / Stage 0.1 harness that answers **R-Q9** (do the native text→T
acceptance domains diverge, from each other and from a canonical candidate?), **R-Q11/Q12**
(Polars version + Decimal behavior), and stubs **R-Q10** (MSSQL — parked, no local env).

It is **spike code**: kept in-repo so the numbers can be regenerated, but excluded from the
default build. Nothing here is production. Ground truth for *why* is
`project/tatrman/features/ttr-p/implementation/rejects/` (plan + contracts + design).

## Layout

| file | what |
|---|---|
| `corpus.yaml` | the single-source-of-truth probe corpus (196 probes × 6 target types) |
| `../kotlin/.../spike/PgDivergenceSpike.kt` | PG runner — Kotest spec tagged `Spike`, JDBC → live ttrp-pg |
| `polars_probe.py` | Polars runner — `cast(strict=False/True)` sweep |
| `merge.py` | joins the per-engine CSVs → `results/divergence.{csv,md}` + asserts the sanity pins |
| `results/` | committed outputs: `pg.csv`, `polars.csv`, `mssql.csv` (stub), `divergence.{csv,md}`, `polars_version.txt` |

The runners join on `(type, probe_json)` where `probe_json = json.dumps(raw, ensure_ascii=True)`
(the Kotlin `jsonEncode` matches it byte-for-byte), so the key is stable and not
CSV-escape-sensitive. The corpus token `<<NULL>>` denotes a real SQL NULL / Python `None`.

## Pre-flight

- **PG16** reachable at `localhost:55432` (Rancher-Desktop `ttrp-pg`, user `postgres` / pw `ttrp`).
  Override via `-Dspike.pg.url/-Dspike.pg.user/-Dspike.pg.password`.
- **Polars ≥ 1** on `python3` (`python3 -c "import polars, yaml"`). Recorded run: **1.42.1**.
- **MSSQL 2022** optional — absent here, so `results/mssql.csv` is the `SKIPPED-no-env` stub and
  the `mssql_try_cast` column reads `unknown`. Re-running with an env fills it (R-Q10 / RJ-P3.3).

## Reproduce

```bash
# from the repo root
./gradlew :packages:kotlin:ttrp-conform:test --tests '*Spike*' -PincludeSpike=true   # -> results/pg.csv
python3 packages/kotlin/ttrp-conform/src/test/spike/polars_probe.py                  # -> results/polars.csv
python3 packages/kotlin/ttrp-conform/src/test/spike/merge.py                         # -> results/divergence.{csv,md}
```

`merge.py` aborts if either sanity pin fails:

- **PIN-1** — NULL input is accept-to-NULL on every engine/form (3VL, design R-A3).
- **PIN-2** — empty string `""` is **not** NULL on PG `text→int` (guards against a driver lying).

## Headline findings (full table: `results/divergence.md`)

- **64 / 196** probes diverge across the comparable engine forms.
- **int64/decimal/float** — PG trims ASCII whitespace and accepts oddities (`0x1F`, `0o17`,
  `1_234`, `NaN` for numeric); Polars `strict=False` trims nothing (**narrower**). Unicode
  whitespace is never trimmed by PG (ASCII-only).
- **float64** — the one place Polars is **wider**: `1e309`→`inf`, `1e-400`→`0` are accepted by
  Polars but rejected out-of-range by PG.
- **date/timestamp** — PG accepts locale, keyword (`today`, `epoch`, `now`, `infinity` — all
  **non-deterministic**), compact and datetime forms; Polars casts ISO-8601 only (and for
  `timestamp` only the `T`-separated form). String→temporal cast is **deprecated in Polars**
  (removed in 2.0) — the emitter must use `str.to_date` / `str.to_datetime`.
- **bool** — Polars `cast(String→Boolean)` **errors on every value**: there is no native Polars
  string→bool path, so RJ-P4 needs a bespoke parse idiom. PG accepts a wide token set and even
  the ambiguous prefix `tru`.

These feed the canonical-domain choices in Stage 0.2 (`../../.../rejects/spike-report.md` +
`ttrp/validity/*.yaml`).
