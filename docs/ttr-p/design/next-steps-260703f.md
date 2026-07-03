# TTR-P Design — Next Steps (pick-up point, written 2026-07-03, after the C1 session)

> Where to resume the **TTR-P** design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03`](./03-tooling-delivery-options.md) · C0 → [`04`](./04-surfaces-options.md) · C3 → [`05`](./05-canonical-dsl-options.md) · D → [`06`](./06-model-binding-options.md) · E → [`07`](./07-emit-options.md) · F-lite → [`08`](./08-orchestration-options.md) · H → [`09`](./09-naming-options.md) · C1 → [`10-graphical-options.md`](./10-graphical-options.md).
> Supersedes [`next-steps-260703e.md`](./next-steps-260703e.md).

## Where we are

**A · B · G · C0 · C3 · D · E · F-lite · H · C1 — all 🟢.** Remaining design sessions (all non-blocking): **C2** (fragments — thin, T5-e pinned most), **C4** (NL / TTR-B: strict grammar + LLM-assist layer; confirms `.ttrb`), **MD-sugar** (D-h's reserved seat); then **consolidation** (architecture.md + contracts.md + plan.md per planning conventions, sweeping all §Leftovers/§Open lists).

## Fork operations status (H-1-bis sweep)

- ✅ Repo forked (`Collite/tatrman`, full history) · docs → `docs/ttr-p/` · publish plumbing re-pointed (Gradle POMs/URLs, PUBLISHING.md, pyproject, vscode-ext) · **first `kotlin/v0.8.4` publish from the new repo** (after deleting the org-colliding `org.tatrman.*` packages from old modeler — GitHub Packages Maven names are org-unique, 422 otherwise) · **kantheon re-pointed** (URL + `0.8.4` + §7.3; PAT re-granted) · **PyPI trusted publisher re-pointed** (publisher added for `Collite/tatrman`/`publish-python.yml`/env `pypi`, old removed, `pypi` environment recreated in the new repo) · TTR-M `.ttrl` amendment recorded (`docs/v1-1/design/v1.1-packages-and-graphs.md` §15 + contracts v8).
- ⏳ Still pending (trivial): rename `~/Dev/tatrman` → `tatrman-poc`; freeze-notice README on old `Collite/modeler` (archive after all consumers re-point); `@modeler/*` → `@tatrman/*` npm scope (opportunistic).

## Decisions banked in the C1 session (2026-07-03) — the graphical surface

- **C1-a = β · two-level viewing model**: orchestration view (container-collapsed = the derived execution graph + program-level leaves) + drill-into-container; nesting recurses; semantic zoom = v2 polish.
- **C1-b · SKINS**: pluggable per-canvas presentation (built-in v1 set: Alteryx/KNIME-ish icons, Enso-ish text nodes; per-skin edge orientation — data L→R, control T↓); family-wide (TTR-M included); fragment drill-ins = read-only derived sub-graphs; positions survive skin switch.
- **Layout = binary per canvas**: deterministic auto (persists nothing) until first manual rearrange, then fully manual; reset-to-auto; derived canvases auto-only.
- **C1-c-i = ζ node identity**: SSA-qualified name keys (`crunch/sales#2`, `crunch/sums~1`) + atomic pair rewrite on rename + deterministic orphaning (SSA-chain-length change ⇒ orphan, auto-layout, visible diagnostic — never mis-attach). Revisitable; δ (ids in text) = named fallback. Blast radius = manual-canvas positions only (edits address by source range).
- **C1-c-ii/iii · `.ttrl`**: shared truth (committed) v1; TTR-M-hosted grammar (`ttr-parser` parses it; v1.1 layout block promoted to document body); inventory = per-canvas {skin, mode, nodes (manual only), collapsed}; viewport dropped (ephemeral); bendPoints reserved. **The TTR-M migration (§15) now has its concrete target.**
- **C1-d**: β minimal authoring vocabulary (hero buildable on canvas; textual property panel per T5-e) · formatter-owned text placement (deterministic) · LSP document versioning for concurrency · δ internal-only (C3 leftover closed) · methods = `ttrp/*` (incl. `ttrp/run`).
- **C1-e = γ (Q11 fully closed)**: `ttrp/run` shells out to the bundle; display sinks = Arrow IPC files in `out/` (= Q9 fingerprint format); Designer watches + renders at completion; streaming = additive v2.
- **C1-f = γ (G open item closed)**: v1 Designer server is TTR-P-only; **TTR-M converges onto it together with its `.ttrl` migration — one arc** (view-state code written once, Kotlin-side; `modeler/*`→`ttrm/*` rename folds in).

## Next session (pick by appetite)

1. **C2 fragments** (thin): TTR-SQL / TTR-pandas fragment grammars — what T5-e's pin leaves open (statement subset per dialect, name resolution inside fragments against D's rules, rejects/err surfacing in fragment-land, fragment-internal formatter posture).
2. **C4 NL / TTR-B**: strict controlled grammar (Byx evolved) + the LLM-assist layer (world + manifests → generated canonical TTR-P, review flow); confirms `.ttrb`; Q1 (agent as author) gets its concrete shape here.
3. **MD-sugar session** (D-h): md-tier references in TTR-P (the reserved seat).
4. **Consolidation**: architecture.md + contracts.md + plan.md (three artefacts per planning conventions), sweeping: C1/C3/06/07/08 §Leftovers, H leftovers (bundle-dir name, versioning, npm scope), `pl-conform` naming, `PL_CONN_*`→`TTR_CONN_*` residue in docs 07/08.

## Key mental model to reload (one paragraph)

**Tatrman** = TTR-M + TTR-P, one repo (`Collite/tatrman`). TTR-P: one graph, typed ports, closed containers with author-assigned targets; canonical γ-hybrid text (`->` + SSA) in `.ttrp`; fragments `"""sql`/bare `.ttr.sql`; world = `schema world` under `[pl]` project defaults, offline. **Graphical: two-level canvas (orchestration + drill-in), skinned per canvas, binary auto/manual layout, ζ name-keys in a shared-truth `.ttrl` (TTR-M-hosted grammar), β edit vocabulary through formatter-owned WorkspaceEdits over `ttrp/*` (stdio + WS), `ttrp/run` → Arrow files in `out/` → canvas render.** Emit: CTE-per-node SQL / straight-line Polars + prelude via `org.tatrman:ttr-translator`; world-driven PlanNode-vs-dialect. Execution: `<program>.bundle/`, wave-parallel bash (FS+SS), Arrow staging, `TTR_CONN_*`, fail-fast. v1 = {PG, Polars} × bash. P2 everywhere. **All v1-blocking design done; C2/C4/MD-sugar are polish; consolidation is next-but-one.**
