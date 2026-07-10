# TTR-P Optimizer (Z) — Contracts

> **Status:** consolidated 2026-07-07. Source of truth for every Z cross-component boundary: grammar deltas, model deltas, manifest/world/stats schemas, the solver and metadata interfaces, CLI/LSP surfaces, bundle deltas, diagnostics. Companions: [`architecture.md`](./architecture.md), [`plan.md`](./plan.md). Decision IDs reference the control-room log (2026-07-07 Z entries) and GI-n in [`../design/13-optimizer-options.md`](../design/13-optimizer-options.md).
>
> **Changelog** at the end. Changes here require a changelog entry.

---

## 1. Language surface delta (Z-e, amends B-T9; extends TTR-P contracts §3)

```ebnf
containerDecl ::= "container" IDENT [ "together" ] [ placement ] portSig containerBody
placement     ::= "target" engineRef        // DIRECTIVE — must run there (v1 semantics, unchanged)
                | "prefer" engineRef        // HINT — Z may deviate, paying the profile penalty
```

| Spelling | Strength | Cohesion | Z off (GI-22) |
|---|---|---|---|
| `target e` | directive | implied (one island) | as v1 |
| `prefer e` | hint | splittable | **= `target e`** |
| `together prefer e` | hint | atomic | = `target e` |
| `together` | none | atomic | contents = uncontained nodes → project defaults, else `TTRP-OPT-020` |
| *(bare)* | none (grouping only) | splittable | ditto |

Reserved words added: `prefer`, `together`. No node-level placement syntax exists (GI-16-d). No numeric weights in program text (GI-16-e).

**Internal model delta:**

```kotlin
// Container (B-T9 amended):
data class Container(
    // ...existing (name, ports, body, provenance)...
    val target: EngineRef?,                 // null = grouping-only
    val strength: PlacementStrength?,       // DIRECTIVE | HINT; non-null iff target != null
    val together: Boolean,                  // cohesion; DIRECTIVE implies effective cohesion
)
// v1 ingest rule: `target e` → strength = DIRECTIVE, together = false (cohesion implied by directive).
```

## 2. Project manifest — `[ttrp]` additions (extends TTR-P contracts §2)

```toml
[ttrp]
optimize         = "off"          # off | on | pins-only   (v1 toolchain behaves as "off")
optimize-profile = "makespan"     # profile instance qname or builtin name; Z 1.0 ships "makespan" only
optimize-budget  = "balanced"     # fast | balanced | thorough  (Designer user-setting overrides)
optimize-no-metadata = "error"    # error (GI-19 fixed in Z 1.0; key reserved for future policy)
```

Precedence: CLI flag > Designer setting (interactive sessions) > `[ttrp]` > defaults above.

## 3. Engine-type manifest — cost-shape schema (GI-21: TTR-M document; closes T6 "format open")

```ttrm
schema manifest                            // new TTR-M schema kind for engine-type manifests

def engine-type postgres {
    capabilities {                         // T6 parameterized entries (existing design)
        Join      { types: [inner, left, right, full, cross, semi, anti] }
        Aggregate { functions: [sum, count, avg, min, max], distinct: true }
    }
    cost-shapes {                          // NEW — resource vectors per node kind
        Load      { cpu: 0.05/row }
        Load.csv  { cpu: 0.5/row, io: 1/byte }
        Filter    { cpu: 0.2/row }
        Project   { cpu: 0.1/row }
        Join      { cpu: 0.6/row, mem: 32/row }
        Aggregate { cpu: 0.4/row, mem: 24/row }
        Sort      { cpu: 0.8/row }
        Store     { cpu: 0.1/row, io: 1/byte }
    }
}
```

- **Dimensions (Z 1.0):** `cpu` (µs) · `io` (bytes) · `mem` (bytes — **feasibility only**, joins the capacity constraint, never the objective) · `xfer` is edge-borne, computed as `rows × row-width` from schemas+stats, never declared per node.
- **Unit grammar:** `<number>/row` and `<number>/byte`, plus bare `<number>` = fixed term. `/row-in`, `/row-out`, `/group` **reserved, rejected in Z 1.0** with `TTRP-OPT-030`.
- Variant selectors (`Load.csv`) name invocation-relevant sub-shapes; fallback = the bare kind entry.
- A node kind missing from `cost-shapes` but present in `capabilities` gets the **default shape** `{ cpu: 1/row }` + `TTRP-OPT-031` info diagnostic (P2: visible, not silent).

## 4. World-instance schema — calibration, prices, transfer rates, stats (extends `schema world`)

```ttrm
def world dev {
    def engine erp_pg extends postgres {
        calibration { cpu-factor: 1.0, io-rate: "400MB/s", memory: "8GB", concurrency: 4 }
        prices      { }                     # per-resource; empty in Z 1.0 worlds
    }
    def engine polars_w extends polars {
        calibration { cpu-factor: 0.8, memory: "32GB", concurrency: 1 }
    }
    transfer-rates {
        default: "100MB/s"                  # Arrow staging, store+load combined
        (erp_pg_store, files): "150MB/s"    # optional pair overrides
    }
    def storage erp_pg_store extends postgres_storage {
        hosts: [erp]
        stats { table erp.db.accounts { rows: 200000, bytes: 24000000 } }   # inline declared floor (rare-change)
    }
}
```

**Sibling stats doc** (the snapshot serialization — W5-B; refresh-churn home; served and repo backings share this grammar):

```ttrm
def stats dev_stats for dev {
    asOf: "2026-07-01T00:00:00Z"
    table erp_pg_store / erp.db.accounts { rows: 200000,   bytes: 24000000 }
    table files / sales_2026             { rows: 10000000, bytes: 800000000 }
}
```

Precedence (P2-ordered): **served snapshot > sibling stats doc > inline `stats{}` > per-node-kind default constants.** Column-level stats: reserved (`ColumnStats` in §5), not Z 1.0.

## 5. Metadata & stats interface (GI-11/13/18/19; worksheet W2)

```kotlin
/** Z's ONLY window onto the surroundings. Backings: served | repo-read. No engine-instance methods exist. */
interface MetadataSource {
    fun resolveWorld(qname: Qname): ResolvedWorld       // world + type manifests merged with instance overlays
    fun statsSnapshot(): StatsSnapshot?                 // null = declared floor only
}

/** Immutable; taken ONCE at optimize-pass start. */
interface StatsSnapshot {
    val fingerprint: String                             // "sha256:<hex>" over resolved-stats content
    val asOf: Instant                                   // display/staleness only — not part of plan identity
    fun tableStats(ref: PhysicalTableRef): TableStats?  // null = fall through the §4 precedence chain
}

data class TableStats(val rowCount: Long?, val byteSize: Long?,
                      val columns: Map<String, ColumnStats>? = null)   // columns: RESERVED
data class ColumnStats(val ndv: Long?, val nullFraction: Double?)      // reserved with it
```

Semantics: one snapshot per pass · absence is a value (fall through precedence, never an error) · ladder: no `MetadataSource` at pass start ⇒ `TTRP-OPT-001`; source lost mid-session ⇒ held snapshot + `TTRP-OPT-002` · plan identity = f(graph, resolved world, snapshot fingerprint, profile, budget tier).

## 6. Solver interface (Z-g; GI-7)

```kotlin
interface PlacementSolver {
    val id: String                                      // "mincut" | "cpsat" | "heft" | ...
    fun supports(problem: PlacementProblem): Boolean    // e.g. mincut: 2 engines, COST_SUM, no capacities
    fun solve(problem: PlacementProblem, budget: SolverBudget): PlacementSolution
}

data class PlacementProblem(
    val nodes: List<OpNode>,                            // movable + pinned (pin = fixed domain)
    val edges: List<DataEdge>,                          // with transfer-byte estimates
    val precedence: List<PrecedenceEdge>,               // data deps + FS/SS (control preserved, B-T2)
    val engines: List<EngineModel>,                     // calibrated rates, memory, concurrency
    val pins: Map<NodeId, EngineId>,                    // directives (incl. Z-off-degraded prefers)
    val hints: Map<NodeId, Hint>,                       // engine + penalty (from profile)
    val cohesionGroups: List<Set<NodeId>>,              // `together` sets
    val choices: List<RewriteChoice>,                   // Z-f-β variables
    val objective: Objective,                           // AS DATA (Z 2.0-proof): MAKESPAN | COST_SUM + terms
)
data class RewriteChoice(
    val id: ChoiceId, val kind: RewriteKind,            // FILTER_PUSHDOWN | PROJECT_PRUNE | EAGER_AGG | MATERIALIZE_REUSE | MATERIALIZE_INDEX
    val appliesTo: EdgeId,
    val effect: ChoiceEffect,                           // replaced volumes/costs when taken (pre-computed, legality already checked)
)
data class PlacementSolution(
    val assignment: Map<NodeId, EngineId>,
    val takenChoices: Set<ChoiceId>,
    val objectiveValue: Double, val optimalityGap: Double,   // 0.0 = proven optimal
    val infeasible: InfeasibilityReport?,               // null unless TTRP-OPT-011
)
data class SolverBudget(val tier: Tier, val wallMillis: Long, val seed: Long = 0)  // fixed seed ⇒ determinism
```

Selection rule: the registry picks the *first supporting* backend in configured order (`mincut` fast-path → `cpsat` → `heft` last-resort seed-as-answer only under `fast` tier); the chosen backend id is recorded in `explain` and the bundle.

## 7. Bundle manifest delta (extends TTR-P contracts §5; GI-15)

```json
{
  "world":  {"qname": "acme.worlds.dev", "fingerprint": "sha256:..."},
  "stats":  {"fingerprint": "sha256:...", "asOf": "2026-07-01T00:00:00Z"},   // NEW; absent = declared floor
  "optimizer": {                                                             // NEW; absent = Z off
    "version": "1.0", "profile": "makespan", "budget": "balanced",
    "solver": "cpsat", "objectiveValue": 5250.0, "optimalityGap": 0.0,
    "hintDeviations": [{"container": "crunch", "preferred": "erp_pg", "placed": "polars_w", "penalty": 120.0}]
  }
}
```

## 8. Explain payload (`ttrp explain` / LSP `ttrp/explain` — extends S4's result)

```json
{
  "placements": [{"node": "join#1", "engine": "polars_w",
                   "why": "free" | "pin" | "hint-honored" | "hint-deviated", "container": "crunch|<derived-1>"}],
  "islands":    [{"id": "<derived-1>", "engine": "polars_w", "nodes": ["..."], "derived": true}],
  "cuts":       [{"edge": "accounts_f->join#1.left", "via": "files", "bytes": 12000000, "ms": 120}],
  "rewrites":   [{"choice": "EAGER_AGG@sales_f->join#1", "taken": true, "savedMs": 1450}],
  "materializations": [{"after": "accounts_f", "indexed": false, "reason": "cut" | "reuse"}],
  "objective":  {"kind": "makespan", "valueMs": 5250, "criticalPath": ["load_sales", "..."], "gap": 0.0},
  "alternatives": [{"summary": "all-PG", "valueMs": 20110}],
  "fingerprints": {"world": "sha256:...", "stats": "sha256:..."},
  "diagnostics": ["TTRP-OPT-031: Pivot has no cost shape for polars; default 1/row used"]
}
```

The Designer renders derived islands from `islands[].derived` per C1 derived-canvas rules.

## 9. Diagnostics — `TTRP-OPT-*` (house convention: named, stable, suggested alternative, fixture-backed)

| Id | Severity | Meaning |
|---|---|---|
| `TTRP-OPT-001` | error | no metadata source at optimize-pass start ("I have no metadata") |
| `TTRP-OPT-002` | warning | metadata source lost mid-session; using held snapshot (may be stale) |
| `TTRP-OPT-010` | error | graph exceeds the optimizable ceiling (≥100 movable nodes) — pin more, or split the program |
| `TTRP-OPT-011` | error | no feasible placement (capacity): reports the binding constraint ("island X needs 41 GB, polars_w has 32 GB") |
| `TTRP-OPT-020` | error | placement required: target-less contents with Z off and no project default |
| `TTRP-OPT-030` | error | reserved cost-shape unit used (`/row-in` etc.) |
| `TTRP-OPT-031` | info | node kind missing a cost shape; default `1/row` applied |
| `TTRP-OPT-040` | warning | budget exhausted; best-found plan emitted with gap N% |

## 10. Published artifacts

| Artifact | Content | Depends on |
|---|---|---|
| `org.tatrman:ttr-optimizer` | rewrite enumerator, cost model, problem builder, min-cut + HEFT backends, plan applier, explainer | ttrp front-half model, `ttr-metadata` (interfaces) |
| `org.tatrman:ttr-optimizer-cpsat` | CP-SAT backend | `ttr-optimizer`, OR-Tools (native) |

No Calcite dependency in either. Consumers: `ttrp` CLI, TTR-P LSP/Designer server.

---

## Changelog

- **v1 · 2026-07-07** — initial consolidation from workstream Z convergence (GI-1…GI-22, W1–W5).
