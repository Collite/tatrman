# Embedded SQL — phased plan

**Status:** Plan v2, 2026-06-11 (post-spike — Phase 0 complete, **GO**). Read
[`architecture.md`](architecture.md) and [`contracts.md`](contracts.md) first —
they are normative, as is
[`embedded-language-blocks.md`](embedded-language-blocks.md) (**DESIGN**) and
[`spike-report.md`](spike-report.md) (**SPIKE**, Phase 0 results). Task lists
live in [`tasks/INDEX.md`](tasks/INDEX.md). (This feature was split out of the
now-closed `grammar-master` work into its own `docs/features/embedded-sql/`.)

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
| E9 | *(SPIKE)* Case-insensitive keywords via the **native `caseInsensitive` lexer option** — `antlr-ng` honours it; DESIGN §12.6 fallbacks not needed. |
| E10 | *(SPIKE)* **`maskPlaceholders` span-preserving pre-pass is a required carrier component** — TTR `{param}` placeholders break raw SQL lexing (contracts §3a). |
| E11 | *(SPIKE)* Browser lexer cost = **+155 KB gz (~+49%)**, **accepted**; parsers (+839 KB) stay desktop-only. SQL code lives in new `@modeler/sql` with lexer-only subpath export. |

## Phase map

| Phase | Theme | Gate in | Hosts | Status |
|---|---|---|---|---|
| **0** | **Spikes & evaluation (GATING)** | — | n/a | ✅ **DONE — GO** |
| **1** | Tagged-block grammar + value contract | Phase 0 not required (text-only) | all | ☐ task-listed |
| **2** | Lexer-first highlighting | **Phase 0 gate ✅** + Phase 1 | all | ☐ task-listed |
| **3** | Best-effort semantics | Phase 2 | desktop | ☐ task-listed |
| **4** | IDE features (hover/def/refs/completion/rename) | Phase 3 | desktop | ☐ task-listed |

Phase 0 is **complete and green (GO)** — see [`spike-report.md`](spike-report.md).
Phase 1 is independent of the SQL grammars and may proceed in parallel. Phases
2–4 are now fully task-listed (the gate that could have changed the approach has
passed).

---

## Phase 0 — Spikes & evaluation (GATING) — ✅ DONE (GO)

**Outcome: all three gates green; ANTLR approach confirmed (E3); `node-sql-parser`
fallback not triggered.** Full results: [`spike-report.md`](spike-report.md).

| Stage | Gate | Verdict |
|---|---|---|
| **S0.1** | grammars generate + instantiate under `antlr4ng`; case-insensitivity solved | ✅ PASS — generated clean; `caseInsensitive` works natively (E9); Postgres base-class injection handled by the gen script, no fork |
| **S0.2** | corpus lexes 100% / parses ≥ threshold; tight spans; backlog | ✅ PASS — tsql lex 100% **via the `{param}` mask** (E10) / parse 100%; postgres 100%/100%; spans exact; grammar backlog empty |
| **S0.3** | lexer fits Worker budget; parser sizes measured | ✅ PASS — lexers +155 KB gz (+49%), accepted (E11); parsers +839 KB → desktop-only |

The scratch `packages/sql-spike` was deleted post-gate; `spike-report.md` and
[`tasks/PINNED.md`](PINNED.md) (grammars-v4 `@923a1a9`, `antlr-ng@1.0.10`) are the
reproducible record.

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

## Phase 2 — Lexer-first highlighting (all hosts)

**Repo:** modeler. **Pre-flight:** Phase 0 gate green ✅; Phase 1 merged.
**Deliverables:** new `@modeler/sql` package with vendored-pinned (`@923a1a9`)
`tsql` + `postgresql` lexers (generated, Postgres base-class injection per SPIKE
S0.1); the `maskPlaceholders` pre-pass (E10); a dialect-keyed SQL lexer service
over the masked `TaggedBlockValue.value`; the §8 source map; embedded SQL
semantic tokens (incl. masked spans → `parameter`) merged into the LSP response;
browser bundles the **lexer-only subpath** (E11). **Stages:** 2.1–2.5 (INDEX).
**DoD:** SQL highlights in VS Code and the Designer; a deliberately broken query
still highlights; `{param}` placeholders colour as parameters; semantic-token
positions verified against the source map in tests; Worker bundle within the
+155 KB budget (guard test).

## Phase 3 — Best-effort semantics (desktop)

**Repo:** modeler. **Pre-flight:** Phase 2 merged; `modeler.toml` SQL config
schema agreed (contracts §5). **Deliverables:** per-dialect `SqlRefModel`
adapters; error-tolerant parsers; `modeler.toml` SQL config loader; the resolver
against the TTR `db` symbol table with dialect identifier folding;
unknown-table/column + ambiguity diagnostics; `parameters` cross-check (using the
masked placeholder spans + native bind params). **Stages:** 3.1–3.5 (INDEX).
**DoD:** diagnostics fire correctly on the S0.2 corpus in VS Code; false-positive
rate below an agreed threshold.

## Phase 4 — IDE features (desktop)

Hover (column type/description), go-to-definition (SQL ref → TTR `db` def),
find-references (TTR `db` symbol → SQL usages), completion (table/column names
inside SQL, dialect-aware qualification via the reverse namespace map). Rename
across the boundary depends on the v1.1 edit synthesizer and is scoped as
conditional. **Stages:** 4.1–4.5 (INDEX). **DoD:** each feature works on the
S0.2 corpus in VS Code; browser remains lexer-only (no semantics features).

---

## Task-list status

Phase 0 is complete (GO). **Phases 1–4 are now fully task-listed** in
[`tasks/INDEX.md`](tasks/INDEX.md) — the gate that could have forced the E8
fallback re-plan has passed, so detailing the later phases is now safe. Phases
3–4 task lists assume the ANTLR/`@modeler/sql` shape; they would only change if a
future grammar gap forces a lazy patch (DESIGN §12.7), which does not alter the
stage structure.
