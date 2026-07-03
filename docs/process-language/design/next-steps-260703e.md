# TTR-P Design — Next Steps (pick-up point, written 2026-07-03, after the H session)

> Where to resume the **TTR-P** (né "PL") design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03`](./03-tooling-delivery-options.md) · C0 → [`04`](./04-surfaces-options.md) · C3 → [`05`](./05-canonical-dsl-options.md) · D → [`06`](./06-model-binding-options.md) · E → [`07`](./07-emit-options.md) · F-lite → [`08`](./08-orchestration-options.md) · H → [`09-naming-options.md`](./09-naming-options.md).
> Supersedes [`next-steps-260703d.md`](./next-steps-260703d.md).

## Where we are

**A · B · G · C0 · C3 · D · E · F-lite · H — all 🟢.** Remaining design sessions are non-blocking: C1 (graphical), C2 (fragments — thin), C4 (NL / TTR-B), MD-sugar; then consolidation (architecture.md + contracts.md + plan).

## Decisions banked in the H session (2026-07-03)

- **The name: Tatrman** ("table transformation manager") = the product; **TTR** = the family; **TTR-M** (modeling) · **TTR-P** (processing) · **TTR-B** (ex-Byx, strict NL); TTR-SQL/TTR-pandas unchanged. "PL" retires (historical in docs 00–08).
- **The fork: modeler → `Collite/tatrman`** (`~/Dev/collite/tatrman`), clone with **full history**; old repo freezes → archives after consumers re-point (ai-platform precedent). Consequence sweep in `09` (kantheon Maven URL + repo-scoped PAT + §7.3 text; first-publish-then-repoint; `~/Dev/tatrman` PoC → `tatrman-poc`; `@modeler/*` → `@tatrman/*` opportunistic; docs → `docs/ttr-p/`).
- **Extensions**: `.ttrp` programs · `.ttr.sql`/`.ttr.py` fragments (double ext — free foreign-editor highlighting) · `.ttrb` reserved (TTR-B) · bundle `<program>.bundle/` (proposed) · `.ttrm`/`.ttrg` stand.
- **One `.ttrl` layout/view-state sidecar FAMILY-WIDE** — TTR-M migrates off the v1.1 in-file layout block (extends C3-h; TTR-M-side amendment to record post-fork). One pair-integrity toolset for both languages.
- **`org.tatrman:ttr-translator`** (Kotlin root `org.tatrman.translator.*`) · **`namespace`** confirmed · **`TTR_CONN_<NAME>`** replaces `PL_CONN_`.

## Immediate next — the fork (operational, before more design)

1. Create `Collite/tatrman` on GitHub; `git clone` modeler → push (full history).
2. First `kotlin/v*` publish from the new repo (packages must exist before kantheon re-points).
3. Kantheon side: re-point `settings.gradle.kts` Maven URL, re-grant the `gpr.*` fine-grained PAT to the new repo, update CLAUDE.md §7.3 — fold into the queued Proteus-extraction arc.
4. Housekeeping in the new repo: docs folder → `docs/ttr-p/`, record the TTR-M `.ttrl` amendment in the TTR-M docs, rename `~/Dev/tatrman` → `tatrman-poc`, freeze old modeler (README notice).
5. Design sessions continue **in the new repo**.

## After the fork (pick by appetite)

1. **C1 graphical session**: fragment rendering, `.ttrl` sidecar *content schema* (now serving both languages — design once), structured-edit ops (`pl/*` → `ttrp/*` methods?), derived-container display, Display transport (Q11 leftover).
2. **MD-sugar session** (D-h's reserved seat).
3. **C2 fragments** (thin — T5-e pinned most) · **C4 NL / TTR-B** (grammar + LLM-assist layer; confirms `.ttrb`).
4. **Consolidation**: architecture.md + contracts.md + plan.md (planning conventions' three artefacts), incl. H leftovers (bundle-dir name, versioning stance, npm scope).

## Key mental model to reload (one paragraph)

**Tatrman** = TTR-M (modeling) + TTR-P (processing) in one repo (`Collite/tatrman` post-fork). TTR-P: one graph, typed ports, closed containers with author-assigned engine targets; canonical text = γ hybrid (`->` + SSA) in `.ttrp` files, `"""sql` fragments or bare `report.ttr.sql` files, `.ttrl` view-state sidecars family-wide. Names resolve db+er through the world (`schema world` in `.ttrm`) under `[pl]`-table project defaults, offline. Emit: CTE-per-node SQL / straight-line Polars + prelude via `org.tatrman:ttr-translator` (Proteus core extracted here); world-driven PlanNode-vs-dialect payloads. Execution: `<program>.bundle/` (JSON manifest, semantic world fingerprint), wave-parallel bash (FS+SS; FF dropped v1), Arrow staging, `TTR_CONN_*` credentials, fail-fast. `pl-conform` (rename pending?) = A4's teeth. v1 = {PG, Polars} × bash. P2 everywhere. **All v1-blocking design done; fork, then surfaces polish, then consolidate.**
