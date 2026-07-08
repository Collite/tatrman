# Tatrman Platform — Control Room

> The single dashboard for the **Tatrman Platform** design effort: making the **two-mode** (standalone / connected) architecture a robust, first-class design choice, and carving the deterministic half of Kantheon into a platform that Tatrman owns.
> Open this first every session. Companion docs: [`01-design-space-map.md`](./01-design-space-map.md) (the branches), [`02-mode-seam-options.md`](./02-mode-seam-options.md) (workstream B).
>
> **Status:** Framing + first divergence (B). Started 2026-07-08.

---

## 0. How we run this

Same protocol as the TTR-P effort (`../../ttr-p/design/00-control-room.md` §0): **diverge before we converge**; three gears (Framing → Divergence → Convergence); ≥3 alternatives per fork including the weird one; leans are not decisions; deferred is a tracked outcome; append-only decision log is ground truth.

Cross-effort citations use the source prefix: `TTR-P G-b`, `MD3` (ttr-metadata), `TR-3` (ttr-translator). This effort's own IDs are bare letters.

---

## 1. Workstream dashboard

Status legend: ⚪ not started · 🔵 diverging · 🟡 options captured · 🟢 converged/decided · ⏸ parked

| # | Workstream | Status | Core question | Notes |
|---|---|---|---|---|
| **A** | Product split, scope & licensing | ⚪ | What *precisely* ships in MIT standalone vs the Platform; where is the edition boundary drawn, and by what rule? | Split itself fixed (FI-1..4); the boundary *rule* and grey-zone items (optimizer depth, Designer server, conformance harness) are open. |
| **B** | The mode seam (compiler/optimizer contract) | 🟢 | What exactly does "connected" change for compilation and optimization — and what may it never change? | **Converged 2026-07-08** → `02-mode-seam-options.md` (B-1 β SPI seam, mode-blind compiler · B-2 β stats overlay · B-3 α hard parity · B-5 δ fetch-then-compile · B-6 γ one core · BQ-1 archive · BQ-2 γ per-object keying + inline recording · BQ-3 layered lock-for-canon/max-age-for-stats · BQ-4 plugins in lock). Owed: C confirms server contract; H confirms advisory-only compile policy. BQ-5 (lock scope) rides with F/D. |
| **C** | Platform service architecture | 🔵 | The service roster and boundaries: metadata server, workers, dispatcher, movement, scheduler, security, Designer backend — what moves from Kantheon, what's new, what dies? | Diverging 2026-07-08 → `03-service-architecture-options.md`. Q-2 verified first (whois = the OPA carrier, not Argos). |
| **D** | Kantheon split & repo/infra topology | ⚪ | Which repos exist after the split; where does the Platform's code and its Olymp-like infra repo live; how do the extraction arcs sequence? | ttr-metadata + ttr-translator arcs are the pattern (thin-wrapper extractions). |
| **E** | Orchestration-engine integration | ⚪ | Orchestrators (Dagster, Airflow, bash, …) as *compiler emit plugins* AND as *platform-registered engines* — one mechanism or two? | GI-1. Builds on TTR-P B-T6 invocation bindings + executor manifests. |
| **F** | Scheduler & job execution | ⚪ | The platform server that schedules and runs jobs — in workers and via registered orchestrators. | This is where TTR-P **F-proper** (orchestrator, events, FF, retries/resume, on-failure islands, runtime params) lands. |
| **G** | Designer evolution | ⚪ | Browser Designer on the Platform: writes, multi-user, collab; vs standalone view-only `.ttrl` in IDEs. | Starts from ttr-designer-server (S24 loopback, read-only v1) + MD6 WS adapter. |
| **H** | Security & governance | ⚪ | The OPA-based security server bound to metadata: enforcement points, identity, what (if anything) security means in standalone mode. | Kantheon's Argos/RLS machinery is prior art. |
| **I** | External metadata & megaproviders | ⚪ | Connecting to OpenMetadata / Collibra / Amundsen …; Google & Azure ecosystems (PowerBI for MD, Fabric for metadata). Direction of truth: import, sync, or federate? | GI-2, GI-3. |
| **J** | Naming & conventions | ⚪ | Platform name, service names (mythology continues?), repo names, artifact coordinates, edition names. | Late, after shapes exist. No bikeshedding before purpose is pinned. |

---

## 2. Framing inputs (Bora, 2026-07-08 — FIXED)

The split itself is **decided framing**, not a fork (Bora's call, this session's opening):

- **FI-1 · Two modes are FIRST-CLASS.** The compiler/optimizer works in (1) **standalone mode** — reading only repositories, static, offline; (2) **connected mode** — connected to the Tatrman metadata server. This is to be made robust and first-class, not a bolt-on.
- **FI-2 · Standalone = MIT open source.** The languages (TTR-M/TTR-P/TTR-B), textual representations, IDE extensions, and the **compiler** — which emits *scripts to be run* (different scripts for different engines) and is **not an execution platform**. Repositories are the only backing store. Designer in standalone = **view-only over `.ttrl`/model files inside IDEs**.
- **FI-3 · Connected = the Tatrman Platform.** The Platform **takes ownership of the workers from Kantheon**, splitting Kantheon in two: the **working (deterministic) platform → Tatrman Platform**; the **intelligence (agents + LLMs) → Kantheon**, which stays in **Olymp**. The Tatrman Platform gets its own Olymp-like infra repo with cluster definitions.
- **FI-4 · Minimum Platform roster:** (1) workers; (2) Designer as a browser app — later writes, multi-user; (3) metadata server; (4) security server bound to the metadata (the OPA machinery from Kantheon). "Basically the whole deterministic part of Kantheon."

### Grounding inputs (Bora, 2026-07-08 — diverge-worthy ideas, not decisions)

- **GI-1 · Orchestration engines as compiler plugins.** Emit script/XML/YAML/… for Dagster / Airflow / … / bash. The Platform can additionally **host and register** orchestrators (like it registers databases) and work with their live-ish metadata, statistics, and contents.
- **GI-2 · Connect to existing metadata servers** — OpenMetadata, Collibra, Amundsen, ….
- **GI-3 · Connect to megaproviders** — Google ecosystem; Azure ecosystem incl. PowerBI for MD work, Fabric for metadata; ….
- **GI-4 · A platform server to schedule and run jobs** — in the execution engine(s) and by connecting to existing orchestration engines.

---

## 3. What we already have (asset inventory)

The TTR-P + extraction-arc corpus already points at this split; we formalize and extend, not invent:

- **TTR-P T6 · world = compile target.** The world doc describes the surroundings; the compiler compiles *against* it offline; the runtime **verifies compatibility** — never the compile-time source of truth. This is the strongest existing hint at the mode seam: connected mode plausibly changes *where the world comes from*, not what compiling means.
- **TTR-P D-g · offline compile.** The compiler embeds the metadata component and reads model repo + world docs directly from paths — no service at compile time. Kantheon workspace metadata = a *population source* for worlds.
- **TTR-P G-g · one-way seam.** Kantheon consumes published `org.tatrman:*` artifacts and compiled plans; never a running Tatrman service; softened only for agents authoring source (C4-e).
- **TTR-P F-lite vs F-proper.** F-lite (bash wave-parallel bundles, fail-fast, Arrow staging, `TTR_CONN_*` env credentials, JSON manifest + semantic world fingerprint) is converged and is *exactly the standalone story*. **F-proper — orchestrator, events, FF, retries/resume, on-failure islands, runtime params — was deferred "to v2 with the Kantheon orchestrator"; it now has an address: workstream F of this effort.**
- **ttr-metadata arc (2026-07-05).** `org.tatrman:ttr-metadata` (+`-git`) extracted from Ariadne; storage SPI (`ModelStorage`: LocalFs / Classpath / GitArchive); world resolution; `ttr-designer-server` (repo-attached, loopback-only S24, read-only v1); Ariadne becomes a thin gRPC wrapper in kantheon. **The metadata *library* is done-by-design; the metadata *server* of FI-4 is not — its relationship to Ariadne is a load-bearing fork (LF-2).**
- **ttr-translator arc.** `org.tatrman:ttr-plan-proto` + `org.tatrman:ttr-translator`; tatrman owns the plan wire format (TR-3/S25). Precedent: the Platform can own wire contracts kantheon consumes.
- **Kantheon roster** (the deterministic candidates for the carve-out): **Theseus** (orchestrate) · **Proteus** (translate; already thinning to a ttr-translator wrapper) · **Argos** (validate/RLS — presumed home of "the OPA thing", verify) · **Kyklop** (dispatch) · workers **Brontes** (MSSQL) / **Steropes** (Polars) / **Arges** (PG) · **Charon** (data movement) · **Ariadne** (metadata; thinning per MD arc). Intelligence side (stays Kantheon): **Pythia** + agents/LLM machinery.
- **Designer** (`packages/designer`, TS/React) with the MD6 WS data-source adapter — the seed of the browser Designer.
- **Olymp** — kantheon's infra repo (cluster definitions); the template for the Platform's infra repo.

---

## 4. Load-bearing forks (decide consciously, not by drift)

1. ~~**LF-1 · The seam object.**~~ **RESOLVED 2026-07-08 → source SPI behind a mode-blind compiler (B-1 = β), with snapshot/lockfile discipline.** See decision log.
2. **LF-2 · What is the metadata server?** Ariadne comes home to tatrman · grow `ttr-designer-server` into it · a new service on the `ttr-metadata` library · adopt-an-external (OpenMetadata as *the* backend). → C, touches I.
3. **LF-3 · Repo & license boundary.** Same monorepo with per-dir licenses · separate `tatrman-platform` repo · repo-per-service. License boundary and repo boundary need not coincide — decide both. → D.
4. **LF-4 · Plugin mechanism.** Emit targets (Dagster/Airflow/…): data-driven manifests + core codegen · JVM SPI plugins · out-of-process plugins. And is the *platform registration* of an orchestrator the same mechanism or a second one? → E.
5. ~~**LF-5 · Where does connected compilation run?**~~ **RESOLVED 2026-07-08 → both, one compiler core (B-6 = γ); B-3-α parity makes location a deployment convenience.**
6. **LF-6 · Scheduler: build vs delegate.** Own scheduler service · "a registered orchestrator IS the scheduler" · a thin trigger layer over both. → F, E.
7. **LF-7 · Direction of metadata truth** vs external servers/megaproviders: import (population source, TTR-P D-g stance) · bidirectional sync · live federation. → I.
8. **LF-8 · Security reach.** OPA enforcement platform-only, or does the OSS compiler carry a policy hook (compile-time advisory checks)? What does standalone security even mean? → H, A.

---

## 5. Design principles (P-n; cite by ID)

- **P1 · Standalone is not a demo. (2026-07-08, proposed this session — ratify)** The MIT compiler is complete, correct, and useful alone; connected mode **adds capability** (live worlds, statistics, execution, scheduling, security, collaboration) — it never *fixes* standalone, and no feature of the *language* is gated on the Platform. The open-source story must survive on its own merits.
- **P2 · One-way arrow, generalized. (2026-07-08, proposed — ratify)** OSS never depends on the Platform; the Platform consumes published `org.tatrman:*` artifacts (the Ariadne/Proteus extraction pattern as *the* law of the split). No circular coupling anywhere in the family.
- **P3 · No miracles. (inherited = TTR-P P2)** Explicit, or deterministically derived from declared defaults; otherwise an error. Applies with full force to the mode seam: connected mode must not introduce invisible compile inputs.

---

## 6. Hero scenario (carried through every workstream)

**"One program, three lives."** The TTR-P hero program (accounts SQL + sales CSV → join → summarize → branch, two engines):

1. **Standalone life:** compiled offline against a repo world → `.bundle/` (F-lite bash artifact) → run by hand. MIT tools only.
2. **Platform life:** the same program, world served by the Platform's metadata server (live schemas + statistics) → deployed, **scheduled nightly**, executed on Tatrman workers (Arges + Steropes), Charon moves data, OPA authorizes who may run/see what, run history and lineage in the metadata server.
3. **Federated life:** the Platform has Dagster registered as an orchestration engine and OpenMetadata connected; the program is emitted as a Dagster DAG, scheduled there, and its lineage/metadata appear in OpenMetadata.

Every workstream renders its options against these three lives.

---

## 7. Decision log

> Append-only. Format: `YYYY-MM-DD · [id] · Decision · Why · Alternatives rejected`.

- **2026-07-08 · [FRAME] · FI-1..FI-4 accepted as fixed framing** (two first-class modes; standalone = MIT OSS compiler; connected = Tatrman Platform owning Kantheon's deterministic half; Kantheon keeps intelligence, stays in Olymp; Platform gets its own infra repo). · Bora's opening statement, confirmed as framing-not-fork. · Alternatives (single OSS platform; plugin-tier editions) consciously not explored — the split is a product decision, not a design output.

### B / mode seam (full options + resolutions in `02-mode-seam-options.md`)

- **2026-07-08 · [B-1 = β] · The mode seam is a SOURCE SPI: the compiler is MODE-BLIND.** It always compiles against `(models, worlds, manifests, statistics)` through the source SPI (`ttr-metadata`'s `ModelSource`/`ModelStorage` line); standalone binds `LocalFsStorage`, connected binds a `MetadataServerSource`. "Connected" is a *binding* chosen in project config, not a behavior. Extends T6/D-g: connected changes *where the world comes from*, never what compiling means. Resolves **LF-1**. · Rejected: α mode flag (mode-awareness smears across every phase); γ two compilers (drift — the ttrp-conform disease; composes later via B-6-γ if wanted); δ sync-only (can't carry live Designer/platform compile; its lockfile discipline survives in B-5).
- **2026-07-08 · [B-2 = β] · Statistics are a SEPARATE, snapshot-pinnable SOURCE KIND** (overlay beside models/worlds; worlds stay lean and stable). **Absent stats = defined degradation to the static cost model** (TTR-P Z v1), not an error. · Rejected: stats-in-world (volatile churn inside a stable document kind); live optimizer probing (unpinnable; parked as Z 3.x behind snapshot/replay); platform-side re-optimizer (two optimizers; artifact ≠ what the author reviewed).
- **2026-07-08 · [B-3 = α] · HARD PARITY: a compile is a pure function of its recorded snapshot; mode only chooses where the snapshot comes from.** Same program + same resolved inputs ⇒ byte-identical artifacts in both modes; every input (models, world, manifests, stats, plugin versions) is part of the recorded snapshot; `ttrp-conform` gains a mode-drift suite. · Rejected: parity-modulo-stats (unneeded — B-2-β makes pinning cheap); no-guarantee (two products by drift; P1 dies).
- **2026-07-08 · [B-5 = δ] · FETCH-THEN-COMPILE is the only connected shape.** The compiler never talks to the server mid-compile; a fetch step (explicit or policy-driven) refreshes the local snapshot; unreachable server = stale snapshot, nothing else — failure semantics collapse into freshness policy. D-g ("offline, no service at compile time") is preserved *verbatim* in both modes. · Rejected: fail-hard; silent fallback to repo files (P3 violation).
- **2026-07-08 · [B-6 = γ] · ONE compiler core, bound both ways:** CLI/IDE bind it locally; the Platform's compile/deploy service binds the same version server-side; B-3-α makes location a deployment convenience. Resolves **LF-5**. · Rejected: client-only; platform-only.
- **2026-07-08 · [BQ-1] · Snapshot transport = ARCHIVE/PROTOCOL** — content-addressed snapshot archives served by the metadata server, cached locally by hash; `LocalFsStorage` remains the plain-repo binding. · Rejected: repo-shaped file tree (slower; sync-flavored).
- **2026-07-08 · [BQ-2 = γ] · Stats keyed PER OBJECT: `{qname, object-schema-hash, observed-at, values}`; validity binds to the object's own resolved shape** — a mismatch discards that object's entry only (→ static cost model for it, granular + P3-explicit); **used values are embedded verbatim in the compile record** (δ composed — B-3-α replay reads them; the artifact shows the numbers the optimizer saw). World fingerprint stays stats-free forever. · Rejected: stats versioned with the world fingerprint (hourly churn, spurious T6 failures); whole-snapshot validity (one world edit invalidates untouched objects' stats).
- **2026-07-08 · [BQ-3 layered + BQ-4] · Freshness splits by content temperament: `ttr.lock` (COMMITTED) pins canon — models, world, manifests, EMIT-PLUGIN VERSIONS (BQ-4) — by content hash into the archive cache; fetch = a reviewable lock diff. STATS FLOAT under max-age auto-refresh, never enter the lock, used values recorded per-compile.** Flags: `--frozen` (CI default) / `--offline` (cache + recorded staleness). IDE: background refresh + advisory live-drift diagnostics; server push = later C capability. Reproducibility: canon by lock, stats by compile record — B-3-α holds team-wide (same commit ⇒ same canon). · Rejected: explicit-only fetch (stale-by-default); one policy for both temperaments; stats-in-lock (hourly diff noise).
- **2026-07-08 · 🟢 WORKSTREAM B IS CONVERGED.** The standing seam-legality rule for all future connected features = B-4's line: *the seam admits data (sources, registries, overlays) and diagnostics; never identity, never side effects.* Owed confirmations: C (snapshot-archive + per-object-stats server contract buildable-cheap); H (compile-time policy = advisory-only). BQ-5 (lock scope: per project root vs per program/bundle) → settles with F/D.

---

## 8. Parking lot

| Item | Why parked | Revisit when |
|---|---|---|
| Monetization / commercial licensing model of the Platform | Product/business call, not design | A converges on the edition boundary |
| TTR-B / NL surfaces on the Platform (agent authoring via Kantheon) | Depends on Kantheon-side agent design (C4-e seam) | D settles the split; Kantheon repo plans its half |
| Multi-tenant SaaS variant of the Platform | Single-org deployment is the design anchor first | C converges |

---

## 9. Open questions (rolling)

- **Q-1 (A/H):** Does *any* security concept exist in standalone mode (e.g., policy lint against an exported policy file), or is security 100% Platform? (LF-8)
- ~~**Q-2 (C):** Is Argos actually the OPA carrier in kantheon?~~ **VERIFIED 2026-07-08 (kantheon repo): NO — "the OPA thing" is `infra/whois`,** the user/role directory + **OPA bundle server** (Keycloak/ERP sync → own Postgres; serves `UserRecord` lookups + OPA policy bundles `roles.tar.gz`). **Argos dropped OPA at the Stage 3.2 fold** — it is the PlanNode-level *validator* (RLS predicate injection, column DENY/MASK, TopN, strict coercion, admin bypass) with an **in-process HOCON policy store**, sitting Proteus→**Argos**→Kyklop; identity resolves at the theseus-mcp edge (Keycloak JWT → `PipelineContext.auth_roles`), whois = optional fail-closed role *enrichment*. Two split complications flagged for C/H: (1) **Argos carries an LLM Guard (DF-V04)** — an intelligence tendril inside the deterministic validator; (2) identity resolution lives at an *agent-side* edge (theseus-mcp). FI-4's "security server bound to metadata" therefore maps to a whois-descendant + the Argos policy engine + policy content bound to metadata objects — H's business.
- **Q-3 (B/F):** The F-lite bundle records a semantic world fingerprint; in connected mode, is the *deployed* unit the same bundle, or a richer platform artifact?
- **Q-4 (D):** Does `packages/designer` (browser Designer) stay in this repo (MIT?) while its multi-user backend is Platform-side — i.e., does the *frontend* cross the license boundary?

---

## 10. Session index

| Date | Gear | What happened | Artifacts |
|---|---|---|---|
| 2026-07-08 | Framing + Divergence (B) | Effort opened; FI-1..4 fixed, GI-1..4 recorded; workstreams A–J; LF-1..8; P1–P3 proposed; hero "one program, three lives"; B divergence → mode-seam options | this doc, `01-design-space-map.md`, `02-mode-seam-options.md` |
| 2026-07-08 | Convergence (B core) + dives | **B-1 β · B-2 β · B-3 α · B-5 δ · B-6 γ · BQ-1 archive decided** (LF-1, LF-5 resolved); BQ-2 + BQ-3 dives written (leans: per-object stats keying + inline recording; lock-for-canon / max-age-for-stats); BQ-4 folded into lock; BQ-5 opened | `02-mode-seam-options.md` (resolutions + dives), decision log |
| 2026-07-08 | Convergence (B closed) | **BQ-2 = γ(+δ) and BQ-3 layered (+BQ-4) ratified → 🟢 B CONVERGED.** Seam-legality rule standing (data + diagnostics; never identity/side effects); confirmations owed by C (server contract) and H (advisory policy); BQ-5 → F/D | decision log, dashboard |
| 2026-07-08 | Verification + Divergence (C) | **Q-2 verified in kantheon repo** (whois = OPA bundle server; Argos = validator w/ in-process HOCON policies + LLM Guard tendril; identity at theseus-mcp edge); full service inventory with placement verdicts; C forks C-1..C-6 catalogued (roster philosophy, metadata server, execution spine, Charon, security placement); **C-6 discharges B's IOU (server contract confirmed cheap)**; CQ-1..CQ-4 opened (CQ-3 = P2 runtime-plugin ratification) | `03-service-architecture-options.md`, Q-2 in §9 |
