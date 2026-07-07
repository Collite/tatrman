# Progress — Phase 5 (Designer server + graphical surface — A4's second surface)

> **Status:** Stages 5.1–5.3 code-complete; Stage 5.4 **automated core** code-complete, with two **human-gated** items outstanding (the T5.4.7 review checkpoint + the T5.4.8 manual acceptance — the v1 A4 exit-criteria gate). Branch `feature/ttr-p-v1-phase5`. `[x]` = intent; the reviewer verifies against runtime (CLAUDE.md cadence).

Deliverable: the hero buildable on a graphical canvas (C1-d bar) against a repo-attached Designer server — TTR-P's second authoring surface (A4).

## Stage 5.1 — Designer server + WS-LSP transport — **code-complete**

Per the MD8 amendment, **no `ttrp-designer-server` module was scaffolded** — the host `ttr-designer-server` already exists (ttr-metadata M3.1). This stage mounts the **same** Phase-4 `TtrpLanguageServer` over WebSocket at `/lsp` beside `/ttrm`:

- **`WsJsonRpcBridge`** — WS text frame ⇄ lsp4j `Content-Length` byte streams, deterministic (P2); unit-tested for split-write reassembly + multi-byte UTF-8 length.
- **`installTtrpLsp()`** — route-only installer on the shared engine (S24 loopback, origin-guarded against CSWSH, reconnect-safe: single *user*, not single *connection*). One server session per connection; project root resolved by walk-up from each document URI.
- **`ttrp/getGraph`** — serializes the **authored** build graph (new additive `PlanResult.authoredGraph`) so the canvas — a second *authoring* surface (A4) — shows `Branch`, not the polars `branch→filter` lowering; plus a derived orchestration overlay (islands/synthesized-transfers/waves) from the collapsed exec graph, cross-engine edges annotated with their transfer ζ. Node ζ identity via `viewstate/ZetaKeys`.
- **`ttrp/getWorld`** — resolved world engines/executors/storages/staging (the Designer target palette).

**Verify:** `./gradlew :packages:kotlin:ttr-designer-server:test :packages:kotlin:ttrp-lsp:test` — `WsLspTransportSpec` (initialize → didOpen hero → getGraph → getWorld → reconnect), `WsJsonRpcBridgeSpec`.

## Stage 5.2 — `.ttrl` sidecar + view state — **code-complete**

- **Grammar (TTR.g4 v4.3, C1-c-iii):** a separate `ttrlDocument` entry rule hosted by the TTR-M grammar (not a fresh `.g4`, not in `TTRP.g4`). Minimal footprint — **2 keyword tokens** (`TTRL`, `CANVAS`, both folded into `idPart`) + a `chains` int-map (the recorded-length orphaning variant); property keys stay generic `id` (skin/mode/nodes/collapsed validated in `TtrlLoader`, not the grammar). Regenerated TS (antlr-ng) + Kotlin (Gradle ANTLR) + TextMate; grammar-version guard 4.2→4.3 + CHANGELOG. No ANTLR ambiguity; TTR-M suites unaffected.
- **ttr-parser / ttr-writer:** `TtrlDocument` AST + `TtrlLoader` (`.ttrl` dispatch, negative shape rules as named errors); `TtrlWriter` — canonical, byte-stable, **wholesale** emission (round-trip + idempotency).
- **ttrp-lsp viewstate:** `ZetaKeys` (canvas-scoped keys + chain lengths), `AutoLayout` (deterministic layered, exact-rational barycenter — run/permutation invariant), `Orphaning` (recorded-chain-length variant — never mis-attach: an inserted mid-chain reassignment orphans the whole name group), `LayoutService` (`getLayout`/`setLayout` wholesale + filename pairing + LAY diagnostics), `SidecarRenameParticipant` (atomic ζ pair rewrite on rename). `ttrp/getGraph` gains the **`autoLayout`** field. New `TTRP-LAY-001..003`.

**Verify:** `./gradlew :packages:kotlin:{ttr-parser,ttr-writer,ttrp-lsp,ttr-designer-server}:test` + `pnpm -r test` (grammar change touches the whole TS toolchain).

## Stage 5.3 — Canvas render (`@tatrman/ttrp-designer`) — **code-complete**

New React 19 + Vite + Cytoscape.js + Tailwind package; connects to `/lsp` and renders **read-only**. Vitest + jsdom + **headless Cytoscape**, zero Playwright.

- **WS client** — reuses the TTR-M designer's protocol-agnostic `JsonRpcWsClient` (renamed `LspRpcError`, added `notify`); typed `LspClient`, tested via injected `FakeWebSocket` (id correlation, one-frame-one-message, didOpen-as-notification).
- **Two-level view** — `deriveOrchestration`/`deriveContainer`/`deriveCanvas` (recursion via one fn) + `view-stack`. `hero-getGraph.json` committed as the frozen 5.1↔5.3 wire contract (dumped from the real `GraphViewBuilder`).
- **Cytoscape adapter (headless-capable) + orientation** (abstract `{layer,index}` → pixels per LR/TD) + **skins** (`alteryx-knime`/`enso`, per-canvas; switching never moves nodes).
- **Binary layout** (`snapshotToManual` flip / `resetToAuto` / orphan badge) + **fragment drill-in read-only** (fragment/derived → read-only + dialect banner; the hero's `acc_prep` `"""sql` proves it).

**Verify:** `pnpm --filter @tatrman/ttrp-designer test build typecheck lint` (30 tests, all green).

## Stage 5.4 — Edit + run — **automated core code-complete; acceptance human-gated**

**Delivered + tested (no browser / live-PG):**
- **Kotlin `ttrp/applyGraphEdit`** (`edit/GraphEditSynthesizer`): closed β op set (unknown ⇒ `TTRP-EDIT-003`, never passthrough) → **formatter-owned** whole-document `WorkspaceEdit` (synthesize minimal γ-hybrid text → `TtrpFormatter` owns placement → determinism is the formatter's). v1 cut = the additive hero-build ops (`createContainer`/`addNode`/`connect`/`assignTarget`); mutating/rename ops return `TTRP-EDIT-003` (rename already via `textDocument/rename` + the 5.2 sidecar-atomic participant). Versioning: stale ⇒ `ContentModified` w/ **`TTRP-EDIT-001`**; fragment/derived ⇒ `EDIT-002`; invalid target ⇒ `EDIT-004`. `GraphEditSynthesisSpec`.
- **New `EDIT` diagnostics** + loopback **`GET /out/{name}`** (path-traversal-guarded; `OutFilesRouteSpec`).
- **Client:** typed β `edit` builders (closed union), versioned stale-replay `edit-queue` (`submitEdits`), diagnostics→node/container badge mapping, `LspClient.applyGraphEdit`/`run`/`isStale`.

**⏸ Human-gated (NOT done by the coding agent):**
- **T5.4.7 review checkpoint** (30 min, with Bora): agree badge placement/aggregation on the hero with an induced error before polish.
- **T5.4.8 manual acceptance** (12-step script): live Postgres + `sales.csv` + browser — build hero from empty canvas → run → Arrow render → terminal parity → A4 conform across PG↔Polars placements. **This is the v1 A4 exit-criteria gate.**
- Live run→Arrow display grid (`apache-arrow`) + interactive canvas gestures (palette drop, port-drag wiring, drag→manual flip, property-panel validate-gate): the pure logic is unit-tested; the DOM handlers + real-Arrow grid land during the acceptance session (they need the live server + real bytes to be meaningful).

## Verification (run by the coder; reviewer re-runs)

- `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-writer:test :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-lsp:test :packages:kotlin:ttr-designer-server:test` — green.
- `./gradlew ...:ktlintCheck` on the touched modules — green.
- `pnpm -r build && pnpm -r typecheck && pnpm --filter @tatrman/parser test` — green (TTR.g4 v4.3 regen did not disturb TTR-M).
- `pnpm --filter @tatrman/ttrp-designer test build typecheck lint` — 30 tests green.

## Notes / deferrals (read before review)

- **Two doc-side changes are NOT mine** — `docs/ttr-p/design/{00,13,14}-*` + `docs/ttr-p/optimizer/` are Bora's parallel optimizer-design work in the same tree; they are intentionally **left unstaged/uncommitted** on this branch (not part of Phase 5).
- The pre-existing `TTR-SEM-201` integration test (did-you-mean nearest) fails on `master` too — untouched here.
- Stage 5.4's applyGraphEdit v1 cut is the additive build path; `removeNode`/`disconnect`/`setProperty`/`bindContainerPorts`/`deleteContainer` synthesis is registered-but-`EDIT-003` pending — a bounded follow-up (the framework, versioning, guards, and formatter-owned output are all in place).
- **A4 exit criteria** ("two surfaces, one graph, identical results") are **met in code** (getGraph is authored-not-lowered; applyGraphEdit round-trips to the same normalized graph via the formatter) but **sealed only by the T5.4.8 manual acceptance** — the last human step of Phase 5.
