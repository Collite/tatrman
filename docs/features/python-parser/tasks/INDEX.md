# Python parser tasks — master index

**Use this index to navigate.** Each linked file is a mini-task-list of 6–8 tasks
intended to be executed in one coding session by one developer/agent. **Check
each box the moment a task is done — do not batch.**

**Read first (required context for every task list):**

- [`../plan.md`](../plan.md) — the six-phase plan; OQ1/2/3 resolved.
- [`../architecture.md`](../architecture.md) — module layout, generation flow,
  semantics layer (§5a), conformance (§7), distribution (§8).
- [`../contracts.md`](../contracts.md) — Python public API: parser (§2),
  semantics (§3), conformance dump (§5/§5.1), CI (§6).
- [`../INDEX.md`](../INDEX.md) — decisions D1–D8.
- [`../../../grammar-master/AST-NAMING.md`](../../../grammar-master/AST-NAMING.md) —
  the TS↔Kotlin type map; **gets a new Python column in stage 03**.

**Canonical sources to port from (the binding instruction is "mirror these"):**

- Parser/walker: `packages/parser/src/walker.ts`, `ast.ts`; cross-checked against
  Kotlin `packages/kotlin/ttr-parser/src/main/kotlin/.../{walker,model}/`.
- Semantics: `packages/semantics/src/{qname,symbol-table,project-symbols,package-inference,package-graph,resolver,validator,stock-loader}.ts` + `stock/cnc-roles.ttr`; cross-checked against Kotlin `packages/kotlin/ttr-semantics/`.
- Conformance: `tests/conformance/{fixtures,out-ts,out-ts-sem}/` and the TS dumper.

---

## Phase P1 — Scaffolding + generated parser  *(modeler-only)*

| Stage | Mini-task-list | Status |
|---|---|---|
| 1.1 | [Scaffolding: package skeleton + ANTLR Python generate](01-scaffolding.md) | ☐ |

**P1 DoD:** `pip install -e packages/python/ttr-parser` then `pytest` smoke
green; `ruff` + `mypy` green (excluding `_generated/`); `pnpm -r test` unaffected.

## Phase P2 — AST model + walker + loader

| Stage | Mini-task-list | Status |
|---|---|---|
| 2.1 | [Tests first: parser pytest suites + fixtures](02-tests-first-parser.md) | ☐ |
| 2.2 | [Model: frozen dataclasses (AST + PropertyValue)](03-model.md) | ☐ |
| 2.3 | [Walker + dedent + tag registry + loader](04-walker-loader.md) | ☐ |

**P2 DoD:** all ported parser suites green; `mypy --strict` green; a real
`samples/` model parses with zero errors and a spot-checked AST; the Python
column in `AST-NAMING.md` is complete.

## Phase P3 — Parser conformance (TS golden, AST)

| Stage | Mini-task-list | Status |
|---|---|---|
| 3.1 | [Conformance: AST dump + py-vs-ts diff + CI](05-conformance-ast.md) | ☐ |

**P3 DoD:** `py-vs-ts` green across all fixtures locally and in CI; an
intentional walker break turns it red (gate proven).

## Phase P4 — Semantics core

| Stage | Mini-task-list | Status |
|---|---|---|
| 4.1 | [Tests first: semantics pytest suites](06-tests-first-semantics.md) | ✅ all 7 suites green (via 4.2–4.4) |
| 4.2 | [Qname + SymbolTable + PackageInference + PackageGraph](07-qname-symboltable.md) | ✅ green |
| 4.3 | [Resolver (six-step chain)](08-resolver.md) | ✅ green |
| 4.4 | [Validator subset + StockLoader + load_project](09-validator-stock.md) | ✅ green |

**P4 DoD:** all ported semantics suites green; `mypy --strict` green; a real
`samples/` project resolves references with no spurious diagnostics.

## Phase P5 — Semantics conformance (§5.1)

| Stage | Mini-task-list | Status |
|---|---|---|
| 5.1 | [Conformance: resolution dump + py-sem-vs-ts + CI](10-conformance-semantics.md) | ✅ green (54/54 byte-identical) |

**P5 DoD:** `py-sem-vs-ts` green across all fixtures (single + multi-doc)
locally and in CI; an intentional resolver/stock break turns it red.

## Phase P6 — Packaging + publish (PyPI)

| Stage | Mini-task-list | Status |
|---|---|---|
| 6.1 | [Packaging metadata + publish-python.yml + first 0.1.0](11-publishing.md) | ✅ `ttr-parser 0.1.0` live on PyPI (Trusted Publishing); clean Java-free install verified |

**P6 DoD:** `pip install ttr-parser` into a clean venv with no Java; both
`parse_file` and `load_project(...).resolve(...)` work on a real model;
`python/v0.1.0` tag pushed; CHANGELOG recorded.

---

## Conventions

- **Check boxes the moment a task is done.** Do not batch.
- **Tests precede implementation** within each phase (TDD per the planning
  skill): stages 2.1 and 4.1 write the failing suites before 2.2–2.3 / 4.2–4.4.
- **Reference docs are normative.** When a task and `plan.md`/`contracts.md`/
  `architecture.md` disagree, the docs win and the task is wrong — open a
  discussion before changing course.
- **modeler-only.** Unlike the Kotlin migration, no stage touches ai-platform.
  This is an additive, standalone Python package.
- **Pin the toolchain.** ANTLR generator + `antlr4-python3-runtime` are both
  `4.13.2` (D1); never float. CPython floor 3.13 (D2, bumped from 3.10 in P1.1).
- **No SNAPSHOTs / pre-release churn** (D7). Iterate locally with `pip install -e`.
