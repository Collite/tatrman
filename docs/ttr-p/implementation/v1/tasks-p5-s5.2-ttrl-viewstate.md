# Tasks · P5 · Stage 5.2 — `.ttrl` sidecar + view state

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The family-wide `.ttrl` view-state sidecar working end-to-end for TTR-P: grammar hosted in the **TTR-M** toolchain (`TTR.g4` → `ttr-parser`, C1-c-iii), `ttrp/getLayout`/`ttrp/setLayout` with **wholesale rewrite** (contracts §4), **ζ** SSA-qualified identity keys with atomic pair rewrite on rename + **deterministic orphaning** (never mis-attach, C1-c-i), pair-integrity diagnostics, and the **binary auto/manual** layout model with a deterministic Kotlin-side auto-layout (C1-b layout decision, P2).

**Cross-boundary honesty:** the `.ttrl` grammar does **not** land in `TTRP.g4` and is **not** a fresh `.g4`. Per C1-c-iii, "the TTR-M grammar hosts `.ttrl`" — i.e. this stage **extends `packages/grammar/src/TTR.g4`** (the canonical TTR-M family grammar, the v1.1 `layout`-block grammar promoted to a document body) and the Kotlin `ttr-parser` + the TS parser regenerate from it. Follow the CLAUDE.md grammar-regeneration procedure to the letter (generated dirs are gitignored; only `TTR.g4`, scripts, and the TextMate JSON are committed).

## Pre-flight (all must pass before T5.2.1)

- [ ] Stage 5.1 DONE (`./gradlew :packages:kotlin:ttrp-designer-server:test` green).
- [ ] TTR-M toolchain green on both domains: `pnpm -r build && pnpm -r test` and `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-writer:test`.
- [ ] Confirm rename (Stage 4.1) is SSA-aware and has the "sidecar-atomic groundwork for ζ" hook the plan promised; if absent, record a blocker — T5.2.5 depends on it.

## Tasks

### T5.2.1 · TDD: fixtures + failing specs (red)

- [ ] Write the normative sidecar fixture `packages/kotlin/ttrp-lsp/src/test/resources/fixtures/hero.ttrl` (shape per the C1-c-iii inventory; concrete spelling finalized in T5.2.2 — update the fixture then if the grammar session lands different punctuation, but the **inventory is fixed**: header, canvas key, skin, mode, ζ-keyed nodes, collapsed; no viewport; bendPoints slot reserved, never written):

```
ttrl 1

canvas program {
    skin: "alteryx-knime"
    mode: manual
    nodes: {
        "db_prep":          { x: 120, y: 80 }
        "crunch":           { x: 420, y: 80 }
        "big_customers~1":  { x: 700, y: 40 }
    }
    collapsed: []
}

canvas crunch {
    skin: "enso"
    mode: auto
}
```

- [ ] `packages/kotlin/ttr-parser/src/test/kotlin/.../TtrlDocumentSpec.kt` (Kotest): parses the fixture; header version = 1; two canvases; auto canvas has **no** nodes block (grammar-enforced or validated); rejects a `nodes` block under `mode: auto`; rejects a `viewport` key (dropped, C1-c-iii). RED.
- [ ] `packages/kotlin/ttrp-lsp/src/test/kotlin/.../ZetaKeySpec.kt`: ζ key derivation from the hero graph — reassigned variable → `crunch/sales#2`, anonymous chain node → `crunch/sums~1`, orchestration-level keys = container names + program-leaf keys (`big_customers~1`). RED.
- [ ] `packages/kotlin/ttrp-lsp/src/test/kotlin/.../OrphaningSpec.kt` + `PairIntegritySpec.kt`: cases below (T5.2.5). RED.
- [ ] `packages/kotlin/ttrp-lsp/src/test/kotlin/.../AutoLayoutSpec.kt`: determinism property cases (T5.2.6). RED.

**Verify:** `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttrp-lsp:test` — new specs fail for the right reasons (missing grammar/impl), no compile errors elsewhere.

### T5.2.2 · Extend `TTR.g4` with the `.ttrl` document body (C1-c-iii) + regen all targets

- [ ] Edit `packages/grammar/src/TTR.g4`: add the `.ttrl` document rule — `ttrl <int>` header; `canvas <key> { … }` blocks (key: TTR-P `program` / container path; TTR-M qname — keep the rule generic, family-wide); `skin: <string>` (generic field, C1-b-iii); `mode: auto|manual`; `nodes:` map of ζ-key strings → `{x, y}` (manual only); `collapsed: [ … ]`; **reserve** an `edges:` slot in the rule commented "bendPoints — reserved, not v1" (parser accepts nothing there in v1). Promote/reuse the v1.1 layout-block sub-rules rather than duplicating them (see `docs/v1-1/design/v1.1-packages-and-graphs.md` §6.2/§6.5/§15).
- [ ] Regenerate per CLAUDE.md: `cd packages/parser && pnpm run prebuild` (antlr-ng TS parser) — TS build must stay green even though TTR-P doesn't consume it; then `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts` (TextMate). Kotlin `ttr-parser` regenerates from `TTR.g4` at build (no sync/copy). Commit only `TTR.g4` + `syntaxes/ttr.tmLanguage.json`.
- [ ] Kotlin `ttr-parser`: AST types for the `.ttrl` document (`TtrlDocument`, `TtrlCanvas`, `TtrlNodeEntry`) + file-kind dispatch on the `.ttrl` extension.
- [ ] Grammar change = TTR spec-version relevant: follow `docs/grammar-master/new-grammar-version-process.md` (S6) — at minimum record the pending version cut; do not silently ship a grammar drift (conformance harness will catch TS/Kotlin divergence).

**Verify:** `./gradlew :packages:kotlin:ttr-parser:test --tests '*TtrlDocumentSpec'` green **and** `pnpm -r build && pnpm -r typecheck` green.

### T5.2.3 · ζ key computation (C1-c-i)

- [ ] `packages/kotlin/ttrp-lsp/src/main/kotlin/org/tatrman/ttrp/lsp/viewstate/ZetaKeys.kt`: from the resolved graph, compute per-node keys — `<container-path>/<name>#<ssa-ordinal>` for named SSA nodes (`crunch/sales#2`), `<container-path>/<name>~<n>` for anonymous chain nodes keyed off the nearest upstream name (`crunch/sums~1`); orchestration canvas keys = container names + program-leaf keys. One naming story with Q7-γ labels and E-b CTE names — assert in the spec that ζ names equal the emit-side SSA names for the same fixture.
- [ ] Also compute per name: **chain length** (SSA count for `X`, chain length behind `X~n`) — the orphaning discriminator stored/compared in T5.2.5.
- [ ] Derived canvases (fragment sub-graphs) get **no** ζ keys — mark them `derived` (C1-b-iv; they never appear in `.ttrl`).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests '*ZetaKeySpec'` green.

### T5.2.4 · `ttrp/getLayout` / `ttrp/setLayout` — wholesale sidecar rewrite

- [ ] `TtrlWriter` in `packages/kotlin/ttr-writer` (family-wide asset — H-2c/C1-f: written once, Kotlin-side; the TTR-M migration arc reuses it): deterministic canonical emission — canvases in document order (`program` first, then container paths sorted), node entries sorted by ζ key, fixed indentation. Same layout ⇒ byte-identical file (P2). Round-trip spec: parse(write(x)) == x.
- [ ] `ttrp/getLayout {uri}` → sidecar content (parsed form) in `ttrp-lsp`; missing sidecar ⇒ empty layout (all canvases implicitly `mode: auto`). Pairing by filename: `x.ttrp` ↔ `x.ttrl`, `report.ttr.sql` ↔ `report.ttrl` (contracts §1).
- [ ] `ttrp/setLayout {uri, layout}` → **rewrite the sidecar wholesale** via `TtrlWriter` (writer isolation — never surgical edits; C3-h/v1.1 §15). Reject payloads containing: nodes on auto canvases, derived-canvas entries, viewport keys.
- [ ] Add getLayout/setLayout round-trip cases to `WsLspTransportSpec` (5.1 harness): setLayout(hero fixture) → getLayout returns it byte-stably.

**Verify:** `./gradlew :packages:kotlin:ttr-writer:test :packages:kotlin:ttrp-lsp:test :packages:kotlin:ttrp-designer-server:test` green.

### T5.2.5 · Atomic pair rewrite + deterministic orphaning + pair-integrity diagnostics (ζ)

- [ ] **Atomic pair rewrite:** LSP `textDocument/rename` of a variable and structured edits that rename (5.4 `renameVariable`) rewrite the paired `.ttrl` **in the same operation** — ζ keys `old#n` → `new#n` — via the 4.1 sidecar-atomic hook. Spec: rename `sales`→`sales2` in hero → sidecar keys updated, positions preserved, one atomic apply (no window where text and sidecar disagree).
- [ ] **Deterministic orphaning rule (verbatim C1-c-i):** if the SSA chain length of name `X` (or the chain length behind anonymous key `X~n`) changed since the sidecar was written, **ALL** `X#n`/`X~n` entries orphan → those nodes fall back to auto-layout. Never re-attach by guess (P2). Implement by recording the chain length per name at write time (a `#chain` annotation per name group in the sidecar, added to the T5.2.2 grammar) or by comparing current graph vs sidecar key population — pick the recorded-length variant (explicit beats inference) and note it in the contracts changelog with the schema addition.
- [ ] **Never mis-attach property test:** Kotest property/table test over edit scripts (insert reassignment, delete statement, reorder, rename) — for every script, each surviving sidecar entry attaches to a node whose ζ key is *provably* unchanged, or is orphaned. Zero silent position transfers.
- [ ] **Pair-integrity diagnostics** (visible, not silent decay): `TTRP-LAY-001` "layout entries unmatched — reset or re-place" (orphans present, warning on the `.ttrl`), `TTRP-LAY-002` sidecar parse error, `TTRP-LAY-003` sidecar references unknown canvas. Register in the named-diagnostics catalogue (contracts §8; suggested alternative per convention). getLayout result flags orphaned keys so 5.3 can badge them.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests '*Orphaning*' --tests '*PairIntegrity*'` green.

### T5.2.6 · Deterministic auto-layout (Kotlin-side layered assignment) + binary mode semantics

**Decision (recorded here — C1-b names no algorithm, only "deterministic"):** auto-layout is computed **Kotlin-side** in `ttrp-lsp` — coherent with C1-f ("view-state code written once, Kotlin-side") and Kotest-testable without a browser. elkjs-on-client rejected: duplicates view-state machinery in TS (the C1-f anti-pattern) and its determinism depends on input-order discipline we'd have to enforce anyway. Algorithm: **layered (Sugiyama-lite), fully deterministic** —

1. layer = longest-path rank from sources (data edges; control edges ignored for ranking);
2. in-layer order = stable sort by (upstream barycenter as exact rational, then ζ key lexicographic) — no RNG, no iteration-count heuristics;
3. output **abstract coordinates** `{layer, index}` per node; the client maps them to pixels per skin orientation (Alteryx/KNIME data L→R ⇒ x=layer; Enso T↓ ⇒ y=layer) — this keeps one deterministic core serving both edge-orientation conventions (C1-b) without per-skin position sets (C1-b-ii).

- [ ] `viewstate/AutoLayout.kt` implementing the above; property test: identical output across 100 runs and across arbitrary node-insertion-order permutations of the same graph (P2-mandatory determinism); snapshot test on the hero (golden `{layer,index}` map).
- [ ] Expose per canvas via a new `autoLayout` field on the `ttrp/getGraph` result. **This is a contract addition** — contracts §4 doesn't carry it today: add the field to `contracts.md` §4 + a changelog entry (the contracts file mandates one).
- [ ] Binary mode semantics enforced server-side: `mode: auto` canvases persist **nothing** (setLayout with nodes on an auto canvas = reject, already in T5.2.4); flipping to manual = the client snapshots rendered positions and setLayout writes them with `mode: manual` (client behavior lands in 5.3 — here, spec the server accepting exactly that transition and the reverse "reset to auto" = canvas block rewritten to `mode: auto`, nodes dropped).
- [ ] Derived canvases: auto-only, never in the sidecar (C1-b-iv) — validated in T5.2.4, asserted again here against `AutoLayout` output (derived sub-graphs still get auto coordinates; they're just never persisted).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests '*AutoLayoutSpec'` green; `grep -n 'autoLayout' docs/ttr-p/architecture/contracts.md` shows the §4 addition + changelog entry.

### T5.2.7 · End-to-end view-state scenario over WS

- [ ] Extend `WsLspTransportSpec` (or new `ViewStateE2ESpec` in `ttrp-designer-server`) with the full lifecycle against the hero: getGraph (autoLayout present) → setLayout (manual snapshot, hero.ttrl fixture) → getLayout (byte-stable) → rename `sales`→`sales2` via LSP → getLayout shows migrated keys → apply a text edit inserting a `sales2` reassignment (didChange) → getLayout flags orphaned `sales2#*` entries + `TTRP-LAY-001` published.
- [ ] Run the TTR-M side once more end-to-end (`pnpm -r test`) — the `TTR.g4` change must not have disturbed TTR-M fixtures.

**Verify:** `./gradlew :packages:kotlin:ttrp-designer-server:test` green **and** `pnpm -r test` green.

## Definition of DONE (stage)

- `.ttrl` parses via `ttr-parser` from the shared `TTR.g4`; TS + Kotlin + TextMate regenerated per CLAUDE.md; both build domains green.
- getLayout/setLayout wholesale rewrite works over stdio and WS; writer emits byte-stable canonical sidecars.
- ζ keys computed; rename rewrites the pair atomically; chain-length change orphans deterministically with visible `TTRP-LAY-001`; the mis-attachment property test passes.
- Auto-layout deterministic (property-tested), abstract-coordinate contract documented; contracts.md §4 updated + changelog entry.

## Blockers

*(record here; STOP on hit)*

## References

- Plan Stage 5.2 · architecture §7 · contracts §1 (pairing), §4 (getLayout/setLayout), §8 (diagnostics)
- Decisions: C1-c-i (ζ + orphaning), C1-c-ii (committed shared truth), C1-c-iii (TTR-M-hosted grammar + inventory), C1-b layout (binary auto/manual, P2), C1-b-iv (derived auto-only), C1-f (Kotlin-side once), H-2c, P2
- v1.1 layout-block source: `docs/v1-1/design/v1.1-packages-and-graphs.md` §6.2/§6.5/§15 · Grammar process: `docs/grammar-master/new-grammar-version-process.md` · CLAUDE.md §Grammar regeneration
