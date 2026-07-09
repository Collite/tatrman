# Tatrman Platform — External Metadata & Megaprovider Options (workstream I)

> Divergence catalogue for **I — the Platform meets other metadata systems** (OpenMetadata, Collibra, Amundsen, DataHub, …) **and megaprovider ecosystems** (Google; Azure incl. PowerBI-for-MD and Fabric-for-metadata). Who is the source of truth, through what mechanism, and which system anchors v1? Session opened 2026-07-09 — the **last ⚪ workstream**, carrying the **last load-bearing fork (LF-7)**.
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) §I · [`06-orchestration-options.md`](./06-orchestration-options.md) (the connector frame, E-4) · map §K (whose worlds harvest fills).
>
> **Scope guard:** I designs the *inbound* metadata flows and the external-system relationships. The **outbound half is already decided** (C-2-γ export connectors, OpenLineage-shaped mapping living platform-side per E-5). Policy/auth for connectors = H (settled: secret refs, service principals). The PowerBI *semantic-model* mapping question is flagged where it stops being plumbing (IQ-1).

## Inherited constraints

- **LF-7 (the fork):** import (population source — TTR-P D-g's stance) · bidirectional sync · live federation.
- **GI-2/GI-3:** metadata servers (OpenMetadata, Collibra, Amundsen, …) and megaproviders (Google; Azure incl. PowerBI/Fabric).
- **TTR-P D-g:** external workspace metadata = a *population source* for worlds; the compiler reads repo + world docs, offline. **B-5-δ:** the compiler never talks to a server mid-compile — any external liveness must land in the snapshot *before* compile.
- **E-4-β (decided):** the connector SPI frame = `{engine kind, auth ref, ingest mapping, optional event subscription}`; run-results harvest for orchestrators rides it; **EQ-4** (data-engine harvest through the same frame?) was deferred to I.
- **BQ-2/BQ-3 temperaments:** canon (schemas, worlds — reviewed, locked) vs observation (stats — floating, auto-refreshed, per-object keyed). Any inbound flow must declare which it feeds.
- **K (decided):** platform world = its own git repo; instance truth is single; project worlds extend it; contradiction = compile error. **G-1-γ (decided):** every writer writes through git — sessions are branches, publishes follow repo policy.
- **C-2-γ:** export connectors + the lineage organ (column-grain, v1); Ariadne's **refresh-scheduling organ was stolen** into the metadata server — the natural driver for harvest schedules.
- **H (decided):** connectors authenticate via secret refs (H-5), run as service principals (H-2-ii); no new identity machinery here.
- **A-α:** turns-source-into-artifacts = MIT; runs/stores/schedules/serves = Platform.
- **Hero life 3:** lineage appears in OpenMetadata (export, decided); **life 2's worlds get populated** from real engines (this workstream's inbound job).

---

## I-1 · Truth direction (LF-7 — the last load-bearing fork)

**Question:** when Tatrman's metadata world meets an external one, who owns truth?

- **I-1-α · Import-only.** External systems are *population sources*: inbound flows generate **proposals** (TTR model/world documents) that humans ratify into repos; nothing external is authoritative. TTR repos remain the single canon; the external system is a well from which drafts are drawn.
  - *Buys:* text-is-canonical survives contact with the outside world; D-g's stance generalized without amendment; review = the same git flow as everything else (G-1-γ); no conflict machinery *ever* — a proposal that's wrong is simply not merged.
  - *Costs:* human latency on schema drift (mitigated: drift *detection* can be automatic and advisory — B's live-drift diagnostics — while *ratification* stays human); the org's "real" catalog may live elsewhere, and we must be honest that we mirror it by review, not by magic.
- **I-1-β · Bidirectional sync.** Tatrman pushes models/lineage out AND pulls schemas in, with conflict-resolution rules (last-write? system-of-record flags per object?).
  - *Buys:* the "good citizen in OpenMetadata" story with no human in the loop.
  - *Costs:* conflict rules between *heterogeneous schemas* are where sync projects go to die; a remote edit can now change what our compiler sees (P3 catastrophe — external mutation of canon); two systems each believing they own truth is the E-2-α disease at inter-org scale.
- **I-1-γ · Live federation.** The metadata server answers queries by delegating live to external servers for non-TTR objects; no copies.
  - *Buys:* always-fresh, storage-free.
  - *Costs:* **B-5-δ bans it from the compile path outright** (the compiler reads snapshots; a federated answer is unpinnable); for catalog browsing it means two availability domains and two latency profiles in one UI; T6's "the world is a compile target, not a live probe" is the same argument at the platform level.
- **I-1-δ · OpenMetadata-as-backend.** Already demoted by C-2 (TTR semantics second-class in a host schema); catalogued for completeness.

**The reframe that sharpens the lean:** with C-2's export connectors already decided, the real shape on offer is **two one-way flows** — *reviewed proposals in, derived views out* — which is **not** sync: exports are regenerable projections (never edited remotely and read back), imports are human-ratified drafts (never auto-merged). No conflict machinery exists because no object has two writers.

**Lean: α + the decided outbound = "proposals in, projections out; never sync, never federate."** Stats are exempt from the review loop by temperament (→ I-2). Drift detection = automatic + advisory; ratification = human.

## I-2 · The harvest mechanism (EQ-4's substance)

**Question:** does data-engine harvest (schemas, stats) ride E-4's connector frame — and what exactly does a harvest *write*?

**The temperament split does the heavy lifting (BQ-2/BQ-3):**
- **Schemas/instances = canon** → harvest generates **world/model document proposals** (→ I-3's mechanics); never auto-merged.
- **Statistics = observation** → harvest writes the **BQ-2 per-object stats store directly** (`{qname, object-schema-hash, observed-at, values}`); no review, floats under max-age, recorded per-compile. Auto by design.

- **I-2-α · One connector frame for everything.** Data-engine connectors are E-4 connectors with a different ingest mapping (INFORMATION_SCHEMA / catalog APIs → proposal docs + stats entries); the metadata server's refresh-scheduling organ (stolen from Ariadne) drives them; external-metadata-server connectors (OpenMetadata import) are the same frame again with a catalog-API mapping.
  - *Buys:* one SPI, one auth story (H), one scheduler; E built the frame *for* this; orchestrator, data-engine, and catalog connectors differ only in mapping.
  - *Costs:* the frame must carry two output kinds (proposals vs stats writes) — a small widening, declared.
- **I-2-β · Dedicated harvester organ per engine kind** inside the metadata server (Ariadne's QueryParseWorker heritage grown per-engine). *Costs:* re-invents the connector frame privately; third parties can't add engines. Catalogued to reject.
- **I-2-γ · Agent-assisted harvest** — kantheon's intelligence proposes models from messy metadata (C4-e/TTR-B flavored). *Parked:* rides on kantheon's side of the split; the *proposal* mechanics below are deliberately agent-compatible (a proposal is a proposal, whoever drafts it).

**The MIT/platform line (a pin worth stating):** a **one-shot introspection CLI** (`ttr import-schema <connection>` → generated world/model docs, run by a human, no service) is *generator-shaped* — **MIT-side** (P1 fuel: standalone users bootstrap worlds without hand-typing schemas); **continuous, scheduled harvest** = runs/stores/schedules ⇒ **Platform** (A-α verbatim). Same mapping code, two packagings (the B-1 two-bindings pattern, once more).

**Lean: α, with the temperament split pinned and the one-shot-CLI/continuous-harvest line drawn.**

## I-3 · Proposal mechanics (how harvested canon enters repos)

**Question:** a connector drafted a world-entry update (new table, changed column). How does it become canon?

- **I-3-α · PR-shaped proposals.** The connector opens a branch/PR against the target repo (platform world repo for instance entries — K; project repos for model docs) with generated TTR documents; humans review the *diff*; merge = ratification. G-1-γ's write model reused verbatim — **every writer writes through git, including robots.**
- **I-3-β · Staging area in the metadata server.** Proposals queue in a UI; accept = the server commits. *Really α with a friendlier skin* — acceptable only if the underlying mechanics stay git (audit, revert, review).
- **I-3-γ · Auto-commit with notification.** Trust the harvester; humans get a changelog. *Costs:* canon mutated without review — P3's line; one bad mapping rewrites forty worlds overnight. Reject for canon (stats already bypass legitimately by temperament).

**Lean: α, with β as the Designer UX over it** (the reader panels grow a "pending proposals" view; accept = merge underneath). Generated docs must be **deterministic** (same external state ⇒ same proposal bytes — re-running a harvest must not churn diffs; the E-1 determinism obligation applied to connectors).

## I-4 · Megaproviders (GI-3) — same frame or special?

- **I-4-α · Megaprovider = a family of connectors.** Fabric/Purview, BigQuery/Dataplex, etc. are harvest/export connector kinds like any other; nothing architecturally new.
- **I-4-β · Dedicated per-ecosystem integration packages** — deeper coupling (auth ecosystems, native artifact formats, marketplace presence).
- **The carve-out that decides it:** the **PowerBI semantic-model ↔ TTR-M `md` mapping is a *modeling* feature, not plumbing** — it maps *meaning* (measures, calc groups — `MD_CALC_CATALOG` adjacency), not catalog entries. It belongs to a future TTR-M arc (**IQ-1**), not to I's connector roster. With that removed, nothing megaprovider-shaped remains that the connector frame can't carry.

**Lean: α**, IQ-1 carved out and routed to TTR-M. Commercial-tier connectors (Collibra, Purview) = the same demand-driven market logic as EQ-3's WLA tier.

## I-5 · The anchor external system (the EQ-3 discipline, applied to catalogs)

The anchor = the system the import connector, export connector, and conformance tests are built against **first**. Field sketch (pre-research; an EQ-3-style researched dive is available on request — **IQ-3**):

1. **OpenMetadata** — open-source, actively growing; hero life 3 names it; C-2-δ already studied its schema; OpenLineage affinity; **CI-testable** (spin it up in conformance, like Airflow in EQ-3's logic). The presumptive anchor.
2. **DataHub** — the other big open-source catalog; strong ingestion framework (prior art for our connector mappings); event-driven architecture. Strong second.
3. **Amundsen** — aging momentum; catalogued, unlikely anchor.
4. **Collibra / Alation** — enterprise-commercial governance; the **commercial-connector market** (WLA-tier logic), not anchor material.
5. **Purview/Fabric, Dataplex** — megaprovider catalogs; I-4-α connectors when demand arrives; entangled with IQ-1 on the Azure side.

**Lean: OpenMetadata anchor, DataHub second** (the second connector proves the mapping layer isn't OpenMetadata-shaped — EQ-3's SPI-proving argument verbatim). Whether the second enters v1 scope = Bora's call at convergence (the EQ-3 precedent widened; the same trade here is real but smaller — one mapping, not three deliverables).

---

## Hero rendering ("one program, three lives")

- **Life 1:** `ttr import-schema` introspects the user's Postgres once, generates a draft world — they review, commit, compile. MIT end to end (I-2's pin).
- **Life 2:** the platform's Postgres connector (scheduled by the metadata server) notices a new column on `accounts` → opens a **proposal PR** against the platform world repo → the data owner merges → the lock diff shows the world changed (K/BQ-3) → next compile sees it. Meanwhile stats flow into the BQ-2 store hourly, unreviewed, recorded per-compile.
- **Life 3:** lineage (column-grain) exports to OpenMetadata via C-2's connector (decided); OpenMetadata's own entries for non-TTR assets can be *imported as proposals* if the org wants TTR worlds to reference them — one direction at a time, never sync.

## Cross-links out

- **→ B/K:** proposals land as ordinary canon (lock diffs, reviewed); stats land in the BQ-2 store; nothing new crosses the seam.
- **→ E:** the connector frame widens to two output kinds (proposals, stats) — EQ-4 answered; determinism obligation extends to connector mappings.
- **→ C:** the refresh-scheduling organ drives harvest; the export connectors (decided) are I's outbound siblings.
- **→ G:** "pending proposals" panel = a reader-set addition (v1.x); the wizard and proposals share the commit plumbing.
- **→ H:** connectors = service principals + secret refs (settled); proposal PRs are attributable to the connector's principal (audit).
- **→ TTR-M:** IQ-1 (PowerBI `md` mapping arc). **→ J:** connector naming conventions.

## Open questions (I-local)

- **IQ-1 · PowerBI semantic-model ↔ TTR-M `md` mapping** — a modeling arc (MD_CALC_CATALOG adjacency), explicitly out of I's connector scope; route to TTR-M planning.
- **IQ-2 · Proposal fidelity & qname discipline** — how external names map to TTR qnames deterministically (collisions, case, schema-qualification); work item for the first connector's mapping spec.
- **IQ-3 · Anchor research dive** — EQ-3-style field scan (current momentum, API quality, OpenLineage state) before ratifying I-5, if desired.
- **IQ-4 · Export completeness** — what of TTR's model semantics maps lossy into OpenMetadata/OpenLineage (worlds? manifests? FS/SS edges?); the export connector's documented limitation list (C-2 side, but I owns the mapping honesty).

## Convergence status

**🟢 I IS CONVERGED (2026-07-09, Bora — all leans ratified):**

- **I-1 = α + decided outbound: "PROPOSALS IN, PROJECTIONS OUT — never sync, never federate."** Two one-way flows, no object has two writers, conflict machinery never exists; drift detection automatic + advisory, ratification human. **Resolves LF-7 — the effort's last load-bearing fork.** · Rejected: β sync (heterogeneous conflict rules; external mutation of canon — P3); γ federation (banned from compile by B-5-δ; two truth domains in one UI); δ (demoted by C-2).
- **I-2 = α:** one connector frame (E-4's, widened to two output kinds) — **EQ-4 answered: yes, shared frame**; temperament split pinned (schemas → reviewed proposals; stats → BQ-2 store direct); refresh-scheduling organ drives it; **the MIT/platform line drawn: one-shot `ttr import-schema` CLI = MIT (generator-shaped, P1 fuel); continuous scheduled harvest = Platform** (A-α). · Rejected: β private harvester organs; γ agent-assisted (parked, proposal-compatible by design).
- **I-3 = α (+β as Designer UX):** proposals are **PR-shaped — robots write through git too** (G-1-γ universalized); generated docs deterministic (same external state ⇒ same proposal bytes; the E-1 obligation extended to connectors); "pending proposals" panel = a G reader-set addition (v1.x). · Rejected: γ auto-commit for canon (P3).
- **I-4 = α:** megaproviders = connector families; **IQ-1 (PowerBI semantic-model ↔ TTR-M `md`) carved out to a TTR-M modeling arc**; commercial catalogs (Collibra, Purview) = demand-driven commercial-connector tier (EQ-3's WLA logic).
- **I-5 = OpenMetadata anchor (Bora: "OpenMetadata it is").** v1 = the OpenMetadata connector pair (import proposals + the C-2-decided export); **DataHub = first follow-on target, consciously NOT widened into v1** (the EQ-3 second-target argument noted; here it's one mapping, not three deliverables — the widening was declined). IQ-3's research dive not needed for the pick; may still inform the mapping spec at implementation.

**Riders out:** IQ-1 → TTR-M planning · IQ-2 (qname mapping discipline) + IQ-4 (export lossiness list) → the connector mapping spec (planning) · "pending proposals" panel → G v1.x.
