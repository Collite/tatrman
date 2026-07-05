# TTR-P Design — Next Steps (pick-up point, written 2026-07-04, after the C4 session)

> Where to resume the **TTR-P** design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03`](./03-tooling-delivery-options.md) · C0 → [`04`](./04-surfaces-options.md) · C3 → [`05`](./05-canonical-dsl-options.md) · D → [`06`](./06-model-binding-options.md) · E → [`07`](./07-emit-options.md) · F-lite → [`08`](./08-orchestration-options.md) · H → [`09`](./09-naming-options.md) · C1 → [`10-graphical-options.md`](./10-graphical-options.md) · C2 → [`11-fragments-options.md`](./11-fragments-options.md) · C4 → [`12-nl-options.md`](./12-nl-options.md).
> Supersedes [`next-steps-260704.md`](./next-steps-260704.md).

## Where we are

**A · B · G · C (all of C0–C4) · D · E · F-lite · H — all 🟢.** Remaining design sessions: **MD-sugar** (D-h's reserved seat — md-tier references in TTR-P); then **consolidation** (architecture.md + contracts.md + plan.md per planning conventions, sweeping all §Leftovers/§Open lists). Both non-blocking; consolidation is the gateway to implementation planning.

## Fork operations status (H-1-bis sweep, unchanged from 260703f)

- ✅ Repo forked · docs → `docs/ttr-p/` · publish plumbing re-pointed · first `kotlin/v0.8.4` publish · kantheon re-pointed · PyPI trusted publisher re-pointed · TTR-M `.ttrl` amendment recorded.
- ⏳ Still pending (trivial): rename `~/Dev/tatrman` → `tatrman-poc`; freeze-notice README on old `Collite/modeler`; `@modeler/*` → `@tatrman/*` npm scope (opportunistic).

## Decisions banked in the C4 session (2026-07-04) — NL / TTR-B

- **C4-a = α · TTR-B = the third fragment dialect**: container content + bare-program kind; the C2 regime inherits wholesale (sentence-wise decomposition, document scope, err-only, single default-out, untouchable interiors, own ANTLR grammar). Interactive command mode = v2 layer over the same grammar.
- **C4-b · full Byx roster + anaphora**: verb synonymy kept; `that/this/it` + implicit = previous result (grammar-resolved); `as <name>`/`call it` = SSA binding; Sort/Limit/Combine added; out-of-roster = named-diagnostic rejects (= the assist repair vocabulary).
- **C4-c = β · verbose expression skin** over the one PL expression grammar (closed synonym table: `is more than`→`>`, `is empty`→`IS NULL`, `is one of`→`IN`); canonical spellings also accepted; never NLP, never fuzzy.
- **C4-d · the assist layer**: emits **canonical TTR-P** (cursor-scoped insertion emits the pointed-at container's dialect; host declares the target); LLM call lives **outside the toolchain** — Tatrman ships `ttrp/assistContext` + `ttrp/validate` (deterministic contracts; generate→validate→repair→review at the host); no mandatory provenance (git diff = review artifact; `[pl] assist-provenance` knob).
- **C4-e · Q1 resolved**: the agent is a **first-class author of canonical TTR-P** via the same contracts; no agent-special surface; kantheon agents consume published artifacts (§7.3), never a running service — G-g softens at exactly this seam. Eval corpus → consolidation.
- **C4-f · `.ttrb` confirmed** (bare, single extension); embeddable, fence tag **`"""ttrb`**.

## Next session (pick by appetite)

1. **MD-sugar (D-h)**: md-tier references in TTR-P — the reserved seat. Cube/dimension/measure refs, what `load(erp.md.sales_cube)`-shaped things mean, the md2db lowering against E-d's provenance pattern, which ops apply (D's "which ops apply to which model" tail).
2. **Consolidation**: architecture.md + contracts.md + plan.md (three artefacts per planning conventions), sweeping: C1/C2/C3/C4/06/07/08 §Leftovers, H leftovers (bundle-dir name, versioning, npm scope), `pl-conform` naming, `PL_CONN_*`→`TTR_CONN_*` residue in docs 07/08, C2 leftovers (LIMIT/OFFSET, `default-imports` key, method rosters, reject-diagnostic tables), C4 leftovers (comment syntax, localization stance, context-bundle format, eval-corpus home, `ttrp/assist*` naming).

## Key mental model to reload (one paragraph)

**Tatrman** = TTR-M + TTR-P, one repo (`Collite/tatrman`). TTR-P: one graph, typed ports, closed containers with author-assigned targets; canonical γ-hybrid text (`->` + SSA) in `.ttrp`. **Three fragment dialects, one regime (C2):** TTR-SQL (one WITH+SELECT query expression), TTR-pandas (method-chain over the op vocabulary), TTR-B (controlled sentences, verbose expression skin) — all decompose fully to the standard node set, document scope reaches in, err-only, single default-out, interiors formatter-untouchable, own ANTLR grammars; bare files `.ttr.sql`/`.ttr.py`/`.ttrb` are valid programs; fences `"""sql`/`"""pandas`/`"""ttrb`. **The LLM lives outside the toolchain**: `ttrp/assistContext` + `ttrp/validate` serve editors and kantheon agents alike (Q1: agents author canonical TTR-P). Graphical: two-level canvas, skins, binary layout, ζ name-keys in `.ttrl`, `ttrp/*` methods, `ttrp/run` → Arrow in `out/`. Emit: CTE-per-node SQL / straight-line Polars + prelude via `org.tatrman:ttr-translator`; world-driven PlanNode-vs-dialect. Execution: `<program>.bundle/`, wave-parallel bash (FS+SS), Arrow staging, `TTR_CONN_*`, fail-fast. v1 = {PG, Polars} × bash. P2 everywhere. **All design sessions done except MD-sugar (polish); consolidation is next.**
