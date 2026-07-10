# Platform Frontends — BI/Analysis Surface Options (workstream B)

> Divergence catalogue for **B — the BI/analysis surface** (LF-2): see the results of TTR-P transformations, and interactively author/generate the TTR-P that produces them. Session 2026-07-09.
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) §B.
>
> **Scope guard:** B designs the *read/analysis product*. Entry UX = C; write path = D; spreading = E; stack/contract legality detail = F (cross-linked, not decided here).
>
> **Research basis (2026-07-09):** semantic-layer/OLAP sweep fully verified against official sources; new-entrants sweep verified; Querybook/Streamlit verified. The classic BI platforms (Superset, Metabase, Lightdash, Redash, Grafana, DataLens, Briefer) are described from pre-mid-2025 knowledge and marked **⚠ unverified-2026** where the fact is load-bearing — re-verify at convergence.

---

## 0. Inherited constraints (the cage)

- **`PL FI-3` / P2 (candidate):** deterministic surface — no LLM/agent intelligence here; NL analytics stays Iris.
- **`PL C-3-γ`:** governed reads ride the **query door** (Theseus): validated plan → Argos (RLS/mask injection) → Kyklop → worker. There is **no SQL endpoint** in the platform's contract — the door takes plans.
- **`PL D-3`/`PL P2`:** kantheon owns nothing shared; no build-dep on `envelope/v1`/report-renderer in place (→ F).
- **`PL A-1`:** "view" leaned MIT for the Designer; where the BI surface sits on the license line is A's LF-6.
- **`PL H`:** per-user identity at every door; policy = Perun's Rego bundles; RLS is *injected at plan validation*, not native in the engines today.
- **`PL I-1`:** "proposals in, projections out." Anything we hand to an external tool should be a regenerable *projection*, never a second source of truth.
- **"Robots write through git":** if dashboards/views become durable artifacts, the platform's temperament is *BI-as-code* (canon documents), not mutable server state.

## 0.1 The structural finding: the policy-honesty problem

Every external BI tool on the long list expects to **speak SQL directly to a database**. The platform's governed read path is a **plan-validating door**. Connecting a BI tool straight to Arges/Brontes bypasses Argos — policy silently evaporates. Any α/δ-flavored option must pick a reconciliation:

- **R1 · SQL façade on the query door.** Theseus (or a sibling) grows a Postgres-wire/ADBC front; incoming SQL is parsed → `plan.v1` → validated normally. *Buys:* every SQL client ever written becomes a policy-honest frontend. *Costs:* a SQL→plan translator is a serious compiler artifact (dialect scope creep is the death zone); prior art exists (Cube Core ships a Postgres-wire SQL API over its own semantic model — so the shape is provably buildable at "semantic-scoped SQL" size, not full-ANSI size).
- **R2 · Published marts + engine-native RLS.** TTR-P programs materialize **governed result marts**; the engines' own RLS (PG policies on Arges, etc.) guards them, **provisioned from Perun's bundles** (an amendment proposal — today Argos injects predicates at plan time only); BI tools connect directly *to marts only*, per-user credentials. *Buys:* zero impedance for any BI tool; "results of TTR-P transformations" is literally what a mart is. *Costs:* second enforcement surface to keep in sync with Perun (drift risk = the `PL H-7` HOCON story again); per-engine RLS dialects; marts must be declared (what's published vs internal — a nice fit for a TTR-M/world-level `published` marker).
- **R3 · Headless semantic layer as the policy carrier.** A semantic layer (Cube-shaped) sits between engines and all BI frontends; its model is **deterministically generated from TTR-M `md`** (a projection, `PL I-1`-style); its row-level access policies are generated from Perun bundles; BI tools connect to *it* (Postgres-wire), never to engines. *Buys:* hierarchies survive (Cube has first-class `hierarchies` since ~v1.x — verified 2026); one choke point for identity+RLS; OSI (the vendor-neutral semantic-interchange spec, finalized 2026-01) makes "TTR-M md → semantic model" a standardized projection target, which also future-proofs `PL IQ-1` (PowerBI mapping). *Costs:* a new always-on service in the roster (license: Cube backend Apache-2.0 — embedding legal); *two* query paths (door for TTR-P, semantic layer for BI) whose policy semantics must provably agree; Cube's SQL API is semantic-scoped, not general SQL.

**R0 (the null):** no external SQL access at all — only platform-native surfaces that call the door. This is what pure B-β/B-ε assume.

## 0.2 The decomposition finding: authoring is native in every branch

No tool on the long list can host **TTR-P authoring** (author → validate → preview via door → graduate to deployed program). That half is platform-native in *every* branch — browser-worker LSP + query-door preview already exist (`PL G`, Designer machinery). So LF-2 is really about the **exploration/dashboard half (B-read)**; the **authoring half (B-author)** is a native panel whose only open question is *where it lives* (Designer extension vs the BI surface vs both — lands with A's LF-1).

---

## 1. The long list (the `PL EQ-3` treatment)

Scoring: **Lic** license/embed legality · **Emb** embedding depth · **Id** per-user identity/RLS pass-through · **MD** hierarchy/OLAP awareness · **Fit** overall fit as/inside the platform BI surface. ✔ good · ~ partial · ✘ poor · ? unverified-2026.

### Tier 1 — full BI platforms (candidates for B-α)

| Tool | Lic | Emb | Id | MD | Notes (verification status) |
|---|---|---|---|---|---|
| **Apache Superset** | Apache-2.0 ✔ | embedded SDK + guest tokens ~ | in-tool RLS rules; DB conns = service acct ~ | pivot chart, no real hierarchies ✘ | The default "serious OSS BI"; Cube documents pairing with it. **⚠ unverified-2026** details |
| **Metabase** | AGPL core; **interactive embedding + sandboxing (RLS) = paid EE** ✘ | paid ✘ | paid ✘ | ✘ | Great UX, wrong license shape for embedding. **⚠ unverified-2026** |
| **Lightdash** | MIT core (⚠ verify); dbt-coupled semantics | cloud/EE features ~? | ~? | dbt metrics, no hierarchies ✘ | Semantic layer is *dbt's*, not ours — impedance with TTR-M. **⚠ unverified-2026** |
| **Redash** | BSD-2 ✔ | ✘ | ✘ | ✘ | Long maintenance-mode; not a 2026 bet. **⚠ unverified-2026** |
| **Grafana** | AGPL ✘ | limited/licensed ✘ | ~ | ✘ | Ops-dashboard shaped, not analyst BI. **⚠ unverified-2026** |
| **Yandex DataLens** | Apache-2.0 ✔? | ~? | ~? | ~? | Worth a look if α shortlists. **⚠ unverified-2026** |
| **Rill** | Apache-2.0 ✔ (verified, v0.87.2 2026-06) | embeddable dashboards ✔ | ~ | YAML metrics layer on DuckDB/ClickHouse; no deep hierarchies ~ | **BI-as-code** (YAML in git — fits our temperament); engine mismatch (wants DuckDB/ClickHouse, we have PG/MSSQL/Polars) |
| **Shaper** (taleshape) | MPL-2.0 ✔ (verified, active 2026-06) | **built for embedding**: white-label, no-iframe JS/React SDKs ✔ | **RLS via JWT claims** ✔ | ✘ | New 2025-26 entrant, embedded-analytics-first, git-based dashboards, DuckDB-powered — small but shaped exactly like "a BI organ inside another product" |
| **Briefer / Querybook / Streamlit** | AGPL?/Apache/Apache | ✘/✘/iframe ~ | ~/engine-impersonation ~/OIDC + caller's-rights conns ✔ | ✘ | Notebook/app tier — not the BI surface, but Streamlit's caller's-rights pattern (1.53, 2026) is prior art for per-user DB identity. Querybook slowed (no release since 2025-04) |

### Tier 2 — headless semantic layers (the B-δ substrate)

| Tool | Lic | Key facts (all verified 2026-07) |
|---|---|---|
| **Cube Core** | backend Apache-2.0, clients MIT ✔ | **First-class `hierarchies` in the data model**; REST/GraphQL/**Postgres-wire SQL API** in Core; `queryRewrite` + `access_policy` row-level security driven by JWT security context; BI tools (Superset, Metabase, Tableau, PowerBI) sit on it; active (v1.6.46, 2026-05); independent company; OSI participant |
| **MetricFlow** | **Apache-2.0 since 0.209 (2025-10)** ✔ | Usable without dbt Cloud (CLI on dbt Core); but metrics-tree shaped, dbt-project-coupled; dbt Labs+Fivetran merged (2026); hosted SL still commercial |
| **Boring Semantic Layer** | MIT ✔ | New 2025; Python/Ibis (any backend incl. Postgres); MCP-first; small (~450★) but the "semantic layer as a library" existence proof |
| **Malloy** | MIT ✔ | A *language* with native `nest:` hierarchical results + an embeddable drillable renderer; Malloy Publisher = OSS semantic model server (2025). Prior art for B-ε more than a component |
| **OSI spec** | Apache-2.0 code / CC-BY spec | Vendor-neutral semantic-model interchange, finalized 2026-01 (Snowflake, Salesforce, dbt, AtScale, Cube…). **TTR-M `md` → OSI is the future-proof projection target** (feeds `PL IQ-1` too) |

### Tier 3 — components for building native (the B-β toolbox)

| Tool | Lic | Key facts (verified 2026-07) |
|---|---|---|
| **Perspective** (OpenJS, ex-FINOS) | Apache-2.0 ✔ | C++→WASM streaming pivot engine; **true tree pivots with expandable levels + subtotals**; Arrow read/write/stream; millions of rows client-side; **editable datagrid** (writeback into its Table — relevant prior art for C!); active (v4.5.1, 2026-05); JPMC + Prospective behind it |
| **Mosaic/vgplot** (uwdata) | ✔ | Declarative linked-views framework over DuckDB(-WASM); "millions to billions of rows" interactive; active (v0.28.1, 2026-06) |
| **visx** | MIT ✔ | React+d3 primitives; v4 alpha = React 19 (Designer kinship) |
| **Vega-Lite** | BSD ✔ | Iris's chart layer — reusable *as a library* regardless of the Iris-pipeline question (it's OSS, not kantheon-owned) |
| **Saiku (revived 2026)** | Apache-2.0/EPL | Mondrian 4.8 + Arrow + SvelteKit; real MDX hierarchies. Existence proof that the XMLA line lives (also: Eclipse Daanse modernizing Mondrian, EPL-2.0) — but a JVM MDX engine is a *second semantic authority* beside TTR-M; catalogued, not leaned |

### Struck from consideration

Latitude (pivoted to LLM observability, repo 404) · react-pivottable (dormant) · GoodData CN CE (EOL, never OSS) · Metabase-for-embedding (license shape) · eMondrian (dormant since 2023).

---

## 2. The branches, rendered

### B-α · Embed/integrate an existing OSS BI

Platform provides data access (via R1/R2/R3); the BI tool provides exploration, dashboards, sharing.

- **α-1 wholesale embed** (Superset or Shaper inside our shell, white-labeled). *Buys:* dashboards/alerts/exports day one; zero viz maintenance. *Costs:* two UX worlds (their model of datasets/charts vs our MD models — hierarchies flatten, ✘MD across Tier 1); their auth bridged to `PL H-2`; upgrades track someone else's roadmap; **R-problem must be paid in full**.
- **α-2 integrate** (their viz engine, our catalog/auth): deeper surgery into a codebase we don't own; usually becomes a fork. Catalogued as the trap it historically is.
- **α-3 "the platform is a data source"** — ship connection recipes + the R2 marts + semantic projection, and let orgs bring Superset/PowerBI/whatever they already run. *Buys:* honest scope cut; enterprises **will demand** PowerBI/Tableau connectivity anyway (`PL GI-3`); zero frontend to build. *Costs:* no owned product surface; the hero's analyst experience is only as good as the org's BI tooling; TTR-P authoring still needs a home (§0.2).

*Hero:* variance dashboard lives in Superset; hierarchy drill is faked with flattened level columns; the TTR-P bridge is authored in the Designer, its output mart appears in Superset after refresh.

### B-β · Build native on the query door

A platform-owned exploration surface: pivot/chart over Theseus, **TTR-M-aware** — it *knows* cubes, hierarchies, calc maps (`MD_CALC_CATALOG`), versions, and column lineage. R0 suffices (no SQL exposure needed). Component reality check: **Perspective (tree pivots, Arrow) + Vega-Lite/visx + Mosaic patterns** mean "native" ≈ assembling proven engines, not writing a pivot grid from scratch; the door's results are already typed tables (Arrow-friendly).

- *Buys:* the only branch where **MD models render as MD models** (real hierarchy drill, subtotals, calc-map-aware measures); policy-honest by construction; lineage-aware drill ("why is this number" → column lineage → program → run) is a differentiator nobody on the long list can copy; BI-as-code temperament (saved views = canon documents) comes naturally.
- *Costs:* we own an exploration product forever (filters, exports, sharing, perf); dashboards/alerts/scheduling are real scope (Q-1); the enterprise "but we use PowerBI" demand remains unanswered without α-3/δ alongside.

*Hero:* analyst opens the `opex` cubelet, drills company→division→department along the real hierarchy with subtotals (Perspective grid), variance vs ACT2027 as a calc-map measure, charts the bridge, saves the view as a canon document; one click shows the lineage from the March marketing cell back to the entry commit.

### B-γ · Re-own the Iris render pipeline

Move/fork `envelope/v1` + the Vue/PrimeVue/Vega-Lite render stack platform-side (per `PL D-3`, kantheon consumes downstream — TR-3 precedent).

- *Buys:* proven block-render pipeline (tables/charts/drilldowns) and team muscle memory.
- *Costs:* the envelope renders **agent turns** (chat blocks, chips) — the wrong shape for pivot-first exploration; buys neither hierarchy awareness nor a query surface; Vue vs the Designer's React (F); pays the contract-move cost for the *least* differentiating asset. **Vega-Lite itself is reusable in any branch as a plain OSS library — the valuable part of "reuse Iris" costs nothing and needs no re-owning.**

### B-δ · Headless semantic layer + thin viewer(s)

The platform ships the **semantic projection** (TTR-M `md` → Cube model or OSI document, deterministically generated — a `PL I-1` "projection out"); R3 carries policy; all BI frontends (bundled thin viewer, org's Superset, PowerBI via Cube) are clients.

- *Buys:* one governed choke point; hierarchies survive to any Cube-aware client; the enterprise-BI demand (`PL GI-3`) and the owned-surface need stop competing — both are clients; OSI standardizes the projection; the bundled viewer can be small because deep exploration can happen anywhere.
- *Costs:* Cube (or equivalent) joins the roster as an always-on service the platform must operate and secure; **two query paths** (door vs semantic layer) whose policy equivalence must be *proven*, not assumed — the platform's second policy surface (same drift class as R2); TTR-M md → Cube mapping losses need an explicit ledger (`PL IQ-4` sibling).

*Hero:* the projection regenerates when the model changes; the analyst uses the bundled viewer for hierarchy drill; the org's controller sees the same numbers in PowerBI through the same layer; the TTR-P bridge still authored natively.

### B-ε · Weird: the BI surface IS TTR-P

No point-and-click exploration; a notebook/code surface where analysts author TTR-P (hand or wizard-generated), run via the door, render results, save programs. Prior art: Evidence/Rill/Shaper (BI-as-code, git-native), Malloy (language with nested results + drillable renderer), marimo.

- *Buys:* radical coherence — exploration artifacts *are* programs; "interactive work generates TTR-P" is the whole product; git/review discipline for free; the graduate-to-deployed path is trivial (it was a program all along).
- *Costs:* excludes the click-first analyst persona entirely (the hero's controller does not write code); as *the* answer it fails FI-2(a). Catalogued to mark the floor — but **its core loop (author → door → render → save) is exactly §0.2's B-author half**, which every branch needs. ε is less a branch than the authoring half promoted to the whole.

---

## 3. Cross-links out

- **→ A (LF-1):** β's surface and the B-author panel could *be* Designer extensions (`PL Q-4-a`) or a standalone app — topology decides; δ's thin viewer likewise.
- **→ C/E:** Perspective's editable grid + Arrow streaming is prior art for the entry grid; a semantic layer (δ/R3), if chosen, would also serve C's cubelet reads.
- **→ D:** R2's "published mart" marker and engine-native RLS provisioning are D/H amendment proposals.
- **→ F:** γ's contract-move cost; Vega-Lite-as-library needs no move; React kinship of visx/Perspective.
- **→ `PL I` (IQ-1):** TTR-M md → OSI/Cube projection is the same mapping family as the PowerBI semantic-model arc — one ledger, two targets.
- **→ TTR-P effort:** B-author's wizard-generated TTR-P (pivot-to-program?) touches TTR-P ergonomics; keep their control room informed.

## 4. Leans (not decisions)

1. **Decompose first (§0.2):** treat **B-author** (native, door-backed, Designer-machinery-adjacent) as settled-by-architecture in every branch; LF-2 decides **B-read**.
2. **For B-read: a δ-shaped composite** — the semantic projection (TTR-M md → Cube/OSI) as the governed BI substrate + **α-3** ("bring your BI", Superset as the documented reference pairing, PowerBI via the same layer) + a **β-thin native viewer** for hierarchy-true, lineage-aware in-platform viewing (Perspective + Vega-Lite; small because δ carries the heavy clients). This is also the only composition that answers `PL GI-3` (PowerBI demand) without betting the product on it.
3. **Reject-lean:** γ as a *pipeline* reuse (take Vega-Lite the library, skip the envelope move); α-1 wholesale embed (license/UX/R-problem stack up); ε as the whole answer.
4. **The R fork is the real decision** and it partially belongs to D/H: R3 vs R2 (vs R1-later) should converge *with* D, not before it.

## 5. Open questions (B-local)

- **BQ-1 ·** R1 feasibility/scope: is "semantic-scoped SQL → plan.v1" a bounded artifact (Cube proves the shape at semantic scope) — or is R1 permanently out?
- **BQ-2 ·** R2/R3 policy provisioning: can Perun bundles deterministically generate engine RLS policies / Cube access policies? (H amendment proposal; the `PL H-7` rekey is the pattern.)
- **BQ-3 ·** TTR-M `md` → Cube/OSI mapping lossiness ledger (hierarchies, calc maps, versions/scenario dims) — sibling of `PL IQ-4`, shared with `PL IQ-1`.
- **BQ-4 ·** Saved views/dashboards as canon documents (BI-as-code): which TTR document kind, and does the `.ttrl` sidecar carry view-state here too?
- **BQ-5 ·** Q-1 sharpened: what dashboard/report scope is v1 — interactive-only, saved views, or scheduled packs (report-renderer territory, parked)?
- **BQ-6 ·** Verify the ⚠-flagged Tier-1 facts (Superset embed/RLS state, Lightdash license, DataLens health) before convergence.

## 6. BQ-6 verification pass (2026-07-10) — RESOLVED

The ⚠-flagged Tier-1 facts, re-verified against official sources:

- **Superset ✔ CONFIRMED, slightly better than assumed:** Apache-2.0; the **embedded SDK + guest tokens + RLS-rules-in-guest-token are all in the open core** (feature flags `EMBEDDED_SUPERSET`; per-dashboard allowed-domains) — no licensing constraint on embedding. Table's ~Emb/~Id stand or improve. [superset.apache.org/embedding](https://superset.apache.org/user-docs/using-superset/embedding/)
- **Metabase ✔ CONFIRMED OUT:** interactive embedding + data sandboxing (row/column permissions, multi-tenant embedding) = **Pro/Enterprise only**; the free OSS edition has neither. The "wrong license shape for embedding" verdict stands. [metabase.com/pricing](https://www.metabase.com/pricing/)
- **Lightdash ✔ CONFIRMED, with a drift note:** MIT core + proprietary `packages/backend/src/ee` (open-core split); actively maintained; **dbt-coupling remains central** (metrics declared in dbt YAML — the TTR-M impedance stands); repositioned as **"Agentic BI"** (AI-first direction — doubly out for us given P2). [github.com/lightdash/lightdash](https://github.com/lightdash/lightdash) · [LICENSE](https://github.com/lightdash/lightdash/blob/main/LICENSE)
- **DataLens ✔ healthy:** Apache-2.0, v2.9.0 (2026-02), active; embedding/RLS/hierarchy depth undocumented — remains "worth a look if α-1 ever shortlists", which the lean doesn't. [github.com/datalens-tech/datalens](https://github.com/datalens-tech/datalens)
- **Redash / Grafana:** not re-verified — consciously; neither is load-bearing in any surviving branch (Redash stale-trending, Grafana AGPL/ops-shaped; both already effectively out).

**Net effect on the lean: none disturb it; Superset's open-core embedding strengthens α-3's reference pairing.** Tier-2 (Cube, OSI, MetricFlow) and Tier-3 (Perspective, Vega-Lite, visx) were already verified 2026-07.

## Convergence status

**🟡 OPTIONS CAPTURED (2026-07-09) → verification complete (2026-07-10), READY TO CONVERGE.** BQ-6 resolved above; D/E/C/F all converged since capture (the R fork's D-coupling is now one-directional: D is fixed, R is free to choose). Convergence proposal presented to Bora 2026-07-10: the δ-composite with **R3** as policy carrier, **R2 rejected**, **R1 parked** (BQ-1), saved views answered by C thread 3's ttrl split (BQ-4), interactive + saved views v1 / scheduled packs parked (BQ-5). BQ-2 (Perun→Cube policy generation) and BQ-3 (md→Cube/OSI lossiness ledger) remain as cross-effort/planning items regardless of the outcome.
