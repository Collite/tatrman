# PL Design — Next Steps (pick-up point, written 2026-07-03, after the F-lite session)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03`](./03-tooling-delivery-options.md) · C0 → [`04`](./04-surfaces-options.md) · C3 → [`05`](./05-canonical-dsl-options.md) · D → [`06`](./06-model-binding-options.md) · E → [`07`](./07-emit-options.md) · F-lite → [`08-orchestration-options.md`](./08-orchestration-options.md).
> Supersedes [`next-steps-260703c.md`](./next-steps-260703c.md).

## Where we are

**A · B · G · C0 · C3 · D · E · F-lite — ALL 🟢. The v1-blocking design set is COMPLETE.** F proper (Kantheon orchestrator, events, FF, on-failure islands, retries/resume, runtime params) is v2/post-v1. Remaining sessions are non-blocking.

## Decisions banked in the F-lite session (2026-07-03)

- **F-a = β**: wave-parallel bash emit (topo waves, `&` + pid-checked `wait`); bash's executor manifest = **FS + SS** (SS = same-wave co-launch); per-island logs.
- **F-b / Q10 DISSOLVED — the headline:** **FF is dropped from v1** (Bora's fifth option, cleaner than all four catalogued). Amends B-T2's v1 scope consciously; `finishes with` stays reserved in the grammar; FF returns with F proper.
- **F-c**: binding table (psql ON_ERROR_STOP / python3 / display = headless file drop + notice); **F-c-i = β Arrow IPC at every staging boundary** (staging format = Q9's fingerprint format — runtime and `pl-conform` agree by construction); **F-c-ii = α `PL_CONN_<NAME>` env-var credentials**.
- **F-d**: fail-fast baseline (`set -euo pipefail`, `wait -n` early wave abort, exit 0/1/2); **cross-container `err` = compile error in v1**; `rejects` unaffected (data-shaped).
- **F-e = α**: no retries/resume; rerun = whole artifact; staging/out wiped at start.
- **F-f (closes E-h)**: bundle ratified (run.sh + manifest + islands/ + transfers/ + schemas/ + plans/); **JSON run manifest**; **semantic world fingerprint** (record-in-artifact / verify-by-capable-invoker); sha256 checksums; no single-file variant.

## What's next (pick one)

1. **H naming sweep — recommended next, it's overdue and now blocks nothing but nags everything.** Accumulated queue: language name; file extensions (program, fragment `.ttrsql`-style, bundle `.plb` placeholder, sidecar suffix); `namespace` (D-d's storage-grouping working name); the extracted translation lib name (E-a); dialect extension names; `PL_CONN_` prefix; Byx/Kyx renames; Display-name conventions. One session, mostly decisive picks — a good palate cleanser.
2. **C1 graphical session**: fragment rendering, view-state sidecar *content* (C3-h gave it a home, not a schema), structured-edit ops (`pl/*` methods), derived-container display for bare fragments, Display transport (the Q11 leftover, → G/E).
3. **MD-sugar session** (D-h's reserved seat): md tier onto D's ref machinery.
4. **C2 fragments / C4 NL sessions** (TTR-SQL & TTR-pandas dialect surfaces; Byx strict grammar + LLM-assist layer).
5. **The consolidation transition**: architecture.md + contracts.md for PL (per planning conventions — the three artefacts before task lists), then the implementation plan. Also queued cross-repo: the **kantheon Proteus-core extraction arc** (planned in kantheon, per E-a).

Suggested order: **H → C1 → consolidation**, with MD-sugar/C2/C4 slotted by appetite — C2 is thin (T5-e pinned most of it), C4 can trail.

## Key mental model to reload (one paragraph)

One graph, typed ports, closed containers with author-assigned engine targets; canonical text = γ hybrid (`->` + SSA), `"""sql` fragments, view-state sidecar; names resolve db+er through the world under `[pl]` defaults, offline. Emit: CTE-per-node SQL, straight-line Polars + prelude, world-driven PlanNode vs dialect payloads, er early + provenance. **Execution (new):** artifact = reviewable directory bundle (JSON manifest, semantic world fingerprint, sha256); `run.sh` = wave-parallel bash (FS + SS only — **FF dropped from v1**); Arrow IPC at every staging boundary (= Q9's format); `PL_CONN_*` env credentials; fail-fast everywhere (no retries, no resume, cross-container `err` illegal); Display = headless file drop. `pl-conform` doubles as invoker + drift guard. v1 = {PG, Polars} × bash. P2 everywhere. **Nothing v1-blocking remains in design — remaining sessions polish surfaces and names, then consolidate to architecture/contracts/plan.**
