# T8 normalizer — termination measure + node-fission rules

> **Status:** approved 2026-07-06 by Bora (Stage 2.3a T2.3a.2 review gate — "approve, proceed"). Authored 2026-07-06.
> This is the design-note-first artifact the plan requires before the T8 fixpoint is coded (B-T10 sweep / T6-d named work items). It **restates** the strata order fixed by prior decisions and **proposes + defends** the strictly-decreasing measure and the node-fission rules.

## 1. Strata order (fixed by prior decisions — restated, not reinvented)

The normalizer runs a fixed sequence of strata; each runs to local fixpoint before the next starts (T5-b order + T6-d "sugar ≺ function-lowering ≺ node-fission ≺ re-placement"):

1. **authoring-sugar expansion** — engine-independent (Select/Calc/Distinct/HAVING). Fires regardless of manifests (B-T10). *(2.3a)*
2. **function capability-lowering** — engine-relative expression rewrites with a compiler-owned table (`between`→`>=/<=`, `coalesce`→`case` where unsupported). *(2.3a)*
3. **node capability-lowering** — engine-relative node rewrites (Branch→Filter×2, Switch→Filter-chain, Intersect/Except→semi/anti Join, right-Join→left-Join+swap, Pivot→CASE-Aggregate). *(2.3a)*
4. **node fission** — split a mixed-miss Project/Calc so only the unsupported-function slice is a re-placement candidate. *(2.3b)*
5. **whole-node re-placement** — a surviving miss moves the node to a capable engine (split-with-warning, `[ttrp] split-policy`). *(2.3b)*
6. **movement synthesis** — every remaining cross-engine data edge → Store+Transfer+Load via staging. *(2.3b)*
7. **container-collapse** — derive the execution graph + waves. *(2.3b)*

Strata 1–3 land in Stage 2.3a; 4–7 in Stage 2.3b.

> **RJ-P1 amendment (rejects producer).** A stratum **0 — reject-elaboration** is prepended, running *before* authoring-sugar. It fires on any node with a **wired** `rejects` port: join-ON decomposition (pull a single-side reject-capable ON subexpr to a per-side `calc`), then guard-and-branch (a `<ssa>_guard` **calc** computing `_ttrp_v1..k` via internal validity fns → `branch` on their conjunction → {original op | reject `calc`}). It sits *before* sugar (not "after sugar" as contracts §5 first read) precisely because it synthesizes name-bearing **Calc** nodes: the single-pass engine visits each stratum once, so a Calc synthesized after SUGAR would never be lowered to Project. "Before capability lowering" — the operative constraint — still holds. Unwired programs are untouched (R-P3), so the measure and every existing golden are unchanged.

## 2. The termination measure

A strictly-decreasing lexicographic tuple over the graph `G`:

```
M(G) = ( pendingRejectSiteCount(G),          # RJ-P1 leading term
         sugarNodeCount(G),
         functionMissCount(G),
         nodeMissCount(G),
         unsynthesizedCrossEngineEdgeCount(G) )
```

- **pendingRejectSiteCount** (RJ-P1, leading/most-significant) — number of nodes that are reject-capable AND have a wired `rejects` port AND are not yet elaborated (excluding the un-decomposable both-sides join, which is the RJ-105 fallback, not "pending"). Each guard-and-branch (or join-decompose) rewrite consumes exactly one and creates none, so it **strictly decreases**. It is the leading term precisely because elaboration *adds* a non-native `Branch` and two Calcs — raising the lower terms (sugarNodeCount, nodeMissCount) — which is lexicographically dominated by the drop in this term. Zero for every unwired program, so the fail-fast path's measure is byte-identical to pre-feature.

- **sugarNodeCount** — number of `SugarNode` instances (Select/Calc/Distinct + a HAVING-bearing Aggregate). Sugar expansion strictly decreases it; no later stratum creates a SugarNode.
- **functionMissCount** — Σ over nodes of unsupported catalogue-function occurrences on the node's container engine. Function-lowering and fission strictly decrease it (a rewrite/fission removes at least one miss); no earlier-or-equal stratum increases it.
- **nodeMissCount** — number of (node, engine) capability misses. Node-lowering and re-placement strictly decrease it. Node-lowering may *introduce* new nodes (Branch→2×Filter), but those are chosen native for the engine, so nodeMissCount drops by ≥1 and never rises.
- **unsynthesizedCrossEngineEdgeCount** — cross-engine data edges not yet lowered to Store+Transfer+Load. Movement synthesis strictly decreases it; re-placement may *increase* it (a moved node creates a crossing) — see the ordering guarantee below.

**Ordering guarantee.** Each rule strictly decreases its own component and **never increases an earlier (more-significant) component**. Re-placement can raise component 4 (new crossing) but it runs in a *later* stratum than the components (1–3) it must not raise, and component 4 is the *least* significant — so the lexicographic tuple still strictly decreases at every step. The engine **asserts** the strict decrease on every applied rewrite (cheap; catches a mis-written rule forever) and hard-fails (internal error) otherwise — that per-step `lexLess` check is the real termination guarantee. The iteration bound is only a secondary backstop; because RJ-P1 elaboration *grows* the graph (guard/branch/reject nodes needing extra sugar + branch→filter lowering), the pre-feature "sum of initial components" bound now under-counts, so it scales generously with graph size (`measureBound + 8·(|nodes|+|edges|) + 64`).

## 3. Node-fission rules (the T6-d work item)

**When it fires.** A `Project`/`Calc`-descended node whose columns have a *mixed* function-miss profile — some output columns use only engine-supported functions, others use ≥1 unsupported function — on its container engine.

**What it does.** Split into `Project(supported-slice) → Project(missing-slice)` (a two-node chain), preserving column dependencies:
- the **supported slice** keeps all columns whose expressions are miss-free (and any pass-through columns the missing slice depends on);
- the **missing slice** keeps only the columns with ≥1 unsupported function; it is the sole re-placement candidate (stratum 5 moves it);
- the **SSA label** lands on the *final* slice (the one whose out-edge the variable named); intermediate slices get anonymous `~n` labels (C1-c-i / E-b).

**Legality.** Fission is legal when no missing-slice output feeds a supported-slice input (no back-dependency). If such a dependency exists, order the split by the column dependency DAG so the supported slice is upstream; if a genuine cycle of mixed dependencies exists (a missing-function output consumed by a supported-function column that another missing column consumes), fission **degenerates** — the whole node stays intact and escalates as one unit (stratum 5).

**Degenerate case.** If *every* output column misses, there is no supported slice — no fission; the whole node escalates.

**Measure effect.** functionMissCount strictly drops per fission: the supported slice becomes miss-free (its misses move entirely to the smaller missing slice, and the missing slice has strictly fewer columns than the original mixed node, so the count on *that engine* for the supported slice is 0). The re-placement of the missing slice then drops nodeMissCount/functionMissCount on the new (capable) engine.

## 4. Determinism rules

- Node ids are **document-order stable** (`n0`, `n1`, … minted at build).
- Within a stratum, rules apply in **(stratum, node-id) order**; a `Unchanged`-by-reference node is not revisited in that stratum pass (post-order reference-equality walker, EXAMPLES.md §7d).
- **Insertion-ordered collections only** (`LinkedHashMap`/`List`); no reliance on hash iteration order.
- Same input graph + same manifests ⇒ **byte-identical** output graph and rewrite log.

## 5. Confluence stance

v1 does **not** prove confluence. Determinism (a fixed application order) substitutes for it (T8-b: v1 rewriting is cost-free/deterministic; the optimizer Z — where confluence/cost would matter — is v2). The property suite proves termination (bounded), determinism (double-run equality), and idempotence (fixed point is fixed).

---

### Review

- [x] **Bora:** approved 2026-07-06 (full fission incl. dependency-DAG ordering). Stage 2.3a T2.3a.3+ and 2.3b's pre-flight gate are unblocked.
