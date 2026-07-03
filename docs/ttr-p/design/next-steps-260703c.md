# PL Design — Next Steps (pick-up point, written 2026-07-03, after the E session)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03`](./03-tooling-delivery-options.md) · C0 → [`04`](./04-surfaces-options.md) · C3 → [`05`](./05-canonical-dsl-options.md) · D → [`06`](./06-model-binding-options.md) · E → [`07-emit-options.md`](./07-emit-options.md).
> Supersedes [`next-steps-260703b.md`](./next-steps-260703b.md).

## Where we are

**A 🟢 · B 🟢 · G 🟢 · C0 🟢 · C3 🟢 · D 🟢 · E 🟢.** One 2026-07-03 mega-session converged C3 + D + E. Remaining: **F-lite** (the last v1-blocking workstream), **C1/C2/C4** (graphical, fragments, NL sessions), **H** (naming sweep — now overdue), the parked **MD-sugar session**, Z (v2).

## Decisions banked in the E session (2026-07-03)

- **E-a = α′ extended — the load-bearing one:** the **Proteus translation core moves to modeler** as a published `org.tatrman` library; kantheon's Proteus becomes a thin wrapper (the metadata/Ariadne pattern, Q6's sibling). PL compiler embeds it; compiles offline. **Artifacts carry concrete payloads** (final SQL + scripts); **PlanNode emission is world-driven** — world says Kantheon ⇒ invocation binding delivers `plan.v1` plans; otherwise dialect SQL. **Cross-repo: plan a kantheon extraction arc** (Proteus core → modeler lib) — belongs in the kantheon repo's planning, not here.
- **E-b**: **CTE-per-node** preserved-shape SQL; SSA variable names survive as CTE names; flat-SELECT rule for trivial islands.
- **E-c = γ**: straight-line dataframe scripts + **generated inline prelude** (only needed helpers; artifacts dependency-free). **v1 engines = {Postgres, Polars}**; pandas *engine* v1.x (TTR-pandas *dialect* unaffected — dialect ≠ engine).
- **E-d = γ**: er rewrite **early** (T8 sugar stratum) **+ mandatory provenance** (diagnostics/graphical/lineage speak the author's names).
- **Q9 RESOLVED**: seven-point equivalence procedure (Arrow fingerprint; multiset rows, order only under terminal Sort; NULLS LAST emitted; decimal exact + declared float tolerance; UTC µs; binary collation; `pl-conform` harness over Arrow IPC).
- **Q8 RESOLVED**: v1 = **trusted principal + tripwire** (`rls: true` storage flag → egress warning; `[pl] rls-egress = warn|error`; row-filter propagation explicitly v2).
- **E-g (amends B-T9)**: **Transfer generalized** — abstract movement node, delivery = invocation binding per (storage, executor) manifest pair; Charon is its Kantheon-world binding; native tools bind it in bash-land.
- **E-h** (artifact bundle shape) deliberately deferred **into F-lite** — it is the executor's artifact.

## Immediate next — F-lite (the last v1-blocking design)

Mostly pre-answered by T6/E; the agenda:
1. **The bash executor emit**: how FS/SS/FF become script structure (FS = sequential; SS = `&` + coordinated start?; FF = **Q10** — define what atomic co-finish *guarantees* under bash: staging + swap + compensation, or v1 restricts FF to same-engine outputs).
2. **Artifact bundle finalization (E-h)**: directory layout, run manifest, world-fingerprint pinning (T6 runtime-compatibility verification hook), checksums.
3. **Invocation bindings concretely**: the (data engine, execution engine) pair table for v1 — pg×bash (inline psql), polars×bash (python script), display×bash (what does Display do in a headless run — file drop + notice?).
4. **Failure semantics**: v1 fail-fast confirmed; what "fail" means per island (exit codes, psql ON_ERROR_STOP); `err`/`rejects` port behavior at the orchestration level.
5. **Retries/resume**: v1 = none (fail-fast), or minimal re-run-from-staging? Record it.
6. Q1's runtime half if it surfaces (agent as author → LLM-assist path already covers; agent as *invoker* of compiled artifacts is F/Kantheon-path, post-v1).

**After F-lite:** the design phase's v1-blocking set is complete → sensible next moves: **H naming sweep** (accumulated: language name, file extensions, sidecar suffix, `namespace`, the extracted translation lib, dialect extensions, Byx/Kyx renames) · **C1 graphical session** (fragment rendering, view-state sidecar content, structured-edit ops) · **MD-sugar session** (md tier onto D's reserved seat) · then the transition to architecture.md/contracts.md consolidation and implementation planning (per the planning conventions — the three artefacts before task lists).

## Key mental model to reload (one paragraph)

One graph, typed ports, closed containers with author-assigned engine targets; canonical text = γ hybrid (`->` chains + SSA names), `"""sql` tagged fragments, view-state sidecar. Names resolve db+er (package-derived, imports, relation-joins) through the world (`schema world`, storages `hosts:` models, `staging: true`) under `[pl]` project defaults — offline, via the embedded metadata component. **Emit is now decided end-to-end:** the normalizer's islands lower in-process to Calcite RelNode via the **Proteus-core-extracted-to-modeler** library; SQL islands emit **CTE-per-node** preserved-shape dialect SQL (SSA names as CTE names), dataframe islands emit **straight-line Polars + generated prelude** enforcing canonical NULL; er desugars early with provenance; Transfer/movement/Display all deliver per **world-driven invocation bindings** (Kantheon target ⇒ PlanNode plans; bash ⇒ scripts + native copy tools). **Q9's `pl-conform`** (Arrow-based, seven points) is A4's teeth; **Q8** = trusted principal + rls tripwire. v1 engines {PG, Polars} + bash executor. P2 everywhere. Last v1-blocking design: **F-lite**.
