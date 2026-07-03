# MD Model — Implementation Plan

**Status:** plan v1, 2026-06-25 · Owner: editor-tooling · Companion to [`../design.md`](../design.md),
[`../contracts.md`](../contracts.md), [`../grammar-md-changes.md`](../grammar-md-changes.md),
[`../map-catalog.md`](../map-catalog.md).

This is the **overall phased plan** for the multidimensional (MD) model — the full v1 scope: the
seven logical objects (Layer A) **and** the binding layer (`md2db_*`, `md2er_cubelet`). Phase 0
(legacy renames) is its own management doc at
[`phase-0-legacy-renames/INDEX.md`](phase-0-legacy-renames/INDEX.md) and is a precondition here.

The per-phase **mini-task-lists (6–8 tasks, TDD-ordered)** live one folder per phase, following the
Phase 0 pattern. This document defines the phases, their deliverables, pre-flight conditions, and
Definitions of DONE — it is the structure those task lists hang off.

## Task lists (mini-task-list folders)

- Phase 0 — [`phase-0-legacy-renames/INDEX.md`](phase-0-legacy-renames/INDEX.md) (precondition)
- Phase 1 — [`phase-1-foundation/INDEX.md`](phase-1-foundation/INDEX.md) — stages 1A–1D
- Phase 2 — [`phase-2-logical-semantics/INDEX.md`](phase-2-logical-semantics/INDEX.md) — stages 2A–2F
- Phase 3 — [`phase-3-binding/INDEX.md`](phase-3-binding/INDEX.md) — stages 3A–3C
- Phase 4 — [`phase-4-conformance/INDEX.md`](phase-4-conformance/INDEX.md) — stages 4A–4D

## Out of scope (v1)

Operations DSL (Layer B), MOLAP bindings, query-backed cubelets, custom (non-catalog) calc maps,
non-additive recompute, Designer MD rendering/edit, writeback inverse strategies beyond declaring
the binding. See [`../design.md`](../design.md) §2, §11.

## Guiding invariants (unchanged)

Text is canonical · one LSP across hosts · parser stays mechanical · source locations on every
node · per-schema validity is **semantic**. Every phase ends green on the four repo gates:

```
pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test
```

TDD within each phase: the test-authoring task(s) come first (red), then the implementation
(green). Check every checkbox the moment its task is done.

---

## Phase sequencing

```
Phase 0  Legacy renames .............. (separate doc; D/E close out cross-repo)   ── DONE
Phase 1  Catalog package + grammar ... @modeler/md-catalog, grammar 3.1, AST, walker
Phase 2  Logical-model semantics ..... domains→cubelets, leaf/grain, hierarchy, diagnostics
Phase 3  Binding layer .............. md2db_*, md2er_cubelet, shapes, journaling, multi-source
Phase 4  Conformance + cross-repo ... Kotlin conformance, ai-platform sync, RAE fixtures, manual
```

Strictly ordered **1 → 2 → 3 → 4**. 1 is mechanical (grammar/AST) and unblocks everything; 2 is the
language brain; 3 needs the logical symbols 2 produces; 4 proves cross-target parity and migrates
real content. Phase 1 may **start** before Phase 0 Stages D/E land (they are cross-repo), but Phase
4 cannot finish until Phase 0 is fully closed (shared grammar release).

---

## Phase 1 — Catalog package + grammar foundation

**Goal:** the parser accepts every MD construct and produces a complete, located AST; the calc
catalog exists as a typed package. No validation yet.

**Pre-flight**
- Phase 0 Stages A–C merged (grammar 3.0: `schema binding`, `def area`, `.ttrm`, freed
  `domain`/`map`). D/E may still be in flight.
- Green on all four gates at 3.0.

**Deliverables**
1. **`@modeler/md-catalog`** package (`packages/md-catalog/`) — `CatalogEntry` types + the Time
   entries from [`../map-catalog.md`](../map-catalog.md), `MD_CALC_CATALOG`, `MD_CATALOG_VERSION`.
   Unit tests assert every entry's shape and that names are unique. (Contracts §8.)
2. **Grammar 3.1** in `TTR.g4` — `MD` schema code; `domain`/`dimension`/`map`/`hierarchy`/
   `measure`/`cubelet` def kinds; `md2db_*` + `md2er_cubelet` binding kinds; `DOTDOT`; new body
   keyword tokens; `idPart` extension; header `Changes in 3.1` block; version bumped in
   `package.json` + marker. (Grammar sketch §1–§8.)
3. **Grammar regen** — `packages/parser` prebuild + `vscode-ext` TextMate regen (CLAUDE.md
   procedure). TextMate highlights the new keywords.
4. **AST nodes** in `packages/parser/src/ast.ts` + **walker** additions — every node from
   contracts §2, each with accurate `SourceLocation` (re-verify `makeSourceLocation`'s multi-token
   `endColumn` rule — known footgun).
5. Cross-references kept **opaque** at the parser layer (per invariant).

**TDD**
- Parser tests first: a corpus of `.ttrm` fixtures (one per def kind + the calc-arg form + range
  literal + inline attribute/measure lists) asserting parse-success and AST shape/spans. Then make
  them pass.
- Catalog tests first: shape + uniqueness + version-format. Then seed the data.

**DONE when**
- Every MD construct in the design's examples parses into the contracts §2 AST with correct spans.
- `@modeler/md-catalog` builds, tests green, exported from `index.ts`.
- All four gates green. No semantic validation expected yet (unresolved refs are still opaque
  strings, not errors).

---

## Phase 2 — Logical-model semantics

**Goal:** the binding-free logical model is fully validated — symbols, references, leaf/grain,
hierarchy inference, calc-catalog checks, additivity — with the full `md/*` diagnostic set wired to
the LSP.

**Pre-flight**
- Phase 1 DONE. `@modeler/semantics` depends on `@modeler/md-catalog` (`workspace:*`); dependency
  graph in CLAUDE.md updated.

**Deliverables**
1. **Symbol table** — the MD namespaces (contracts §5); dimension-qualified attributes; pre-load
   `MD_CALC_CATALOG` as a read-only `calc:` symbol source (mirrors stock CNC vocab).
2. **Resolver** — `domain`/`map`/`measure`/`hierarchy`/`cubelet-grain`/`dimension` refs; the
   "map attribute A to B" → underlying-domain-map sugar resolution.
3. **Per-kind validators**:
   - domain (`kind` consistency, `restrict` clause shapes, scalar vs member-set),
   - attribute (per-schema `domain:`/`type:` rule),
   - map (calc resolution + arg/type check + cardinality),
   - **leaf/grain lattice** + **1:1 co-leaf classes** (contracts §6.1–6.2),
   - **hierarchy step inference** + ambiguity (contracts §6.3),
   - measure additivity consistency (contracts §6.5),
   - cubelet grain resolution.
4. **Diagnostics** — every logical `md/*` code from contracts §7 emitted with accurate ranges,
   published through the standard LSP channel.
5. **LSP wiring** — hover/definition/completion for MD symbols; calc-map completion lists catalog
   entries. (No new custom method; contracts §9.)

**TDD**
- Semantics unit tests first, per validator, including the leaf/grain and hierarchy-inference
  algorithms (table-driven: a small map lattice → expected leaves/ambiguities). Then implement.
- Integration tests in `tests/integration/` (the `PassThrough` harness) for the diagnostic
  round-trip on representative `.ttrm` files.

**DONE when**
- Every logical `md/*` code has a triggering fixture and a clean-file negative fixture.
- Leaf/grain and hierarchy inference match hand-computed expectations on the design's Time + the
  RAE cost-center examples.
- Catalog type-checks reject a mismatched `from`/`to` and accept the correct calendar maps.
- All four gates green.

---

## Phase 3 — Binding layer

**Goal:** the logical model binds to physical (`db`) and structurally to `er`, with shapes,
journaling, multi-source, and writeback-completeness validation.

**Pre-flight**
- Phase 2 DONE (logical symbols + leaf/grain available — binding completeness needs the grain).

**Deliverables**
1. **AST + walker** for `md2db_cubelet` / `md2db_domain` / `md2db_map` / `md2er_cubelet` (contracts
   §2) — already parsed in Phase 1; here the semantic side.
2. **Binding validators**:
   - cubelet binding: grain coverage, `shape`↔measure-form match, map-mediated attribute form,
   - journaling shapes (`overwrite`/`invalidate{validColumn}`/`diff`),
   - `md2db_domain`: target is `kind: bound`,
   - `md2db_map`: target is table-backed; columns cover all from/to domains,
   - **multi-source** grain-union agreement,
   - **writeback completeness** (every measure + grain attr bound when journaling present),
   - `md2er_cubelet` structural-only (reject physical props).
3. **Diagnostics** — the binding `md/*` codes from contracts §7.
4. **`kind: bound` ↔ source** cross-check closed (the Phase-2 `md/bound-domain-no-source` now has
   the `md2db_domain` side to satisfy it).

**TDD**
- Validator unit tests first (wide + long shapes, all three journaling modes, a multi-source pair,
  a map-mediated attribute, an over-specified md→er). Then implement.
- Integration tests: end-to-end parse→resolve→diagnose on a wide and a long binding fixture.

**DONE when**
- Wide, long, map-mediated, and multi-source bindings validate; each binding `md/*` code has a
  fixture.
- A `kind: bound` domain with a matching `md2db_domain` is clean; without it, errors.
- All four gates green.

---

## Phase 4 — Conformance, cross-repo, fixtures & docs

**Goal:** cross-target parity (TS ↔ Kotlin), ai-platform consumes grammar 3.1 + the catalog, real
RAE-derived fixtures pass end-to-end, and the manual documents MD.

**Pre-flight**
- Phases 1–3 DONE. **Phase 0 fully closed** (Stages D/E — the 3.0 grammar release and ai-models
  migration), since 3.1 publishes on top of it.

**Deliverables**
1. **Kotlin conformance** — the conformance harness (`conformance.yml`) covers the new constructs;
   the Kotlin parser reads the 3.1 `TTR.g4` and matches the TS AST on the MD corpus.
2. **`@modeler/md-catalog` vendoring** — ai-platform vendors the catalog package (mirroring
   `@modeler/grammar`); document the sync + version-pin story; ai-platform owns lowerings.
3. **End-to-end fixtures** from the RAE examples (design §12.4): `costCenterTransactions`,
   `otherDrivers` (long shape), `costCenterM2` (map-mediated store) — as conformance + integration
   targets.
4. **Grammar release** — tag/publish 3.1 per `PUBLISHING.md`; `CHANGELOG.md` documents the additive
   3.1 surface.
5. **Manual** — a `docs/manual/en/` chapter on the MD model + binding (mirrors the ER/CNC chapters).

**TDD / verification**
- Conformance is the verification gate here (cross-target diff), plus the integration fixtures from
  Phases 2–3 re-run against the real RAE-derived models.

**DONE when**
- Conformance harness green across TS + Kotlin on the MD corpus.
- ai-platform builds against grammar 3.1 and the vendored catalog; metadata service loads an MD
  fixture model with zero errors.
- RAE end-to-end fixtures validate clean.
- Manual chapter merged; `CHANGELOG.md` updated; 3.1 published.

---

## Risks & coordination notes

- **Catalog as cross-repo contract.** The catalog version is the sync key; land
  `@modeler/md-catalog` (Phase 1) early so ai-platform can begin lowerings in parallel. A model
  referencing a newer entry than the runtime supports is an ai-platform deploy-time check, not a
  modeler diagnostic (contracts §8.3).
- **Shared `attribute` body.** The one-body/per-schema-validation split is the highest-risk
  mechanical change (ER regressions). Phase 1 must keep all existing ER attribute fixtures green;
  Phase 2's per-schema rule must not fire on ER attributes.
- **`makeSourceLocation` multi-token spans.** Re-verify the `endColumn = stopToken.column +
  stopTokenLength` rule when adding the new multi-token bodies (CLAUDE.md footgun).
- **Grammar bump ordering.** 3.1 publishes on top of 3.0; do not tag a 3.1 release until Phase 0
  Stage D has shipped 3.0.
- **Designer is deferred.** No `modeler/getModelGraph` / `applyGraphEdit` work in v1; MD rendering
  is a later phase, consistent with how `db`/`er` rendering preceded edit mode.

---

## Deferred follow-ups (post-v1, for reference)

- Operations DSL (Layer B) + scalar-expression tier (enables custom calc, non-additive recompute,
  calculated measures).
- Query-backed cubelets; MOLAP bindings; Designer MD rendering then edit mode.
- Catalog escape-hatch (project-declared abstract functions) — design §9, targeted v1.1.
