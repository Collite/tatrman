# Feature: Python parser (`ttr-parser` for Python)

A **Python** parser, walker **and semantics layer** for TTR, generated from the
same canonical `packages/grammar/src/TTR.g4` and producing a typed AST isomorphic
to the TS and Kotlin parsers. It lets developers building **outside** the
ai-platform runtime consume models from the central place — read `.ttr` files,
get a typed AST, **and resolve references** the same way the platform does, all
in Python.

It is a **third target** alongside the existing TS parser (`packages/parser`,
`antlr-ng`) and Kotlin parser (`packages/kotlin/ttr-parser`, ANTLR Gradle
plugin). The discipline that makes this cheap is already in place: `TTR.g4` is
target-neutral (no `@members`, no `{…}` actions, no semantic predicates), so
ANTLR's Python3 target generates a working parser with **zero grammar changes**.

## Status

Planning. Artefacts in this folder:

| Artefact | File |
|---|---|
| Architecture (solution shape, generation flow, AST mapping, distribution) | [`architecture.md`](architecture.md) |
| Contracts (Python public API, AST shapes, conformance dump, CI) | [`contracts.md`](contracts.md) |
| Phased implementation plan (deliverables, DoD, pre-flight) | [`plan.md`](plan.md) |

Task lists are **not** generated yet — they are the next step after this plan is
approved (per the planning skill: architecture + contracts + plan must exist
first). The Kotlin equivalent of this work is the grammar-master migration
([`docs/grammar-master/`](../../grammar-master/plan.md)); read its
`architecture.md`/`contracts.md` first — this feature deliberately mirrors them.

## Scope (locked with the user)

- **Read model files only.** Parse `.ttr` → typed AST. **No graphs:** `.ttrg`
  files and `graph { … }` blocks are out of scope (the consumer explicitly only
  needs the models, not the graphical layout). `parse_directory` excludes
  `.ttrg`, matching the Kotlin loader.
- **Parser + walker + semantics.** This feature covers the analogue of **both**
  Kotlin **Phase 1** (parser + walker + model + loader) **and Phase 2**
  (`ttr-semantics`: qname, symbol table, six-step resolver, package inference,
  package graph, the portable validator subset, and the stock CNC vocab) — folded
  into **one** Python package (D8). Consumers need reference resolution, not just
  the raw AST (OQ3).
- **Read-only.** A `ttr-writer` Python twin (model → text) is **deferred** — the
  stated need is consumption, not round-tripping (OQ2). Captured in `plan.md`
  "Deferred".

## Decisions

OQ1/OQ2/OQ3 are now **resolved** (confirmed with the user): the language will be
**public** (→ PyPI), the package is **read-only** (no writer), and consumers
**need resolution** (semantics in scope, D8).

| # | Decision | Resolution |
|---|---|---|
| D1 | ANTLR tooling for the Python target | **Reference ANTLR tool (`antlr4` jar) 4.13.2, `-Dlanguage=Python3`; runtime `antlr4-python3-runtime==4.13.2`.** Pinned to the same ANTLR major.minor as the Kotlin runtime (`org.antlr:antlr4-runtime:4.13.2`) so generated code and parse behaviour match. **Not `antlr-ng`** — that is a TypeScript-only reimplementation; the Python path mirrors the Kotlin path, not the TS one. |
| D2 | Where the package lives | **`packages/python/ttr-parser/`** — sits next to `packages/kotlin/ttr-parser/`. Dist name `ttr-parser`, import name `ttr_parser`. Reads `../../grammar/src/TTR.g4` directly; **no vendoring**. |
| D3 | Distribution | **CI-built wheels published to public PyPI** (OQ1 resolved). Java/ANTLR present in CI only, so consumers never need a JVM. Tag-driven like Kotlin: `python/v<x.y.z>` → `publish-python.yml`. |
| D4 | Generated-parser tracking | **`_generated/` is gitignored**, regenerated from `TTR.g4` at build time (preserves the repo invariant; CI builds the wheel with Java). The published wheel bundles the generated parser, so installers get pure Python. |
| D5 | AST naming | **Python classes mirror the Kotlin names** (`ModelDef`, `TableDef`, …) with **snake_case fields** (`primary_key`, `value_labels`). The conformance dump keys off the lowercased TTR keyword and TTR **surface** property names, so class/field naming is decoupled from the diff. Add a **Python column** to [`AST-NAMING.md`](../../grammar-master/AST-NAMING.md). |
| D6 | Conformance | Python emits the **same normalised JSON dumps** (grammar-master `contracts.md` §5 parser dump **and** §5.1 semantics dump) and is diffed against the committed TS golden baselines (`tests/conformance/out-ts/`, `out-ts-sem/`). New `py-dump` + `py-sem-dump` jobs join `conformance.yml`. The same green/red gate that pins TS↔Kotlin now pins TS↔Python for **both** AST shape and resolution. |
| D7 | No SNAPSHOTs / pre-release churn | Mirror Kotlin D6: cut real `0.x.y` versions; iterate locally with editable installs (`pip install -e`) before tagging. |
| D8 | One package for parser **and** semantics | **A single distribution `ttr-parser`** carries both the parser/walker (`ttr_parser`) and the semantics layer (`ttr_parser.semantics`: resolver, symbol table, package inference, package graph, validator, stock vocab). This **diverges from Kotlin's two artifacts** (`ttr-parser` + `ttr-semantics`) deliberately: Python consumers want resolution out of the box with one `pip install` and one dependency (OQ3, "in the same package"). The stock CNC vocab ships as package data. |

## Why this is low-risk

The hard, drift-prone parts — the **walker** (16 def kinds, 8 `PropertyValue`
variants, triple-string dedent, tagged blocks, `Reference` splitting,
`SourceLocation` indexing) **and the resolver** (the six-step chain + stock
auto-imports) — already exist twice (TS + Kotlin) and are pinned by the
conformance harness. The Python ports are a third implementation held to the
**same** fixtures from day one (AST dump *and* semantics dump), so they cannot
silently rot when the grammar bumps (already at `@grammar-version: 2.2`). The
main new cost is a third surface to keep in sync on grammar changes and a Python
column in the rename map.
