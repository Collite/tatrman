# Changelog

All notable changes to the published `org.tatrman:*` Kotlin artifacts are
recorded here. While versions are `< 1.0.0`, minor bumps may contain breaking
changes (see [`PUBLISHING.md`](PUBLISHING.md) → Semver discipline).

## 0.3.0 — unreleased

Delivers the modeler-side half of the resolver-consolidation follow-up (the
deferred part of grammar-master Phase 2.8): exposes the file namespace on every
symbol so ai-platform's downstream proto adapter can build its
`QualifiedName` triple without re-parsing the qname string.

- **`org.tatrman:ttr-semantics:0.3.0`** — `SymbolEntry` gains a `namespace:
  String` field, populated in `DocumentSymbols` from the file's declared
  namespace (`""` when none is declared — **not** the `nsOrKind` qname
  fallback). Purely additive: the resolver algorithm, qname construction, and
  stock handling are unchanged, so both conformance harnesses are unaffected.
- `ttr-parser` / `ttr-writer` re-cut at `0.3.0` for the `kotlin/v0.3.0` bundle
  tag (no behavioural change).

Also in `ttr-semantics:0.3.0`: **schema/namespace are now optional with defaults
derived from the object kind** (namespace already fell back to the kind; the
schema now does too). When a file has **no `schema` directive**, each
definition's qname uses the default schema for its kind — `entity`/`attribute`/
`relation` → `er`, the `db`-family → `db`, `er2db_*` → `map`, `role`/`er2cnc_role`
→ `cnc`, `query`/`drill_map` → `query` (`defaultSchemaForKind`). An explicit
`schema` directive still wins for the whole file. **No grammar change — no
grammar-version bump** (both `packageDecl` and `schemaDirective` were already
optional in `TTR.g4`; only schema-less resolution changed). TS and Kotlin emit
identical qname + diagnostic sets, locked in by new schema-less conformance
fixtures (`tests/conformance/fixtures/35–40`).

Consuming `0.3.0`, ai-platform completed the **resolver consolidation**: its
hand-maintained `ReferenceResolver`/`SymbolTable` are deleted and
`ReferenceResolutionPass` now resolves through a thin adapter over
`org.tatrman.ttr.semantics.{SymbolTable, Resolver}` (proven equivalent by a
differential parity harness). This closes the deferred half of grammar-master
Phase 2.8 — a future grammar/version bump now reaches ai-platform as an
`org.tatrman:*` version-ref change with no hand-written semantics edits
(rehearsed). See [`docs/grammar-master/resolver-consolidation/`](docs/grammar-master/resolver-consolidation/).

## 0.2.1 — 2026-06-09

Reconciles the bundled stock CNC vocabulary (`StockLoader` /
`builtin/cnc-stock-roles.ttr`) with ai-platform's canonical content ahead of the
ai-platform stock-source switch: each `def role` now carries a localized
`label { cs, en }` and ai-platform's descriptions (tags dropped). Names are
unchanged, so resolution and the conformance harness are unaffected. This makes
the published artifact a true single source of truth for stock roles — including
their display labels — so ai-platform's `BuiltinStockSource` can delegate to
`StockLoader.load()` without losing data.

## 0.2.0 — 2026-06-09

Phase 2 of grammar-master. Adds the semantics artifact; replaces ai-platform's
hand-rolled resolver / symbol-table / stock-loader equivalent.

- **`org.tatrman:ttr-semantics:0.2.0`** — symbol table, six-step reference
  resolver, package inference + dependency graph (Tarjan cycle detection),
  per-kind + cross-reference `Validator`, and the bundled stock CNC vocabulary
  (`StockLoader`, `builtin/cnc-stock-roles.ttr`). Faithful Kotlin port of
  `packages/semantics/src/`. Depends on `org.tatrman:ttr-parser` (api).
- `ttr-parser` / `ttr-writer` re-cut at `0.2.0` for the `kotlin/v*` bundle tag
  (no behavioural change since `0.1.0`).

Conformance: a second harness (`SemanticsConformanceSpec` / `dump-sem` /
`diff-sem`) verifies the Kotlin resolver + validator against the TypeScript
semantics layer byte-for-byte (resolved-qname + diagnostic-code sets) across
the shared fixtures.

## 0.1.0 — 2026-06-03

Phase 1 of grammar-master. First published release of the modeler-owned Kotlin
parser stack, generated from the canonical `packages/grammar/src/TTR.g4` (v2.2).

- **`org.tatrman:ttr-parser:0.1.0`** — ANTLR-generated parser + typed AST
  (`TtrLoader`, `ParseResult`, the `Definition` hierarchy, `PropertyValue`,
  `SourceLocation`, `DiagnosticCode`, `Dedent`). Depends only on
  `org.antlr:antlr4-runtime` + `org.slf4j:slf4j-api`.
- **`org.tatrman:ttr-writer:0.1.0`** — deterministic AST → TTR-source renderer
  (`TtrRenderer`) with a round-trip guarantee against the parser. Depends on
  `org.tatrman:ttr-parser`.

Conformance: the Kotlin parser is verified byte-for-byte against the TypeScript
parser across the shared fixture set (`conformance.yml`).
