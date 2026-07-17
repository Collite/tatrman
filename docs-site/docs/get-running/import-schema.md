<!-- SPDX-License-Identifier: Apache-2.0 -->
# `ttr import-schema` — bootstrap a model from your database

> **Status: S3 (the `db` mirror) is built; the `er` first cut + review checklist land in S4.**
> This page is the Get-running how-to stub; S6 fills in the worked walkthrough.

`ttr import-schema` points at an existing database, introspects it, and writes TTR-M model
documents you review and commit. You get back a **`db` mirror** — a faithful physical picture of
your tables, columns, keys and declared foreign keys — and (from S4) an **`er` first cut** with
relations graded by evidence, plus a review checklist of every judgement the tool made.

## Run it

```bash
export DB_PW=…                       # never pass the password on the command line
ttr import-schema \
  --jdbc-url "jdbc:postgresql://host:5432/mydb" \
  --user analyst --password-env DB_PW \
  --dialect postgresql \
  --package myestate \
  --profile mssql-default \
  --out ./myestate
```

This writes, into `./myestate/`:

- `db.<schema>.ttrm` — one document per database schema (`model db schema <schema>`).
- `modeler.toml` — the package manifest.
- `conventions.yaml` — materialised from the chosen profile on the first run (see below).

Parse it back any time with the toolchain; the emitted model is canonical TTR-M.

## Flags

| Flag | Meaning |
|---|---|
| `--jdbc-url` | JDBC connection URL (required). |
| `--dialect` | `mssql` or `postgresql` (required). |
| `--package` | The TTR model package. **Never inferred** — you name it. |
| `--out` | Output model-package directory (default: current dir). |
| `--user` / `--password-env` | DB user, and the **env var** holding the password (secrets never ride argv). |
| `--conventions` | Path to a `conventions.yaml` (overrides the package file and any profile). |
| `--profile` | Starter profile: `mssql-default` (generic) or `czech-erp` (Czech ERP + `Ciselnik` codebooks). |

## Conventions & profiles

The `conventions.yaml` file is, together with the database, the **only** input to the import —
same database + same conventions ⇒ **same bytes**, every run, on every machine. It holds the
naming heuristics the `er` derivation uses, plus the probe rule and budgets.

Resolution order: `--conventions <path>` → the package's `conventions.yaml` → `--profile <name>`
→ the dialect default. The first run **materialises** the chosen profile into the package as
`conventions.yaml`; from the second run your estate's truth is fully pinned and versioned in git.

## Determinism

The tool is deterministic by construction. Objects are emitted in sorted order with no timestamps;
data probes use exact counts and keyed sampling (never storage-dependent `TABLESAMPLE`), so a
re-run against an unchanged database reproduces the output byte-for-byte. That is what makes the
re-run story (S4) a clean git diff rather than a merge conflict.

## Identifiers

TTR-M identifiers allow Latin letters (including Czech diacritics), so real Czech names survive
verbatim. An identifier that is not a legal TTR-M name (a space, punctuation, a leading digit) is
deterministically rewritten to a legal one, and the original is recorded in the review checklist —
never lost. If two different source names would collide after rewriting, the import stops with
`TTRP-IMP-001` rather than silently guessing.
