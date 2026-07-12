# Tatrman Platform — Repo & Infra Topology Options (workstream D)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Divergence catalogue for **D — the Kantheon split's mechanics**: which repos exist afterwards, where the license boundary runs, who owns contracts, where infra lives, and how the split sequences. Session opened 2026-07-08.
> Companions: [Control Room](./00-control-room.md) · [`03-service-architecture-options.md`](./03-service-architecture-options.md) (the service map D houses).
>
> Inherited riders from C: **CQ-1** (kantheon transition sequencing) and the **grey-zone placement sweep** (→ D-6).

## Inherited constraints

- **FI-3:** Kantheon (intelligence) stays in Olymp; the Platform gets its own Olymp-like infra repo with cluster definitions.
- **P2 (as clarified):** build-time dependency arrow is one-way — `tatrman (OSS) → platform → kantheon`; runtime plugins on Platform-defined SPIs are legal. **The arrow must hold at every intermediate state of the split.**
- **C-1:** β-spine (re-found: metadata server, program door) + α-leaves (transplant: workers, Charon, Kyklop, whois, slimmed Theseus), sequenced metadata + spine + workers first.
- **Precedents:** MD arc + TR arc = the extraction pattern (lib moves to tatrman, published `org.tatrman:*` artifacts, kantheon adopts thin wrappers); tag-driven publishing (`PUBLISHING.md`); this repo itself was forked from `Collite/modeler` with full history (the fork muscle exists).
- **Current repo estate (verified):** `tatrman` (this) · `kantheon` · `olymp` (infra: `clusters/{bp-dsk, bp-olymp01, local}`, bootstrap, justfile) · `modeler` (frozen) · `tatrman-semantics` (docs-only satellite).

---

## D-1 · Where does the Platform's code live?

- **D-1-α · New `tatrman-platform` repo.** Fresh repo; re-founded services (metadata server, program door) start here; transplanted leaves arrive via history-preserving moves (`git filter-repo`, the modeler→tatrman fork muscle).
  - *Buys:* a repo whose shape *is* the C service map (no inherited layout debt); the license story is repo-clean (D-2); re-founding wants a blank page; kantheon keeps its identity and history intact.
  - *Costs:* cross-repo coordination tax (tatrman libs ↔ platform services — version pinning, the Maven-Local dance); transplant moves lose in-place history (mitigated by filter-repo).
- **D-1-β · Platform inside this monorepo** (`services/` beside `packages/`).
  - *Buys:* one-repo ergonomics; compiler-lib changes and their platform consumers land atomically; the two-mode seam is exercised in one CI.
  - *Costs:* the MIT repo grows a non-MIT wing (per-dir licensing — D-2-β's problems); OSS contributors clone a platform they can't run; repo size/CI entanglement; the "editor/compiler tooling only, never talks to services" identity of this repo (CLAUDE.md) dies.
- **D-1-γ · Repo-per-service.** Kantheon-style federation (metadata-server repo, scheduler repo, …).
  - *Buys:* independent cadences, small blast radius. *Costs:* maximal coordination cost *during* the split (contracts churn hardest exactly now); premature — services can graduate out of a platform monorepo later (the reverse is painful).
- **D-1-δ · Weird: rename kantheon → `tatrman-platform`; the *intelligence* moves out.** The deterministic majority stays put with its git history; agents/frontends/prometheus/kadmos/… move to a fresh `kantheon` repo.
  - *Buys:* least code motion for the hall/leaves; their in-place history preserved.
  - *Costs:* the *renamed* repo inherits every kantheon shape C-1 chose to re-found (single-query spine at the center, `org.tatrman.kantheon.*` proto roots, agent-era docs); the intelligence half — the part that keeps the *name* kantheon — loses *its* history instead; identity chaos in every doc that says "kantheon" today. The C-1 compound (re-found the spine) argues squarely against inheriting the old center.

**Lean: α** — a new `tatrman-platform` repo (name → J); re-found services born there, leaves filter-repo'd in. β catalogued as the fallback if cross-repo tax proves brutal; γ = a later graduation path, not a start.

**RESOLVED 2026-07-08 → D-1 = α (new platform repo; re-found services born there; leaves arrive via history-preserving filter-repo moves).** Rejected: β monorepo (license wing, identity loss of the OSS repo); γ repo-per-service (premature federation); δ kantheon-rename (inherits the single-query center C-1 re-founds; identity chaos).

## D-2 · License boundary vs repo boundary (LF-3, Q-4)

- **D-2-α · Boundary = repo boundary.** `tatrman` = 100% MIT (FI-2's list *is* this repo's content); `tatrman-platform` = the commercial/controlled license (pick → A/J); no per-directory carve-outs anywhere.
  - *Buys:* zero license ambiguity per clone; OSS hygiene (LICENSE at root means what it says); contribution/CLA story is per-repo simple.
  - *Costs:* every component must pick a repo — no "mostly-MIT repo with one closed dir" escape hatch; Q-4 (Designer frontend) must be answered, not deferred.
- **D-2-β · Per-directory licenses in a shared repo.** (Only live if D-1-β.) *Costs dominate:* tooling, contributor confusion, `pnpm -r` builds crossing license lines. Catalogued.
- **D-2-γ · Everything source-available; the product is operations.** (A-δ echo — parked with A's monetization item.)

**The Q-4 sub-fork (Designer frontend placement), under D-2-α:**
- **Q-4-a · Frontend stays in tatrman (MIT).** One React app, backend-selectable (MD6 adapter): browser-worker LSP / loopback designer-server / **platform metadata server**. The Platform's Designer is the same MIT app pointed at a platform backend; platform-only UI (multi-user presence, run views, lineage) arrives via a **plugin/extension surface** the MIT app defines.
- **Q-4-b · Frontend forks platform-side.** The platform Designer becomes its own app (G-β's shape). Divergence cost forever; G leaned against.
- **Q-4-c · Frontend moves to the platform repo entirely.** Standalone IDE users lose the rich viewer (FI-2 explicitly keeps a view-only Designer in IDEs — violates framing).

**Lean: α + Q-4-a** — repos are license-pure; the Designer frontend stays MIT with a backend adapter + extension surface; platform-only panels ship as platform-side extensions (same pattern as the validator SPI: OSS defines the socket, the Platform ships the plug).

**RESOLVED 2026-07-08 → D-2 = α (license boundary = repo boundary; tatrman 100% MIT, platform repo commercially licensed, no per-dir carve-outs) + Q-4 = a (Designer frontend stays MIT; backend-selectable; platform panels = platform-shipped extensions on an MIT-defined extension surface).** Resolves control-room **Q-4**. Rejected: per-dir licensing; frontend fork; frontend moves platform-side (violates FI-2's IDE viewer).

## D-3 · Contract & artifact ownership

**Question:** the split multiplies shared contracts. Who owns what? The working rule so far (TR-3): **tatrman owns wire formats kantheon consumes.** Extend or amend?

Contract inventory: plan protos (`plan.v1`, owned by tatrman — TR-3) · world/manifest schemas (tatrman, language-adjacent) · **snapshot-archive format + lock format** (B) · **stats-entry schema** (BQ-2) · **validator SPI** (C-5-i) · **door protos** (program door, query door) · Charon/worker protos (`worker.v1`, `metis.v1`, …) · lineage/export shapes (OpenLineage-ish).

- **D-3-α · Language-adjacent contracts in tatrman; service contracts in the platform repo.** Rule: *if the OSS compiler/toolchain reads or writes it, tatrman owns it* (plan protos, world schemas, snapshot/lock/stats formats, validator SPI *interface*); *if only platform services speak it, the platform owns it* (door protos, worker protos, internal service protos). Kantheon owns nothing shared.
  - *Buys:* the OSS toolchain is self-describing (a standalone user can read every format their artifacts touch); P2-clean (platform depends on tatrman contracts, never reverse); one rule, no committee.
  - *Costs:* the boundary needs judgment calls (is the query-door proto "toolchain-read"? — the Designer's preview path might call it… via the metadata server, so: platform-owned, fine).
- **D-3-β · A dedicated contracts repo.** All shared protos/schemas in one place.
  - *Buys:* single registry. *Costs:* a third thing to version; every change fans out two repos anyway; the TR arc *just* established tatrman-owns — reversing it churns for no gain.
- **D-3-γ · Weird: contracts as a published TTR model.** The contracts themselves authored as TTR documents (schemas as `def` docs), generating protos/JSON-schemas.
  - *Buys:* dogfooding; machine-checked contract evolution. *Costs:* the toolchain isn't there yet; bootstrap circularity (the contract for snapshots… inside snapshots). Parked — revisit as a v2 dogfooding arc.

**Lean: α** with the rule stated crisply: **"toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned."**

**RESOLVED 2026-07-08 → D-3 = α: "toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned"; kantheon owns nothing shared; dependency chain `tatrman → platform → kantheon`, one-way, three tiers.** Rejected: contracts repo (third thing, reverses TR-3 for no gain); contracts-as-TTR (parked as a v2 dogfooding arc).

## D-4 · Infra topology (the Olymp twin)

- **D-4-α · New infra repo, olymp-shaped.** Cluster definitions for platform deployments (name candidate → J; "Tatry" noted); k8s charts stay *in* the platform repo beside services (kantheon's working pattern: `services/*/k8s`, justfile deploys).
  - *Buys:* mirrors a proven working setup; product packaging (charts) versions with code; instances (clusters) live apart from product.
  - *Costs:* two infra repos to keep honest (drift in conventions).
- **D-4-β · One infra repo for both** (olymp grows `clusters/tatrman-*`).
  - *Buys:* one ops surface for the org. *Costs:* FI-3 says *separate* ("similar infra repo"); access/audit boundaries blur exactly where the license boundary was just drawn; a platform *customer* deployment can't consume olymp.
- **D-4-γ · Infra fully in the platform repo.** Charts + cluster defs together.
  - *Buys:* one clone runs everything. *Costs:* cluster instances are *operational state* of particular deployments, not product; customers' cluster defs don't belong in the product repo.
- **D-4-δ · Weird: the Platform ships as a Helm umbrella chart / operator only** — there *is* no infra repo in the product's world; every deployment (ours included) is an instance of the packaged chart, and our own cluster defs are just the first customer's private repo.
  - *Buys:* forces product-grade deployability from day one (the Platform is *sold*, eventually — deployability is the product); our infra repo loses specialness, which is honest.
  - *Costs:* umbrella-chart/operator engineering up front; day-one overkill while the only deployment is ours.

**Lean: α now, with δ as the declared direction** — start olymp-shaped (fast, proven), but treat our infra repo as "deployment instance #1," keeping product packaging (charts) in the platform repo so the δ graduation is a packaging arc, not a re-architecture.

**RESOLVED 2026-07-08 → D-4 = α-now-δ-direction (olymp-shaped infra repo as "deployment instance #1"; charts live in the platform repo; ship-as-chart/operator is the declared graduation path).** Rejected: shared olymp (FI-3; customer deployments can't consume it); cluster defs in the product repo.

## D-5 · Split sequencing (CQ-1)

- **D-5-α · Extraction-arcs-first, serial.** Finish MD arc → TR arc → spine libs → then move services. Safest per-step; slowest to a visible platform.
- **D-5-β · Big bang.** One coordinated split PR-storm across repos. Catalogued to reject: P2-at-every-intermediate-state is unverifiable mid-bang; nothing ships until everything does.
- **D-5-γ · Strangler.** The platform repo opens with the *new* organs (metadata server, program door) consuming already-published `org.tatrman:*` libs; leaves transplant one-by-one as the new organs need them (workers+Kyklop with the program door; Charon with the first cross-engine deploy; whois with H). **Kantheon keeps its own spine throughout** and adopts the platform's query door *last*, via a kantheon-side adoption arc — answering **CQ-1: no day-one switch; the mini-spine duplication is accepted, time-boxed, and P2-legal** (both consume published libs; neither consumes the other until the door is proven).
- **D-5-δ · Weird: platform-greenfield only.** Build metadata server + program door; *never* transplant — kantheon keeps hall + workers indefinitely and the Platform dispatches *to kantheon's hall* over its APIs. Rejected-shaped: inverts the arrow (Platform depends on kantheon services) — P2 violation at the steady state, not just transitionally.

**Lean: γ (strangler), with α's discipline per move** (each transplant = a mini-arc: move, adopt, delete). Sequencing skeleton: ① MD+TR arcs complete (already planned) → ② platform repo bootstrapped: metadata server v1 (B contract + Designer serving) → ③ program door + workers/Kyklop transplant → ④ Charon → ⑤ Argos+SPI, whois-descendant (with H) → ⑥ kantheon query-door adoption arc (CQ-1 closes) → ⑦ scheduler (F) et al.

**RESOLVED 2026-07-08 → D-5 = γ strangler with per-move mini-arc discipline; sequencing skeleton ①–⑦ adopted; CQ-1 answered (kantheon keeps its mini-spine, adopts the query door last — accepted, time-boxed, P2-legal duplication).** Rejected: big bang (P2 unverifiable mid-bang); greenfield-forever (steady-state P2 inversion).

## D-6 · Grey-zone placement sweep (rider from C)

| Service | Verified identity | Verdict | Revisit-when |
|---|---|---|---|
| `kallimachos` | Compiled-wiki corpus engine (Librarian/DocWH arc) | **stays kantheon** (document-intelligence substrate) | — |
| `report-renderer` | XLSX/PPTX/PDF/HTML rendering from templates (Midas arc; POI + Playwright) | **stays kantheon** (agent/product output surface) | if the Platform grows report-shaped Display sinks (TTR-P Display transport) — then *consume*, don't move |
| `metis` | SARIMAX/Prophet/regression over prepared Arrow series; **already Charon-stageable via `metis.v1` Import/Export (worker-shaped edges)** | **stays kantheon for now** — but flagged: metis is a *worker in disguise* | when the platform worker SPI (CQ-4 vocabulary) lands, metis is the test case for "engine plug-in by manifest" (a `stats` engine kind) |
| `pinakes` | **UNVERIFIED** — no README, no architecture-doc entry (name suggests catalog) | placement deferred | verify identity during ② (platform-repo bootstrap); decide then |
| `echo`, `kadmos` | Fuzzy matcher; NLP foundation | **stay kantheon** (confirmed from C-0) | — |

**Lean:** ratify the table as-is; pinakes gets a verify-then-place task inside the D-5 sequence.

**RESOLVED 2026-07-08 → D-6 = table ratified as-is** (kallimachos/report-renderer/echo/kadmos stay; metis stays-but-flagged as the engine-by-manifest test case; pinakes = verify-then-place task in step ②).

---

## Cross-links out

- **→ A:** D-2-α makes the license boundary physical; A's edition rule (compile vs operate) can now be ratified against a concrete repo map. Q-4 answered (pending ratification).
- **→ J:** naming demands created: platform repo name, infra repo name ("Tatry" candidate parked), extension-surface name, license name (with A).
- **→ F/G/H:** their designs land in the D-1-α repo; H inherits whois transplant timing (⑤); G inherits the Q-4-a extension surface.
- **→ kantheon repo:** two arcs to plan there when D converges: query-door adoption (⑥) and intelligence-side doc sweep (what "kantheon" means post-split).

## Open questions (D)

- ~~**DQ-1:** platform build system~~ **RESOLVED 2026-07-08 → Gradle-only** (Q-4-a keeps all TS in tatrman).
- ~~**DQ-2:** proto/package roots on transplant~~ **RESOLVED 2026-07-08 → sweep on arrival** (TR/MD precedent).
- ~~**DQ-3:** `tatrman-semantics` satellite repo~~ **RESOLVED 2026-07-08 → obsolete, no interesting content; archive/ignore.**

## Convergence status

**🟢 D IS CONVERGED (2026-07-08)** — D-1 new platform repo · D-2 license=repo + Q-4-a MIT frontend w/ extension surface · D-3 "toolchain-touched ⇒ tatrman-owned" · D-4 α-now-δ-direction infra · D-5 strangler ①–⑦ (CQ-1 closed) · D-6 sweep ratified · DQ-1 Gradle-only · DQ-2 sweep-on-arrival · DQ-3 tatrman-semantics obsolete. Naming demands handed to **J** (platform repo, infra repo — "Tatry" parked, extension surface, license). Kantheon-repo arcs to plan there post-design: query-door adoption (⑥) + post-split doc sweep.
