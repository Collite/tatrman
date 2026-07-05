# Tasks · P5 · Stage 5.4 — Edit + run (hero-on-canvas)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The C1-d bar cleared: **the hero scenario built on canvas from empty**, run via `ttrp/run`, results rendered from Arrow files in `out/` (C1-e γ). β edit vocabulary → `ttrp/applyGraphEdit` → **formatter-owned** `WorkspaceEdit`s (C1-d-ii), LSP document versioning with stale-reject + client replay (C1-d-iii, contracts §4), textual property panel, canvas diagnostics as **node badges at both levels** (the C1 leftover, decided in-stage with a review checkpoint). Completing this stage = **v1 A4 exit criteria fully met** (plan Phase 5 DONE line).

**The β edit vocabulary (C1-d-i, enumerated — this is the closed op set; everything else → text):**

| op | payload sketch |
|---|---|
| `addNode` | `{canvas, kind, name?, afterZeta?}` — from the T10 palette |
| `removeNode` | `{zeta}` |
| `connect` | `{from: port-ref, to: port-ref}` — data edge; cross-container = just a wire, movement synthesis is the compiler's (C3-d-iv) |
| `disconnect` | `{from, to}` |
| `addControlEdge` | `{kind: "after"\|"with", a, b}` — FS/SS drawable (C3-e) |
| `createContainer` | `{name, target, dialect?}` — assign target/dialect at creation |
| `deleteContainer` | `{path}` |
| `assignTarget` | `{path, target}` |
| `bindContainerPorts` | `{path, ports: {in[], out[], err?}}` |
| `renameVariable` | `{zeta, newName}` — triggers the ζ atomic pair rewrite (5.2) |
| `setProperty` | `{zeta, property, valueText}` — args/expressions as text via the one expression grammar (T5-e) |

## Pre-flight (all must pass before T5.4.1)

- [ ] Stage 5.3 DONE (`pnpm --filter @tatrman/ttrp-designer test` green against the frozen fixtures).
- [ ] Formatter from Stage 4.2 green (formatter-owned placement builds on it); `ttrp build`/`ttrp run` work on the canonical hero (Phase 3): bundle produced, exit 0, `out/*.arrow` files drop.
- [ ] `TTR_CONN_*` env vars for the hero world available in the shell that will host the Designer server (contracts §5 credentials rule).

## Tasks

### T5.4.1 · TDD Kotlin: edit-synthesis specs (red)

- [ ] `packages/kotlin/ttrp-lsp/src/test/kotlin/.../GraphEditSynthesisSpec.kt` (Kotest): for **every** op in the β table above, `{uri, version, edits[]}` → the returned `WorkspaceEdit`, applied to the source, yields **byte-exact expected canonical text** (golden strings in the spec — same edit ⇒ same text, P2/C1-d-ii). Placement rules under test (C1-d-ii verbatim): new node → after its upstream's defining statement in the target container; new container → after the last container; wiring → the program-level wiring section.
- [ ] Determinism case: apply the same edit list twice from the same base text → identical `WorkspaceEdit`s.
- [ ] Versioning cases (C1-d-iii): stale `version` ⇒ error response (named diagnostic `TTRP-EDIT-001` "stale document version — re-pull and replay", contracts §8 convention); current version ⇒ success.
- [ ] Invalid-op cases: unknown zeta, connect to an occupied single-in port, delete of a referenced container → named diagnostics, no partial edits (all-or-nothing per request).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests '*GraphEditSynthesisSpec'` — red for missing implementation, compiles clean.

### T5.4.2 · Kotlin: `ttrp/applyGraphEdit` — β ops → formatter-owned WorkspaceEdits

- [ ] `packages/kotlin/ttrp-lsp/src/main/kotlin/org/tatrman/ttrp/lsp/edit/GraphEditSynthesizer.kt`: decode the β op set (closed enum — unknown op = error, not passthrough), synthesize **minimal canonical statements**, delegate insertion position + style to the Stage-4.2 formatter (the formatter owns placement — the synthesizer never concatenates strings into final positions itself, C1-d-ii). δ node/edge surface form stays **internal-only** (C1-d-iv): edits emit γ-hybrid canonical text, nothing else.
- [ ] Register `ttrp/applyGraphEdit {uri, version, edits[]}` → `WorkspaceEdit` (contracts §4); version check against the live document; `renameVariable` routes through the 5.2 atomic pair rewrite (sidecar keys migrate in the same operation).
- [ ] Fragment guard: edits targeting a `derived: true` container or a fragment interior are rejected (`TTRP-EDIT-002`) — fragment interiors are formatter-untouchable and read-only on canvas (C2-f, C1-b-iv).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests '*GraphEditSynthesisSpec'` green.

### T5.4.3 · TDD TS: edit-flow, property-panel, run/Arrow fixtures (red)

- [ ] `src/__tests__/fixtures/big_customers.arrow` — generate once in `src/test-setup.ts` (or a committed binary) via `apache-arrow`: `tableToIPC(tableFromArrays({ customer: ["acme", "globex"], total: [12500.0, 18400.0] }), 'file')` — the canned display sink.
- [ ] Failing Vitest suites:
  - `src/__tests__/edit-flow.test.tsx` — palette add-node dispatch → mock client receives `applyGraphEdit` with correct op payload + current version; on success the Designer re-pulls graph + layout; on `TTRP-EDIT-001` (stale) it re-pulls and **replays the same edits** against the new version (C1-d-iii), asserted by a second `applyGraphEdit` call with bumped version.
  - `src/__tests__/property-panel.test.tsx` — select node → panel shows textual args/expression fields; edit + commit → `setProperty` op; invalid text (mock `ttrp/validate` returning a diagnostic) → inline error shown, **no edit sent**.
  - `src/__tests__/run-display.test.tsx` — run button → `ttrp/run {uri, version}`; mock result `{runId, exitCode: 0, out: ["out/big_customers.arrow"]}` → display panel fetches the fixture bytes, `tableFromIPC` parses, grid shows 2 rows × 2 cols; `exitCode: 1/2` → error surface with the exit-contract meaning (contracts §5).
  - `src/__tests__/diagnostic-badges.test.tsx` — published diagnostics with source ranges map to nodes: drill-in shows per-node badges; orchestration shows the **aggregated count badge on the container**; badge click lists messages.

**Verify:** `pnpm --filter @tatrman/ttrp-designer test` — four new suites red for want of implementation.

### T5.4.4 · Designer edit UX: palette, wiring, containers, versioned replay

- [ ] `src/components/Palette.tsx`: node roster from T10 (Load, Store, Filter, Branch, Switch, Join, Aggregate, Sort, Union, Intersect, Except, Values, Limit, Pivot, Distinct, Display) + "New container" (name + target picker fed by `ttrp/getWorld` engines + optional dialect); drop onto canvas → `addNode`/`createContainer` (position is presentational only — text placement is the formatter's, C1-d-ii).
- [ ] `src/edits/graph-edits.ts`: typed builders for all 11 β ops (the table above is the type definition — closed union); `src/edits/edit-queue.ts`: serialize edits, attach document version, on stale error re-pull graph+layout and replay verbatim (bounded: 3 attempts then surface an error toast).
- [ ] Canvas gestures: port-to-port drag → `connect` (cross-container wires allowed from the orchestration view — edit locality per C1-a consequence 3: program wiring top level, island edits in drill-in; enforce it — `addNode` into a container only from its drill-in); modifier-drag → `addControlEdge` after/with; delete key → `removeNode`/`disconnect`/`deleteContainer`; double-click node name → `renameVariable` inline.
- [ ] `err`/`rejects` ports: render as always-visible stubs on nodes that have them (v1 call for the C1 leftover; unconnected = fail-fast, F-d — tooltip says so).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- edit-flow` green.

### T5.4.5 · Textual property panel

- [ ] `src/components/PropertyPanel.tsx` (evolve the forked `InspectorPanel.tsx`): selected node → name, kind, target (containers), and **textual** fields for args/expressions — by design textual (C1-d-i): snippets lift through the one expression grammar (T5-e); no expression builder UI in v1.
- [ ] Pre-commit gate: candidate text through `ttrp/validate {source, dialect?}` (contracts §4) — diagnostics render inline with their suggested alternatives (contracts §8); only valid text dispatches `setProperty`.
- [ ] Provenance display: er-origin names shown in node detail per E-d (read from getGraph provenance; the display-default knob D-e is out of scope — show physical + er name when present).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- property-panel` green.

### T5.4.6 · Run + `out/` watch + Arrow render (C1-e γ)

- [ ] Server side (small `ttrp-designer-server` addition): loopback HTTP GET route `get("/out/{name}")` on the 5.1 Ktor server serving files **only** from the current program's bundle `out/` directory (path-traversal guarded; the browser cannot read the filesystem — this route is the "Designer watches `out/`" transport; loopback-only per S24). Record the route in `contracts.md` §4 notes + changelog entry.
- [ ] Client: Run button → `ttrp/run {uri, version}` → on `{runId, exitCode, out[]}` completion, fetch each `out/` entry over the route and render (C1-e: one execution path — Designer-run and terminal-run indistinguishable; render at completion, no streaming in v1).
- [ ] `src/components/DisplayPanel.tsx`: Arrow reader = **`apache-arrow` npm** (confirmed current: `tableFromIPC(new Uint8Array(bytes))` → `Table`; rows via `table.toArray()`, columns via `table.schema.fields`); side-panel data grid per display sink (named tabs — `big_customers`, `problems`), row count + schema header; non-zero exit → stderr tail + exit-contract explanation (0 ok · 1 island failure · 2 pre-flight).
- [ ] Kotest: `ttrp/run` over WS on the hero returns `out[]` matching the bundle manifest `displays` (extend `WsLspTransportSpec`; requires the Phase-3 executor working in the test env — if PG isn't available in-process, gate this one case behind the dockerized-PG CI tag used by `ttrp-conform`).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- run-display` green; `./gradlew :packages:kotlin:ttrp-designer-server:test` green.

### T5.4.7 · Canvas diagnostics = node badges at both levels ⚠ includes review checkpoint

In-stage decision (the C1 leftover, per plan): diagnostics render as **node badges at both view levels** — per-node badge in drill-in; aggregated per-container badge (count, max severity) in the orchestration view; program-level diagnostics (world, movement, staging) badge the orchestration canvas edge/leaf they attach to, else a canvas-corner indicator.

- [ ] Map `publishDiagnostics` ranges → nodes via getGraph source ranges (`src/graph/diagnostics-map.ts`); severity coloring; badge click → message list with diagnostic ids + suggested alternatives (contracts §8). Covers: unresolved refs, T5-b split warnings, T6 capability errors, rls-egress tripwires (Q8), `TTRP-LAY-001` orphan badges (5.3 already renders these — unify the badge component).
- [ ] `diagnostic-badges.test.tsx` green (both levels, aggregation, click-through).
- [ ] **⏸ REVIEW CHECKPOINT (30 min, with Bora, BEFORE polish):** demo the badge behavior on the hero with an induced error (drop a wire, add an unsupported function for the target). Agree: badge placement/aggregation as implemented, or adjust. Record the outcome here as a dated note; only then proceed to T5.4.8. **Do not skip; do not polish first.**

**Verify:** `pnpm --filter @tatrman/ttrp-designer test -- diagnostic-badges` green **and** the checkpoint note recorded below this task.

### T5.4.8 · Hero-on-canvas manual acceptance (the C1-d bar) — stage DONE gate

Manual QA script — run it start to finish; every step checked = Phase 5 DONE and **v1 A4 exit criteria met**. Environment: hero project checkout with world + model fixtures; `TTR_CONN_ERP_PG` + `TTR_CONN_FILES` exported; PG up with the accounts fixture; `sales.csv` in place.

- [ ] 1. Start the server: `./gradlew :packages:kotlin:ttrp-designer-server:run --args='9257 <hero-project-root>'` — log shows loopback/no-auth notice (S24).
- [ ] 2. `pnpm --filter @tatrman/ttrp-designer dev` → open `http://localhost:5173`; create/open **empty** `hero_canvas.ttrp`; orchestration canvas is empty; skin = alteryx-knime.
- [ ] 3. Palette → New container `db_prep`, target `erp_pg` (target list came from `ttrp/getWorld`). Drill in: add `load` (pick `erp.db.accounts` — model objects resolve), add `filter`; property panel: filter predicate `active = true` (validated before commit); wire load → filter; bind container out port `accounts`.
- [ ] 4. Back to orchestration → New container `crunch`, target the Polars engine. Drill in: add `load` for `sales.csv` (declared schema per D-c), `join` (named ports `left:`/`right:`, C3-c), `aggregate` (`by: customer`, `total: sum(amount)`), `branch` (threshold predicate); bind ports `in accounts`, `out high`, `out low`, `err problems`.
- [ ] 5. Orchestration: wire `db_prep.accounts → crunch.accounts` — **a synthesized Transfer appears on the canvas** (movement synthesis, C3-d-iv — you drew one wire); wire `crunch.high → display(big_customers)`, `crunch.low → store(…)`, `crunch.problems → display(problems)` (the error path).
- [ ] 6. Open `hero_canvas.ttrp` in a text editor: formatter-owned canonical γ-hybrid text, sensibly placed (nodes after upstream statements, wiring section at program level); repeat one edit (delete + re-add the same wire) → **byte-identical** text (P2/C1-d-ii).
- [ ] 7. Drag a node in orchestration → canvas flips to manual; `hero_canvas.ttrl` appears with ζ keys + `mode: manual`; reset-to-auto restores; re-drag for the run.
- [ ] 8. Induce an error (disconnect `crunch.accounts`): badges at both levels (container badge top, node badge in drill-in); fix it; badges clear.
- [ ] 9. **Run** → exit 0; DisplayPanel renders `big_customers` from `out/big_customers.arrow` (aggregated rows visible, correct totals) and `problems` (empty or the error rows, per fixture data).
- [ ] 10. Terminal parity (C1-e): `ttrp run hero_canvas.ttrp` from a shell → identical `out/` results; Designer-run and terminal-run indistinguishable.
- [ ] 11. A4 seal: `ttrp explain hero_canvas.ttrp` vs `ttrp explain <canonical hero>.ttrp` → same normalized island/wave/movement structure; run `ttrp conform` across the PG↔Polars placement variants → green. **Two surfaces, one graph, identical results.**
- [ ] 12. Record the acceptance run (date, commit sha, any deviations) in `docs/ttr-p/implementation/v1/progress-phase-05.md`.

**Verify:** all 12 boxes checked in one uninterrupted session; deviations recorded as blockers, not worked around.

## Definition of DONE (stage)

- All β ops synthesize deterministic formatter-owned text (golden Kotest suite); stale-version reject + replay proven both sides.
- Property panel textual + validate-gated; run→Arrow render works from canned fixtures (Vitest) and live (manual script).
- Diagnostics badges at both levels shipped **after** the Bora checkpoint.
- T5.4.8 manual script fully checked — **v1 A4 exit criteria met at the end of this phase** (plan Phase 5 DONE line).

## Blockers

*(record here; STOP on hit)*

## References

- Plan Stage 5.4 · contracts §4 (applyGraphEdit/run/validate), §5 (exit contract, displays), §8 (diagnostics)
- Decisions: C1-d-i (β vocabulary), C1-d-ii (formatter-owned placement), C1-d-iii (versioning + replay), C1-d-iv (δ internal-only), C1-e γ (run + Arrow + `out/` watch), C1-a (edit locality), C3-d-iv (movement synthesis), T5-e (one expression grammar), S24, P2, A4/A5 (hero + exit criteria)
- Arrow JS reader: `apache-arrow` npm — `tableFromIPC` / `Table` (confirmed via docs 2026-07)
