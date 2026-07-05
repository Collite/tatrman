# TTR-P Design — Next Steps (pick-up point, written 2026-07-04, after the C2 session)

> Where to resume the **TTR-P** design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03`](./03-tooling-delivery-options.md) · C0 → [`04`](./04-surfaces-options.md) · C3 → [`05`](./05-canonical-dsl-options.md) · D → [`06`](./06-model-binding-options.md) · E → [`07`](./07-emit-options.md) · F-lite → [`08`](./08-orchestration-options.md) · H → [`09`](./09-naming-options.md) · C1 → [`10-graphical-options.md`](./10-graphical-options.md) · C2 → [`11-fragments-options.md`](./11-fragments-options.md).
> Supersedes [`next-steps-260703f.md`](./next-steps-260703f.md).

## Where we are

**A · B · G · C0 · C3 · D · E · F-lite · H · C1 · C2 — all 🟢.** Remaining design sessions (all non-blocking): **C4** (NL / TTR-B: strict grammar + LLM-assist layer; confirms `.ttrb`; Q1's concrete shape), **MD-sugar** (D-h's reserved seat); then **consolidation** (architecture.md + contracts.md + plan.md per planning conventions, sweeping all §Leftovers/§Open lists).

## Fork operations status (H-1-bis sweep, unchanged from 260703f)

- ✅ Repo forked (`Collite/tatrman`, full history) · docs → `docs/ttr-p/` · publish plumbing re-pointed · first `kotlin/v0.8.4` publish from the new repo · kantheon re-pointed (URL + `0.8.4` + §7.3; PAT re-granted) · PyPI trusted publisher re-pointed · TTR-M `.ttrl` amendment recorded (v1.1 §15 + contracts v8).
- ⏳ Still pending (trivial): rename `~/Dev/tatrman` → `tatrman-poc`; freeze-notice README on old `Collite/modeler` (archive after all consumers re-point); `@modeler/*` → `@tatrman/*` npm scope (opportunistic).

## Decisions banked in the C2 session (2026-07-04) — the fragment dialects

- **C2-a = β · full decomposition**: fragment text parses to the standard PL node set (clause-wise SQL, statement-wise SSA pandas); CTE name = SSA label (E-b's CTE-per-node emit in reverse); unmappable ⇒ named-diagnostic reject (P2); fragments are leaf content (no containers inside).
- **C2-b = α · TTR-SQL workhorse cut**: one query expression per fragment (`WITH` + final `SELECT`); full clause table in `11`; `EXISTS`/`IN` desugar to semi/anti Join (only subquery forms); reject list = DML/DDL, vendor syntax, scalar subqueries, window fns, procedural, multi-statement; PIVOT authoring canonical-only. SQL position carries ports (b-ii); `SELECT *` expands statically (b-iii).
- **C2-c = β · TTR-pandas = method-chain over the PL op vocabulary** ("dataframe-shaped TTR-P"): `.filter(amount > 0 and …)`, PL expressions bare, SSA reassignment; Python-*looking* (`.ttr.py` highlighting), not Python-parseable. Single default-out v1, both dialects (c-i); assignment + final chain only (c-ii). Literal-pandas and Polars-API rejected on T5-e contact.
- **C2-d = α · document scope flows in**: in-ports > document imports > qnames; ambiguity = error; er refs + `on: relation` work in fragment-land (provenance through, E-d); expression scope = input columns only; bare fragments use project defaults as implicit prelude.
- **C2-e = α · err only in v1**: signal port free (fail-fast per F-d); no rejects-producing dialect syntax until an SQL-side producer semantics exists (E/T8 work item).
- **C2-f = α · formatter never touches fragment interiors** (embedded or bare); fragment trivia verbatim; `sourceText` canonical, parse derived.
- **C2-g = α · own ANTLR grammars** (`TTRSql.g4`, `TTRPandas.g4`, antlr-ng/Kotlin, sized to subsets); Calcite stays at the emit boundary.

## Next session (pick by appetite)

1. **C4 NL / TTR-B**: strict controlled grammar (Byx evolved) + the LLM-assist layer (world + manifests → generated canonical TTR-P, review flow); confirms `.ttrb`; Q1 (agent as author) gets its concrete shape here.
2. **MD-sugar session** (D-h): md-tier references in TTR-P (the reserved seat).
3. **Consolidation**: architecture.md + contracts.md + plan.md (three artefacts per planning conventions), sweeping: C1/C2/C3/06/07/08 §Leftovers, H leftovers (bundle-dir name, versioning, npm scope), `pl-conform` naming, `PL_CONN_*`→`TTR_CONN_*` residue in docs 07/08, C2 leftovers (LIMIT/OFFSET, `default-imports` key, method-name roster, reject-diagnostic tables).

## Key mental model to reload (one paragraph)

**Tatrman** = TTR-M + TTR-P, one repo (`Collite/tatrman`). TTR-P: one graph, typed ports, closed containers with author-assigned targets; canonical γ-hybrid text (`->` + SSA) in `.ttrp`; fragments `"""sql`/bare `.ttr.sql`; world = `schema world` under `[pl]` project defaults, offline. **Fragments: TTR-SQL = one WITH+SELECT query expression, TTR-pandas = method-chain over the op vocabulary; both decompose fully to the standard node set (CTE/variable names = SSA labels); document scope reaches in; err-only, single default-out; interiors formatter-untouchable; own ANTLR grammars.** Graphical: two-level canvas, skins, binary auto/manual layout, ζ name-keys in a shared-truth `.ttrl`, β edit vocabulary over `ttrp/*`, `ttrp/run` → Arrow files in `out/` → canvas render. Emit: CTE-per-node SQL / straight-line Polars + prelude via `org.tatrman:ttr-translator`; world-driven PlanNode-vs-dialect. Execution: `<program>.bundle/`, wave-parallel bash (FS+SS), Arrow staging, `TTR_CONN_*`, fail-fast. v1 = {PG, Polars} × bash. P2 everywhere. **All v1-blocking design done; C4/MD-sugar are polish; consolidation is next.**
