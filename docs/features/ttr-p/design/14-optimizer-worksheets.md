# Z · Optimizer — Pre-convergence Worksheets (W1–W5)

> Companion to [`13-optimizer-options.md`](./13-optimizer-options.md) (the option catalogue + GI-1…GI-20). These are the five "before converging" work items, executed 2026-07-07. They are **sketches to be shot at**, not contracts — nothing here is decided until it survives review and lands in the control-room decision log.
>
> **Status:** drafted, awaiting Bora's review.

---

## W1 · Cost-shape vocabulary — dimensioned resource vectors (ZQ3 decide-now item 1)

**Rule (from the Z-b γ-direction note):** manifests never carry a scalar "cost". They carry **resource vectors** — dimensioned quantities per node kind — and the world *instance* carries **calibration** (how fast is this box) and **prices** (what a resource unit costs *here*). Any objective (time, $, …) is then a fold over the same vectors; adding an objective never touches manifests again.

**Dimensions (v-first set):** `cpu` (µs) · `io` (bytes read/written at the engine) · `mem` (bytes held, for feasibility not cost, v1) · `xfer` (bytes crossing a staging boundary — carried by edges, not nodes). Deliberately no "money" dimension: $ = prices × resources, computed, never stored.

**Engine-type manifest additions** (compiler-shipped, beside the T6 capability entries — illustrative syntax, format follows the manifest format decision):

```yaml
engine-type: postgres
capabilities: { Join: {types: [inner,left,right,full,cross,semi,anti]}, ... }   # T6, exists
cost-shapes:                    # NEW — per node kind: fixed + per-unit terms
  Load:      { cpu: 0.05/row }                     # scan of hosted storage
  Load.csv:  { cpu: 0.5/row,  io: 1.0/byte }       # COPY parse path
  Filter:    { cpu: 0.2/row-in }
  Project:   { cpu: 0.1/row-in }
  Join:      { cpu: 0.6/row-in, mem: 32/row-build }
  Aggregate: { cpu: 0.4/row-in, mem: 24/group }
  Sort:      { cpu: 0.8/row-in }                   # n log n flattened to a linear proxy, v1
  Store:     { cpu: 0.1/row,  io: 1.0/byte }
```

```yaml
engine-type: polars
cost-shapes:
  Load.csv:   { cpu: 0.3/row, io: 1.0/byte }
  Load.arrow: { cpu: 0.02/row }                    # staged Arrow is near-free to load
  Load.adbc:  { cpu: 0.3/row }                     # + the edge pays xfer
  Filter:    { cpu: 0.1/row-in }
  Join:      { cpu: 0.3/row-in, mem: 40/row-build }
  Aggregate: { cpu: 0.2/row-in, mem: 32/group }
```

**World-instance calibration + prices** (TTR-M world doc, instance `extends` type — D-d-α):

```ttrm
schema world

def world dev {
    def storage erp_pg_store extends postgres_storage { hosts: [erp] }
    def storage files       extends local_fs { staging: true }

    def engine erp_pg extends postgres {
        calibration { cpu-factor: 1.0, io-rate: "400MB/s", concurrency: 4 }
        prices      { }                          # on-prem: no marginal $
    }
    def engine polars_w extends polars {
        calibration { cpu-factor: 0.8, memory: "32GB" }
        prices      { }
    }
    # a priced engine, for the Z 2.0 shape:
    def engine snow extends snowflake {
        calibration { warehouse: "XS" }
        prices      { cpu-second: "0.0008 USD", egress-byte: "0.00000009 USD" }
    }

    transfer-rates {                             # per (storage|engine, storage|engine) pair, via staging
        default: "100MB/s"                       # Arrow IPC store+load combined
        (erp_pg, files): "150MB/s"
    }
}
```

**Time objective (Z 1.0)** = fold: `duration(node) = Σ_dim resource_dim × calibrated-rate_dim`; island duration = Σ nodes (v1 proxy — the engine may do better, we don't model its internals, GI-1); edge duration = `xfer_bytes / rate`; **makespan = critical path over the wave structure** (F-a semantics). **$ objective (Z 2.0)** = `Σ resources × prices` — same vectors, different fold. Deadline profile = makespan constraint + $ objective (GI-10).

*Open naming/format:* manifest file format (YAML? TTR-M doc?) is the standing T6 leftover; `/row-in` vs `/row` unit grammar; whether `mem` is v1 or reserved.

---

## W2 · Stats-query contract sketch (GI-18)

What Z may ask; two backings answer it (served / repo-read). The *mechanism* of acquisition is the metadata server's design, not Z's.

```kotlin
/** The optimizer's ONLY window onto metadata. Backings: served (fresh) | repo-read (declared). */
interface MetadataSource {
    fun resolveWorld(qname: Qname): ResolvedWorld          // world + manifests + calibration + prices
    fun statsSnapshot(): StatsSnapshot?                    // null = declared stats only (repo backing, or server w/o stats)
}

/** Immutable per optimize pass — Z takes ONE snapshot at pass start (GI-19 ladder applies). */
interface StatsSnapshot {
    val fingerprint: String            // sha256 over content; recorded in bundle manifest (GI-15)
    val asOf: Instant                  // display + staleness warnings; NOT part of plan identity
    fun tableStats(ref: PhysicalTableRef): TableStats?     // null = not collected → declared/default
}

data class TableStats(
    val rowCount: Long?,
    val byteSize: Long?,               // v1 stops here (Z's wins are boundary-volume-shaped)
    val columns: Map<String, ColumnStats>? = null,         // RESERVED, not v1
)
data class ColumnStats(val ndv: Long?, val nullFraction: Double?)   // reserved with it
```

**Contract semantics (the actual contract, prose):**

1. **One snapshot per optimize pass.** Taken at pass start; all queries answer from it (internal consistency — no torn reads across a refresh).
2. **Absence is a value.** `null` snapshot / `null` per-table ⇒ fall back to *declared* stats (world doc), then to *default constants*. Never an error by itself.
3. **The ladder (GI-19):** no `MetadataSource` at all at pass start ⇒ **hard error** (`TTRP-OPT-001 "no metadata source"`); source lost mid-session (Designer) ⇒ keep the held snapshot, **stale warning** (`TTRP-OPT-002`).
4. **Determinism (GI-15):** plan identity = f(graph, resolved world, **snapshot fingerprint**, profile, solver budget tier). Same tuple ⇒ same plan. The fingerprint lands in `manifest.json` beside the world fingerprint.
5. **Z never asks anything else.** No engine URLs, no connections, no instance probing — the interface has no method for it (rejection of Z-d-β enforced by the type system, the nicest kind of enforcement).

*Fold into the ttr-metadata feature (`docs/ttr-metadata/`) as a consumer requirement when Z 1.0 is planned.*

---

## W3 · Hero scenario hand-run — min-cut sanity check

**Setup.** The hero graph (A5), placement-relevant spine (error path omitted — it rides the chosen placement):

```
load_accounts(pg) -> filter_active ─┐
                                    ├─ join -> aggregate -> branch -> store/display
load_sales(csv)  -> filter_amount ──┘
```

**Declared stats:** accounts 200 K rows × 120 B = 24 MB · sales.csv 10 M rows × 80 B = 800 MB. **Default selectivity 0.5** (Z-d: deliberately dumb): accounts_f = 100 K (12 MB), sales_f = 5 M (400 MB), join out = 5 M, aggregate out = 1 K rows. **Rates:** W1's tables; transfers 10 ms/MB (100 MB/s, store+load combined).

**Candidate plans, cost-sum (arithmetic shown so it can be checked):**

| Plan | Work | ms |
|---|---|---|
| **A · all-PG** | CSV→PG transfer 800 MB = 8 000 · COPY parse 10 M×0.5 µs = 5 000 · scan acc 10 · filt acc 40 · filt sales 10 M×0.2 = 2 000 · join 5.1 M×0.6 = 3 060 · agg 5 M×0.4 = 2 000 | **≈ 20 110** |
| **B · all-Polars** | acc: ADBC 200 K×0.3 = 60 + xfer 24 MB = 240 + filt 20 · CSV read 10 M×0.3 = 3 000 · filt 10 M×0.1 = 1 000 · join 5.1 M×0.3 = 1 530 · agg 5 M×0.2 = 1 000 | **≈ 6 850** |
| **C · cut after filter_active** (PG preps accounts; Polars does the rest) | PG scan+filt 50 · xfer 12 MB = 120 · Arrow load 100 K×0.02 = 2 · CSV 3 000 · filt 1 000 · join 1 530 · agg 1 000 | **≈ 6 700** |
| **D · C + eager pre-aggregation** (choice var: pre-agg sales by account before join — legal, SUM decomposes) | as C, but: pre-agg 5 M×0.2 = 1 000 → 100 K rows · join 200 K×0.3 = 60 · re-agg 100 K×0.2 = 20 | **≈ 5 250** |
| **E · pre-agg in Polars, join+final in PG** (the cut moves; transfer shrinks 400 MB→9 MB) | Polars 3 000+1 000+1 000 · xfer 9 MB = 90 · PG scan+filt 50 · join 200 K×0.6 = 120 · agg 100 K×0.4 = 40 | **≈ 5 300** |

**Findings:**

1. **Plain min-cut (no rewrite vars) picks C** — and note *where* it cuts: not "PG vs Polars per table" but **inside the accounts chain, after the filter** — ship 12 MB, not 24 MB. That's precisely the boundary-economics win, found mechanically. A human data engineer picks the same plan. ✅
2. **The choice-variable version (Z-f-β) finds D/E ≈ 5.3 s** — the eager-aggregation rewrite shrinks either the join input (D) or the transferred volume 400 MB → 9 MB (E). D vs E is a near-tie on cost-sum; **makespan breaks it** (in D, PG's 170 ms runs parallel to the Polars chain; critical path ≈ 5.1 s) — a small live demonstration of why Z-b-β is the right objective.
3. **Stone construction check** (2 engines ⇒ exact min-cut): terminals PG/POL; per movable node, an edge *to POL-terminal* with capacity = cost-on-PG and *to PG-terminal* with capacity = cost-on-Polars; per data edge, an undirected edge with capacity = its transfer cost; data-gravity (accounts live in PG, CSV on files near Polars) enters as the Load-cost asymmetry. Max-flow/min-cut over ~9 nodes: trivial. Solves A–C exactly; D/E need the choice variables (CP-SAT) or two min-cut runs (one per rewrite state) — with **one** coupled rewrite, enumerate×min-cut is even simpler than ILP. Noted as a Z 1.0 implementation freedom.
4. **Dumb selectivity was enough.** The ranking A ≪ B ≈ C < D/E is driven by *declared base sizes* and *transfer volumes*, not by selectivity precision — evidence for Z-d's "deliberately dumb, boundary-volume-shaped" stance. (Sensitivity: at selectivity 0.1 the C-vs-B gap widens ~5×; ranking unchanged.)

**Verdict: the model produces the human plan. The candidate machinery passes the hero test.**

---

## W4 · Container-role amendment draft (against B-T9 / for the decision log)

> Draft wording for the control-room decision log at Z convergence; amends **B-T9 (container)** and **B-T9/T8 (v1 placement)** for the Z era. v1 semantics are the strict special case.

**[Z-…] · Container roles generalized (amends B-T9).** A `Container` carries an optional placement annotation with a strength, and a cohesion flag:

```
container <name> [together] [ target <engine> | prefer <engine> ] ( ports… ) { … }
```

- `target <engine>` — **directive**: every contained node runs on `<engine>`. Implies cohesion (one island) by construction. *This is exactly v1's semantics — v1 files parse unchanged and mean what they meant.*
- `prefer <engine>` — **hint**: Z may place contents elsewhere (wholly or partly), paying the profile-declared deviation penalty (GI-16-e: uniform per strength; never a number in program text).
- `together` — **cohesion without (or with) an engine opinion**: contents form one island on one engine; combinable with `prefer`, redundant with `target`.
- *no annotation* — **grouping only**: a closed function (ports, scope — C3-d-iii unchanged) with **zero placement meaning**; Z places nodes freely across it.
- **No containers at all** is a valid program under Z: the optimizer derives islands; `explain`/canvas render them as derived containers (C1 derived-canvas rules: auto-layout, read-only).
- Node-level pins do not exist (GI-16-d); the mini-container is the escape hatch.
- Internal model: `Container { target: EngineRef?, strength: HINT|DIRECTIVE (iff target), together: Bool }`; v1 ingest: `target` present ⇒ `DIRECTIVE`.
- **Without Z** (v1 toolchain, or optimize=off): `prefer` and target-less containers are **capability errors** (`TTRP-OPT-0xx "placement required"`) unless `[ttrp] bare-target`-style defaults resolve them — no silent guessing (P2). ⟵ *this clause needs Bora's eye: alternative = `prefer` degrades to directive when Z is off. Flagged, undecided.*

**Grammar delta (C3):** two keywords (`prefer`, `together`); `target` unchanged. **Canvas (C1):** distinct affordances for directive/hint/grouping/derived — folds into the skin-roster leftover.

---

## W5 · World-schema stats-attribute sketch (GI-11)

Requirements: declared floor writable in TTR-M text (serverless backing needs it); served stats must *shape-match* it (one vocabulary); churn must not pollute the world doc proper.

```ttrm
schema world

def world dev {
    def storage erp_pg_store extends postgres_storage {
        hosts: [erp]
        stats {                                   # OPTION A — inline declared floor
            table erp.db.accounts  { rows: 200000,   bytes: 24000000 }
        }
    }
}
```

```ttrm
# OPTION B — sibling stats doc (same schema kind, own file → churn isolated, servable shape)
def stats dev_stats for dev {
    asOf: 2026-07-01
    table erp_pg_store / erp.db.accounts   { rows: 200000,  bytes: 24000000 }
    table files / sales_2026               { rows: 10000000, bytes: 800000000 }
}
```

- **A · inline in storage defs.** *Buys:* one file, stats beside the thing described. *Costs:* refresh churn = world-doc churn (the exact "nonsense" GI-13 rejected for the repo — but a *declared floor* changes rarely, so inline survives for that narrow use).
- **B · sibling `def stats` doc.** *Buys:* churn isolation; the served backing and any repo-written floor share one grammar; `asOf` in one place; can be gitignored *or* committed per project taste. *Costs:* one more doc kind.
- **C · served-only, no text form.** *Costs:* kills the serverless backing's declared floor. **Rejected by GI-18.**

*Lean: **B as the canonical shape** (it IS the serialization of W2's `StatsSnapshot` — fingerprint = hash of the resolved stats doc, unifying the two backings beautifully), with **A permitted for the rarely-changing declared floor**. Precedence: served snapshot > sibling doc > inline > defaults — P2-ordered like D-c schemas.*

*Column-level stats: the `table` entry grows optional per-column blocks when `ColumnStats` unreserves (W2). Not v1.*

---

## Disposition

| Item | State | Next |
|---|---|---|
| W1 cost shapes | sketched | review; manifest-format decision inherits the T6 leftover |
| W2 stats contract | sketched | review; fold into ttr-metadata feature as consumer requirement |
| W3 hero hand-run | **passed** | none — evidence recorded |
| W4 B-T9 amendment | drafted | **one flagged sub-decision** (behavior of `prefer`/target-less without Z) |
| W5 stats schema | sketched, lean B(+A) | review; hand to metadata-server design with W2 |

After review + the W4 flag: write the Z decision-log entries into the control room → **Z goes 🟢** (as design; implementation = TTR-P v2).
