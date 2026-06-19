# Implementation plan — Python parser

Six phases in **modeler only** (no ai-platform changes — this target is consumed
by external Python developers, not the platform). Each phase is self-contained
and leaves the repo green. TDD throughout: the named test suite is written and
made to fail before the implementation in that phase. The Kotlin migration
([`docs/grammar-master/plan.md`](../../grammar-master/plan.md): Phase 1 parser +
Phase 2 semantics) is the template — this mirrors it for the Python target, but
ships both layers in **one** package (D8).

**Global pre-flight (all phases):** baseline green `pnpm -r test` (TS untouched);
the shared conformance goldens `tests/conformance/out-ts/` **and** `out-ts-sem/`
are current (`pnpm --filter @modeler/conformance dump-all` clean); Java 21 +
Python 3.10+ available locally for the generate step.

> Decisions D1–D8 and scope are in [`INDEX.md`](INDEX.md). OQ1 (public PyPI),
> OQ2 (read-only) and OQ3 (semantics in scope) are **resolved** — reflected in
> the phases below.

---

## Phase P1 — Scaffolding + generated parser

**Deliverable.** `packages/python/ttr-parser/` builds: the ANTLR Python parser
generates from the canonical `TTR.g4`, imports cleanly, and parses a trivial
`.ttr` into a raw parse tree.

**Work.**
- New package tree per [`architecture.md`](architecture.md) §3: `pyproject.toml`
  (Hatchling), `src/ttr_parser/__init__.py`, `scripts/generate-python-parser.sh`.
- `generate-python-parser.sh` mirrors
  `packages/grammar/scripts/generate-typescript-parser.sh`: invoke the **reference
  `antlr4` jar 4.13.2** (`-Dlanguage=Python3 -visitor`) against
  `../../grammar/src/TTR.g4`, output to `src/ttr_parser/_generated/` (D1).
- Pin `antlr4-python3-runtime==4.13.2`. Add `_generated/` to `.gitignore` (D4);
  exclude it from ruff/mypy.
- Hatchling build hook runs the generate step before the wheel is assembled.

**Tests first** (`tests/test_smoke.py`): generate succeeds; `import ttr_parser`
works; the generated `TTRParser` parses `model X {}` to a tree with no recogniser
errors.

**DONE.** `pip install -e packages/python/ttr-parser` then `pytest` green;
`ruff check` + `mypy` green (excluding `_generated/`); `pnpm -r test` still green.

---

## Phase P2 — AST model + walker + loader

**Deliverable.** `parse_string` / `parse_file` / `parse_directory` return the
typed AST of [`contracts.md`](contracts.md) §2, faithful to grammar v2.2, for the
full grammar surface (read-only, models only).

**Work.**
- `model.py` — the frozen dataclasses: `SourceLocation`, `Definition` subtypes
  (16 kinds with `kind` class-vars), `PropertyValue` variants incl.
  `TaggedBlockValue`, `Reference`, `SchemaDirective`, `ImportStatement`,
  `PackageDeclaration`, `Localized*`, `SearchHintsValue`, `DataType`, mapping
  types. **No** `GraphBlock` (out of scope).
- `walker.py` — top-down context traversal porting `walker.ts` / `TtrWalker.kt`
  (architecture §5): 16 def builders, the 8 `PropertyValue` builders, `Reference`
  splitting, the `SourceLocation` multi-token-span invariant
  (`end_column = stop.column + len(stop.text)`).
- `dedent.py` — the 3-step dedent (contracts §2.9); `tag_registry.py` — tag →
  language/dialect; `diagnostics.py` — `DiagnosticCode` enum (string values ==
  Kotlin `.id`).
- `loader.py` — `ParseResult`, error listener (never raises; empty defs on
  error), `parse_directory` filtering `*.ttr` / excluding `*.ttrg` / skipping
  `.modeler` `node_modules` `.git`.
- Add the **Python column** to
  [`AST-NAMING.md`](../../grammar-master/AST-NAMING.md) in the same change.

**Tests first** (port the Kotlin Kotest specs to pytest):
- `test_loader.py` ← `TtrLoaderSpec` + `ParseDirectorySpec` (each def kind; error
  accumulation; `.ttrg` exclusion).
- `test_dedent.py` ← `DedentSpec` (CPython reference cases).
- `test_tagged_block.py` ← `TaggedBlockSpec`; `test_inline_mappings.py` ←
  `InlineMappingsSpec`; `test_drill_map.py` ← `DrillMapParserSpec`.
- `test_source_location.py` ← `SourceLocationSpec` (the span invariant);
  `test_id_value_parts.py` ← `IdValuePartsSpec`.

**Pre-flight.** P1 merged. Read `walker.ts` and `TtrWalker.kt` side-by-side for
each def kind before porting — they are the spec.

**DONE.** All ported suites green; `mypy --strict` green; a real sample from
`samples/` parses with zero errors and a spot-checked AST.

---

## Phase P3 — Conformance harness (TS golden ⇄ Python)

**Deliverable.** The Python parser is pinned to the TS golden on every shared
fixture, in CI.

**Work.**
- `tests/conformance/dump.py` — emit the normalised JSON dump (contracts §5)
  for each fixture in `tests/conformance/fixtures/` into
  `tests/conformance/out-py/`. Re-implement the §5 rules exactly (strip
  `SourceLocation`; sort keys; `kind` discriminator; **TTR surface** property
  names via the rename map; native scalars).
- `tests/conformance/test_conformance.py` — diff `out-py/<f>.json` against the
  committed `out-ts/<f>.json` per fixture; fail on any difference.
- `conformance.yml`: add `py-dump` (setup-python + setup-java → generate →
  dumper) and `py-vs-ts` (download + byte diff) jobs (contracts §6.1).

**Tests first.** The harness *is* the test — wire one fixture, watch it diff,
then run the full set. Add a deliberately-wrong dump to confirm the gate fails
red before relying on it.

**Pre-flight.** P2 merged; confirm `out-ts/` is current (regenerate + commit if
the TS dumper moved since).

**DONE.** `py-vs-ts` green across **all** fixtures locally and in CI; an
intentional walker break turns it red (gate proven).

---

## Phase P4 — Semantics core

**Deliverable.** `ttr_parser.semantics` resolves references via the six-step
chain with stock auto-imports, builds the symbol table, infers packages, and runs
the portable validator subset — the API of [`contracts.md`](contracts.md) §3.

**Work.**
- `semantics/qname.py` ← `qname.ts`; `semantics/package_inference.py` ←
  `package-inference.ts`; `semantics/package_graph.py` ← `package-graph.ts`.
- `semantics/symbol_table.py` ← `symbol-table.ts` + `project-symbols.ts`
  (`upsert_document` / `get` / `get_by_package` / `get_by_suffix` / `duplicates`;
  `SymbolEntry` full shape). Keep the `is_stock_cnc` gate (stock under `stock://`,
  doubled `cnc.cnc.role.*`).
- `semantics/resolver.py` ← `resolver.ts` — `ResolutionContext`,
  `resolve_reference` / `resolve_bare_id`, the `ResolutionResult` union, the
  **six steps in exact order** (lexical → same-package → named-import →
  wildcard-import → auto-import → fully-qualified).
- `semantics/validator.py` ← `validator.ts` — the **portable subset** only
  (`validate_document` + `validate_references` + `validate_project` +
  `validate_imports`); wire the semantics codes into `diagnostics.py`.
- `semantics/stock_loader.py` ← `stock-loader.ts`; bundle
  `stock/cnc-roles.ttr` as package data, **copied at build** from
  `packages/semantics/src/stock/cnc-roles.ttr` (no committed duplicate). Load via
  `importlib.resources`.
- `semantics/__init__.py` + `load_project` / `Project` convenience (§3.0); export
  from `ttr_parser`'s public surface.

**Tests first** (port the Kotlin/TS semantics specs to pytest):
- `test_resolver.py` ← `resolver.test.ts` / `ResolverSpec` (each of the six
  steps; ambiguity; non-recursive wildcard; `cnc.*` auto-import).
- `test_symbol_table.py` ← `symbol-table.test.ts`; `test_package_inference.py`,
  `test_package_graph.py`; `test_stock_loader.py` ← `StockLoaderSpec` (doubled
  qname form; stock auto-import integration).
- `test_validator.py` — the portable-subset codes only.

**Pre-flight.** P3 merged. Read `resolver.ts` + the Kotlin `Resolver` side by
side before porting — the step order and the stock-qname shape are load-bearing.

**DONE.** All ported semantics suites green; `mypy --strict` green; a real
`samples/` project resolves its references with no spurious diagnostics.

---

## Phase P5 — Semantics conformance (§5.1)

**Deliverable.** The Python resolver is pinned to the TS golden resolution dump
on every shared fixture (single files **and** multi-doc subdirectories), in CI.

**Work.**
- `tests/conformance/dump_sem.py` — for each fixture (and each multi-doc
  subdirectory), load stock + build the symbol table + resolve every reference +
  run the portable validator, emit normalised `{ diagnostics, resolved }`
  (contracts §5.1) into `tests/conformance/out-py-sem/`.
- Extend `tests/conformance/test_conformance.py` to diff `out-py-sem/<f>.json`
  against the committed `out-ts-sem/<f>.json`; fail on any difference.
- `conformance.yml`: extend the `py-dump` job to also run the semantics dumper
  and add the `py-sem-vs-ts` diff job (contracts §6.1).

**Tests first.** The harness is the test — wire one single-file fixture and one
multi-doc subdirectory, watch them diff, then the full set; confirm an
intentional resolver break turns it red.

**Pre-flight.** P4 merged; confirm `out-ts-sem/` is current.

**DONE.** `py-sem-vs-ts` green across all fixtures locally and in CI; an
intentional resolver/stock break turns it red (gate proven).

---

## Phase P6 — Packaging + publish (PyPI)

**Deliverable.** A consumer can `pip install ttr-parser` and both
`from ttr_parser import parse_file` and `from ttr_parser.semantics import
load_project` work — with **no JVM** on their machine.

**Work.**
- Finalise `pyproject.toml` metadata (classifiers, `requires-python = ">=3.10"`,
  license, URLs). Confirm the wheel bundles `_generated/` (D4) **and** the stock
  `cnc-roles.ttr` package data, so the installed wheel is pure-Python and
  resolution works out of the box.
- `publish-python.yml` (contracts §6.2): tag `python/v<x.y.z>` → build
  wheel+sdist (Java in CI for the generate step) → `twine upload` to **public
  PyPI** (D3). No SNAPSHOTs (D7).
- `README.md` — consumer quickstart: `pip install ttr-parser`; a ~15-line "parse
  a directory, build a project, resolve a reference" example.
- `CHANGELOG.md` — seed `0.1.0` (ships parser **and** semantics together).
- PyPI API token in repo secrets (`PYPI_TOKEN`).

**Pre-flight.** P5 green; PyPI project/name reserved; token in secrets.

**DONE.** `0.1.0` installs from PyPI into a clean venv with no Java; `parse_file`
and `load_project(...).resolve(...)` work on a real model; tag pushed; CHANGELOG
recorded.

---

## Deferred (not in this feature)

- **`ttr-writer` (Python).** Model → text renderer. Mirrors grammar-master
  `contracts.md` §3. Only if a consumer needs round-tripping (OQ2 resolved —
  read-only is sufficient for now).

## Sequencing & risk

```
P1 ─► P2 ─► P3 ─► P4 ─► P5 ─► P6      (strictly ordered; modeler-only)
       AST + walker  │    semantics  │  publish parser+semantics together
```

- **Top risk — walker/resolver drift from TS/Kotlin** (architecture §10.1):
  mitigated by P3 (AST) and P5 (resolution) landing before any consumer depends
  on the package, and by both gates running on every PR thereafter.
- **Runtime parse-tree parity** (§10.2): mitigated by pinning generator+runtime
  to 4.13.2 and by P3.
- **Byte-offset / Unicode** (§10.3): covered by `test_source_location.py` +
  shared fixtures; derive offsets from the token stream, never from `str`
  slicing.
- **Stock vocab drift** (§10.5): mitigated by copying the canonical
  `cnc-roles.ttr` at build (no duplicate) and by the P5 resolution gate.
- **No grammar change** → no `TTR.g4` regen, no TextMate regen, no Kotlin/TS
  impact. This feature is purely additive.

## Next step after approval

Generate per-phase task lists (6–8 tasks each, checkboxes, TDD ordering) under
`docs/features/python-parser/tasks/`, plus an index doc — per the planning
skill's task-list rules. The P3/P5 task lists should cite the conformance harness
(AST and semantics dumps) and the P6 task list the PyPI publish flow explicitly.
