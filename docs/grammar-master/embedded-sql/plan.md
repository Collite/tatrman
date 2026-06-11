# Embedded SQL — phased plan

**Status:** Plan v1, 2026-06-11. Read [`architecture.md`](architecture.md) and
[`contracts.md`](contracts.md) first — they are normative, as is
[`../embedded-language-blocks.md`](../embedded-language-blocks.md) (**DESIGN**).
Task lists live in [`tasks/INDEX.md`](tasks/INDEX.md).

## Goal & headline DoD

SQL in `query.sourceText` / `view.definitionSql` is syntax-highlighted in all
hosts, and (on desktop) its tables/columns resolve against the TTR `db` schema
with unknown-table/column diagnostics, hover, and go-to-definition. MS SQL and
PostgreSQL work from the first semantics release; DuckDB follows. Imperfect
parse/lex is acceptable; a broken parse must still highlight.

## Decisions (from the design discussion — see DESIGN for rationale)

| # | Decision |
|---|---|
| E1 | Carrier = `"""<tag>` tagged triple-string; untagged triple = raw string. |
| E2 | `TaggedBlockValue` is a **new sealed `PropertyValue` variant** — accepted breaking change. |
| E3 | SQL parser = **ANTLR grammars-v4** (`tsql`, `postgresql`; DuckDB = postgres-derived), `antlr4ng` runtime, **vendored-pinned + lazily patched** (not hard-forked). |
| E4 | **Lexer-first.** Highlighting needs only the lexer; semantics is a later, best-effort, desktop-only phase. |
| E5 | Multi-dialect normalised via `SqlRefModel` extraction, **not** full-AST unification. |
| E6 | Browser = lexer-only (parser bundle size); desktop = full. |
| E7 | Name mapping: SQL `(database, schema)` ⇄ TTR `namespace`, bijective, project-wide in `modeler.toml`. |
| E8 | `sql-parser-cst` rejected (no T-SQL); `node-sql-parser` is the documented fallback; Calcite rejected. |

## Phase map

| Phase | Theme | Gate in | Hosts |
|---|---|---|---|
| **0** | **Spikes & evaluation (GATING)** | — | n/a |
| **1** | Tagged-block grammar + value contract | Phase 0 not required (text-only) | all |
| **2** | Lexer-first highlighting | **Phase 0 gate** + Phase 1 | all |
| **3** | Best-effort semantics | Phase 2 | desktop |
| **4** | IDE features (hover/def/refs/completion/rename) | Phase 3 | desktop |

Phase 1 is independent of the SQL grammars and **may proceed in parallel with
Phase 0**. Phases 2+ **must not start until the Phase 0 gate is green.**

---

## Phase 0 — Spikes & evaluation (GATING)

**Repo:** modeler (scratch package `packages/sql-spike`, deleted after).
**Pre-flight:** none. **Purpose:** de-risk the ANTLR-SQL approach before any
production wiring. Each stage has a **gate**; all three gates must be green to
open Phases 2+.

| Stage | Deliverable | Gate (required outcome to continue) |
|---|---|---|
| **S0.1** | `antlr-ng` generates `tsql` + `postgresql` lexers/parsers | Both generate TS that compiles & instantiates under `antlr4ng`; case-insensitive keywords work **or** a concrete fallback is chosen and costed. |
| **S0.2** | Real-query parse + span-fidelity evaluation | A representative corpus of *actual* project T-SQL + Postgres lexes 100% and parses ≥ an agreed threshold; identifier tokens carry tight line/col/offset spans; failures catalogued as the lazy-patch backlog. |
| **S0.3** | Bundle-size + host-split evaluation | Generated **lexer** bundle fits the browser-Worker budget; **parser** sizes measured; confirms (or revises) lexer-only-in-browser. |

**Phase 0 DoD:** a short `spike-report.md` records, per gate: pass/fail, measured
numbers (parse %, span samples, bundle KB), the chosen case-insensitivity
approach, and the lazy-patch backlog. **If S0.1 fails outright**, fall back to
`node-sql-parser` (E8) and re-plan Phases 2–4 against its AST — do not proceed on
ANTLR.

---

## Phase 1 — Tagged-block grammar + value contract

**Repo:** modeler. **Pre-flight:** none (text-only; no SQL grammar).
**Deliverables:** the `"""<tag>` carrier parses; `TaggedBlockValue` is produced
in TS and Kotlin with byte-identical values; `ttr-writer` round-trips; a new
`org.tatrman:*` version is published. TDD — conformance fixtures first.

**DoD:**
- C1–C11 (DESIGN §9) green in `@modeler/conformance` (TS ⇄ Kotlin).
- Untagged triple-strings and all existing fixtures unchanged (no regressions).
- `language:` inferred from tag; mismatch + unknown-tag + deprecation diagnostics
  emitted (DESIGN §5/§6).
- `ttr-writer` round-trip spec green; new version published; CHANGELOG noted.
- ai-platform consumer impact documented (DESIGN §10) — handled in a separate
  ai-platform PR, **out of scope here** beyond the version bump note.

---

## Phase 2 — Lexer-first highlighting (post-gate)

**Repo:** modeler. **Pre-flight:** Phase 0 gate green; Phase 1 merged.
**Deliverables:** vendored-pinned `tsql` + `postgresql` lexers; a dialect-keyed
SQL lexer service over `TaggedBlockValue.value`; the §8 source map; embedded SQL
semantic tokens merged into the LSP response; VS Code + Designer show coloured
SQL. **DoD:** SQL highlights in VS Code and the Designer; a deliberately broken
query still highlights; semantic-token positions verified against the source map
in tests. *(Task lists authored once the Phase 0 gate is green — see INDEX.)*

## Phase 3 — Best-effort semantics (desktop)

**Repo:** modeler. **Pre-flight:** Phase 2 merged; `modeler.toml` SQL config
schema agreed (contracts §5). **Deliverables:** per-dialect `SqlRefModel`
adapters; error-tolerant parsers; the resolver against the TTR `db` symbol table
with dialect identifier folding; unknown-table/column + ambiguity diagnostics;
param cross-check. **DoD:** the diagnostics fire correctly on the S0.2 corpus in
VS Code; false-positive rate on the corpus below an agreed threshold. *(Task
lists authored after Phase 2.)*

## Phase 4 — IDE features (desktop)

Hover (column type/description), go-to-definition (SQL ref → TTR `db` def),
find-references, completion (table/column names inside SQL), rename across the
boundary. *(Outlined; task lists authored after Phase 3.)*

---

## Why later phases are not yet task-listed

The Phase 0 gate can change the approach (E8 fallback). Authoring detailed Phase
2–4 task lists before the gate risks planning against a parser we may not use.
Phase 0 and Phase 1 are fully task-listed now (INDEX); Phases 2–4 are
stage-level here and expanded immediately after the gate.
