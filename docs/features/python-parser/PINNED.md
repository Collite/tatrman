# Python parser — pinned facts (read before starting)

Quick-reference card for whoever picks this up. These are **fixed** by the design
docs; do not change them without updating `plan.md`/`contracts.md` first (the docs
are normative — when a task disagrees with them, the task is wrong).

## Start here

1. [`INDEX.md`](INDEX.md) — what & why, decisions D1–D8.
2. [`architecture.md`](architecture.md) — solution shape, generation flow,
   semantics layer (§5a), conformance (§7), distribution (§8).
3. [`contracts.md`](contracts.md) — the Python public API (parser §2, semantics
   §3, dump schema §5/§5.1).
4. [`plan.md`](plan.md) — six phases, DoD per phase.
5. [`tasks/INDEX.md`](tasks/INDEX.md) — the 11 mini-task-lists; **execute in
   order**, check boxes as you go. Begin at [`tasks/01-scaffolding.md`](tasks/01-scaffolding.md).

## Pinned versions (D1, D2) — never float

| Thing | Value | Why |
|---|---|---|
| ANTLR generator (reference `antlr4` jar) | **4.13.2** | Must match the Kotlin runtime so parse behaviour is identical. **Not `antlr-ng`** (TS-only). |
| `antlr4-python3-runtime` | **==4.13.2** | Same major.minor as the generator. |
| CPython floor | **3.13** | Matches the toolchain available in this repo (`pyenv 3.13.9`). `match`, PEP 604 unions, `dataclass(slots=True)`, PEP 742 narrowing types all available. |
| TTR grammar | `@grammar-version 2.2` | Read directly from `packages/grammar/src/TTR.g4`; **no vendoring**. |

## Locked decisions

- **D3 — Public PyPI.** CI-built wheels, tag `python/v<x.y.z>`. Pure-Python wheel,
  **no JVM at consumer install**.
- **D4 — `_generated/` gitignored**, regenerated from `TTR.g4` at build; bundled
  into the wheel via the Hatchling `artifacts` include (it is VCS-ignored, so an
  explicit `artifacts` pattern un-ignores it for the build; `force-include` was
  tried but double-adds against the `packages` walk — see review-064 F1).
- **D5 — Class names mirror Kotlin, fields snake_case.** The Python column of
  [`../../grammar-master/AST-NAMING.md`](../../grammar-master/AST-NAMING.md) is the
  surface-name map (added in `tasks/03-model.md`).
- **D8 — One package, two layers.** `ttr-parser` ships `ttr_parser` (parse) **and**
  `ttr_parser.semantics` (resolve) — diverges from Kotlin's two artifacts on
  purpose, so consumers get resolution with one `pip install`.
- **Read-only** (OQ2): no writer. **Models only** (scope): no `.ttrg`/graphs.

## Canonical sources to mirror (the binding instruction is "mirror these exactly")

- Parser/walker: `packages/parser/src/{walker,ast,tag-registry}.ts`
  (cross-check Kotlin `packages/kotlin/ttr-parser/src/main/kotlin/.../{walker,model}/`).
- Semantics: `packages/semantics/src/{qname,symbol-table,project-symbols,
  package-inference,package-graph,resolver,validator,stock-loader}.ts`
  + `stock/cnc-roles.ttr` (cross-check Kotlin `packages/kotlin/ttr-semantics/`).
- Conformance: `tests/conformance/{fixtures,out-ts,out-ts-sem}/` + the TS dumper.

## Invariants that bite if ignored

- **`SourceLocation` span:** `end_column = stop.column + len(stop.text)` (not
  `start + span`); offsets are **byte** offsets from the token stream
  (`offset_end = ctx.stop.stop + 1`) — never `str`-slice. (contracts §2.4)
- **Errors never raise:** accumulate on `ParseResult.errors`; on any error
  `definitions == ()` (no partial trees). (contracts §2.1)
- **Resolver six-step order is load-bearing:** lexical → same-package →
  named-import → wildcard-import (non-recursive) → `cnc.*` auto-import →
  fully-qualified. Stock resolves to the **doubled** `cnc.cnc.role.<name>` qname.
  (contracts §3.3)
- **Two conformance gates pin everything** against the committed TS golden:
  `py-vs-ts` (AST, §5) and `py-sem-vs-ts` (resolution, §5.1). The TS golden is the
  reference — fix the Python code on drift, never the golden.

## Where it lives

- Package: `packages/python/ttr-parser/` (next to `packages/kotlin/ttr-parser/`).
- Dist name `ttr-parser`; import `ttr_parser` (+ `ttr_parser.semantics`).
- **modeler-only** — no ai-platform changes anywhere in this feature.
