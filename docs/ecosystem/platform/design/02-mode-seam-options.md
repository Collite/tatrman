# Tatrman Platform — Mode-Seam Options (workstream B)

> Divergence catalogue for **B — the compiler/optimizer two-mode contract**: what "connected" changes, through what interface, with what guarantees. Session opened 2026-07-08.
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md).
>
> **Scope guard:** B designs the *seam*, not the services behind it (C) and not the deployment pipeline (F). Wherever an option needs a platform capability, it names the capability abstractly and cross-links.

## Inherited constraints

- **TTR-P T6:** the world is a compile target; the runtime *verifies* compatibility; runtime capability advertisement is never the compile-time source of truth.
- **TTR-P D-g:** the compiler embeds the metadata component; repo + world read directly via paths; offline; Kantheon workspace metadata = population source for worlds.
- **MD arc:** `ttr-metadata` already has the `ModelSource`/`ModelStorage` SPI (LocalFs / Classpath / GitArchive) and snapshot semantics (`SourceSnapshot`, `fetchVersion()`).
- **TTR-P F-f-ii:** artifacts record a *semantic* world fingerprint; the capable invoker verifies.
- **P1:** standalone is not a demo. **P2:** one-way arrow. **P3:** no miracles — no invisible compile inputs.
- **TTR-P Z (optimizer):** grounded with static cost model in v1; statistics-driven optimization is the roadmap (Z 2.x+).

---

## B-1 · The seam object — what *is* "connected mode"?

**Question:** through what interface does connectedness enter the compiler?

- **B-1-α · Mode flag.** The compiler grows `--connected <url>` and branches internally (resolution, stats, registries each check the flag).
  - *Buys:* nothing is redesigned; quick.
  - *Costs:* mode-awareness smears across every compiler phase; each new connected capability adds another branch; testing doubles; P1 erodes by a thousand `if (connected)`s. The bolt-on FI-1 explicitly rejects.
- **B-1-β · Source SPI seam — the compiler is mode-blind.** The compiler *always* compiles against `(models, worlds, manifests, statistics)` obtained through the existing source SPI. Standalone binds `LocalFsStorage`; connected binds a `MetadataServerSource` (new SPI impl, served by the Platform). Compilation itself has no notion of mode; "connected" is a *binding*, chosen in project config (`modeler.toml` / `[ttrp]` defaults).
  - *Buys:* one compiler, one semantics, one test surface; the SPI already exists (MD arc) and snapshot semantics come with it; P1/P3 by construction; T6/D-g extend rather than amend — connected changes *where the world comes from*, not what compiling means.
  - *Costs:* everything connectedness adds must be expressible as *data through the SPI* (statistics, registries, secrets-by-reference) — pressure to widen the SPI carefully (see B-4); truly interactive features (compile-time policy dialogue) don't fit.
  - *Prior art:* Bazel remote caches; Gradle dependency resolution (repositories are sources, the build is source-blind).
- **B-1-γ · Two compilers.** OSS compiler stays as-is; the Platform runs a *compile service* that wraps it and adds connected phases (stats-driven optimization, policy checks, deploy packaging) server-side.
  - *Buys:* OSS core untouched, maximal platform freedom; heavyweight connected features (multi-tenant caching, fleet-wide optimization) have a natural home.
  - *Costs:* two compile behaviors to explain ("why does the platform build differ?"); drift risk = the exact disease `ttrp-conform` exists to fight; the *language experience* forks — P1's spirit bruised even if letter kept.
- **B-1-δ · Weird: connected = sync, not mode.** No live seam at all: a `ttr sync` tool materializes the Platform's metadata (worlds, stats, registries) into repo files; the compiler only ever reads files. Connectedness is a *fetch step* with a lockfile.
  - *Buys:* radical simplicity; hermetic + reviewable compile inputs (the lockfile is P3 heaven); offline-first forever; git diff shows you what the platform changed.
  - *Costs:* staleness by construction; "live-ish statistics" become "statistics as of last sync"; Designer-on-Platform and server-side compile (B-6) still need a live path — so δ alone can't carry the Platform, it becomes a *complement*.

**Lean: β, with δ's lockfile discipline folded in** (a connected compile *records* the resolved snapshot — B-3), because it makes FI-1 literal: two bindings of one first-class contract, not two behaviors. γ resurfaces *on top of* β if the Platform later wants a compile service (B-6-γ) — they compose.

**RESOLVED 2026-07-08 → B-1 = β (source SPI seam; the compiler is mode-blind; "connected" is a binding).** Rejected: α mode flag (mode-awareness smears across phases); γ two compilers (drift; composes later via B-6-γ instead); δ-alone (can't carry live Designer/platform compile — its snapshot discipline survives inside B-5-δ).

## B-2 · Statistics & the optimizer

**Question:** connected mode's headline gift to the optimizer is live statistics (cardinalities, sizes, engine load). How do they enter?

- **B-2-α · Statistics are world-doc content.** Stats live in (or beside) the world: instance manifests carry `statistics {}` blocks (row counts, sizes, freshness stamp). Standalone: authored/synced values, possibly stale. Connected: the metadata server serves the same blocks, fresh.
  - *Buys:* one representation; the optimizer reads *the world*, never a service (T6 purism); standalone gets stats-driven optimization too (with honest staleness); trivially recordable in the compile snapshot.
  - *Costs:* worlds get fat; churn (stats change hourly, worlds shouldn't); a freshness-vs-canon tension inside a document kind designed to be stable.
- **B-2-β · Statistics are a separate source kind.** A `StatisticsSource` beside models/worlds in the SPI: standalone binds a stats file (or nothing → static cost model), connected binds the server. Worlds stay lean and stable; stats are an overlay keyed by object qname.
  - *Buys:* separation of stable canon (world) from volatile observation (stats); the optimizer declares its input explicitly; absent-stats degradation is a defined state (static cost model, TTR-P Z v1) not an error.
  - *Costs:* a second overlay concept (worlds already overlay type manifests); keying/versioning discipline needed so a snapshot pins *both*.
- **B-2-γ · The optimizer asks live.** Connected optimization may *query* the server mid-optimization (cardinality of this predicate? sample this join?). Standalone: those probes are simply unavailable, optimizer uses static costs.
  - *Buys:* the strongest optimizations (selectivity probing) become possible; this is what real cost-based optimizers with live catalogs do.
  - *Costs:* compile output depends on server state at compile *instant* — unpinnable, unreproducible (P3 alarm); optimizer phases become async/network-aware; the seam stops being data and becomes conversation.
- **B-2-δ · Weird: the Platform optimizes *after* the compiler.** Standalone compiler always emits the naive-but-correct plan; the Platform re-optimizes deployed artifacts continuously (re-plan on stats drift, like a DBMS re-planning queries).
  - *Buys:* standalone stays simple; optimization becomes an *operational* capability (fits the "compile vs operate" A-α rule); re-optimization without recompile is genuinely novel value.
  - *Costs:* two optimizers (drift); the deployed artifact is no longer what the author reviewed — the reviewable-text instinct (E-a/F-f) objects loudly; blurs B into F.

**Lean: β, with α's freshness stamp** (stats overlay as its own source kind, snapshot-pinnable; static cost model as the defined zero-stats degradation). γ recorded as a Z 3.x possibility *behind* a snapshot/replay discipline; δ's "re-optimize on drift" parked to F as an operational feature that *re-runs the compiler*, never a second optimizer.

**RESOLVED 2026-07-08 → B-2 = β (statistics = separate snapshot-pinnable source kind; static cost model = defined zero-stats degradation).** Keying/versioning discipline → BQ-2 dive below. Rejected: α stats-in-world (churn corrupts a stable document kind); γ live probing (unpinnable — parked behind snapshot/replay, Z 3.x); δ platform re-optimizer (two optimizers; artifact ≠ what the author reviewed).

## B-3 · Parity & reproducibility

**Question:** what does the design *guarantee* about standalone vs connected compiles of the same program?

- **B-3-α · Hard parity (hermetic).** Same program + same resolved inputs ⇒ **byte-identical artifacts**, regardless of mode. Enforced by making every input part of the recorded snapshot (models, world, manifests, stats, plugin versions) — the connected compile emits its snapshot; replaying it standalone reproduces the artifact. `ttrp-conform` gains a mode-drift suite.
  - *Buys:* "first-class two modes" gets teeth; debugging = replay; trust story for the OSS community (the Platform can't make your program mean something else); F-f-ii fingerprint generalizes into a full input pin.
  - *Costs:* discipline everywhere (no timestamps, stable orderings, pinned plugin versions); B-2-γ-style live probing is banned or must record its answers.
- **B-3-β · Parity modulo statistics.** Semantics identical; *plan choice* may differ with stats. Artifacts carry the stats snapshot used, so a differing plan is explainable, but not byte-reproducible without it.
  - *Buys:* honest about what stats-driven optimization means; cheaper discipline.
  - *Costs:* "explainable" < "reproducible"; conformance can only compare *results* (Q9 procedure), not artifacts.
- **B-3-γ · No guarantee.** Connected mode is allowed to be better/different; document it.
  - *Buys:* freedom. *Costs:* the two modes become two products in practice; P1 dies by drift. (Catalogued for completeness.)

**Lean: α, stated as: *a compile is a pure function of its recorded snapshot; mode only chooses where the snapshot comes from.*** β is what α degrades to if snapshot-pinning stats proves too heavy — but B-2-β makes pinning cheap, so start at α.

**RESOLVED 2026-07-08 → B-3 = α (hard parity, hermetic: a compile is a pure function of its recorded snapshot; mode only chooses where the snapshot comes from).** `ttrp-conform` gains a mode-drift suite. Rejected: β parity-modulo-stats (falls out only if pinning proves too heavy — it doesn't under B-2-β); γ no guarantee (two products by drift).

## B-4 · What connected mode may ADD (beyond sources)

**Question:** candidates for connected-only compile-time capabilities — which are seam-legal?

Catalogue (each: seam-legal under β/α-parity? or platform-phase instead?):

1. **Registry content** — engines, orchestrators, emit plugins registered on the Platform (E). *Seam-legal:* registries are data (manifests) through the SPI; standalone equivalent = files in repo. ✔
2. **Named-connection resolution** — standalone: `TTR_CONN_*` env at *run* time (F-c-ii); connected: the Platform resolves connections at *deploy* time, secrets never in artifacts. *Seam-shape:* connections stay **by-reference** in artifacts in both modes; only the resolver differs. ✔ (resolution is run/deploy-phase, not compile-phase — no seam widening)
3. **Compile-time policy checks** (H) — "you may not read `hr.salaries`". *Tension:* a *blocking* policy check makes compile output depend on identity — breaks B-3-α purity. Options: advisory-only at compile (warn), enforcement at deploy/run (the T6 declare/verify split applied to policy). Lean: advisory compile / enforced deploy. ⚠
4. **Deploy packaging** — compile→deploy in one step, run identity minted, bundle registered in metadata. *Platform phase after compile*, not a compile capability. ✘ (F's business)
5. **Fleet knowledge for optimization** — cross-program materialization reuse ("another program already stages this join"). Genuinely connected-only; representable as stats-overlay content (B-2-β) → snapshot-pinnable. ✔ (Z 3.x)
6. **Live schema drift warnings** — "the DB no longer matches this world". This is *verification*, T6 says it's runtime/deploy-side; connected compile may surface it as a *diagnostic overlay*, never as changed compile semantics. ✔ as diagnostics.

**Lean:** the seam admits **data (sources, registries, overlays) and diagnostics; never identity, never side effects.** Everything identity-bound or effectful (policy enforcement, deploy, secrets) happens in platform phases *after* compile — which keeps B-3-α intact.

## B-5 · Degradation & failure

**Question:** connected compile, server unreachable/degraded — what happens?

- **B-5-α · Fail.** No server, no compile (in connected-configured projects). Simple; brutal for laptops on planes.
- **B-5-β · Degrade to cache.** Last-fetched snapshot (lockfile — B-1-δ's gift) with a **staleness stamp**; compile succeeds, artifact records "compiled against snapshot of T". P3-clean because staleness is explicit and recorded.
- **B-5-γ · Degrade to standalone.** Fall back to repo files silently. *Costs:* silent semantic difference — P3 violation. Catalogued to reject loudly.
- **B-5-δ · Weird: no degradation exists because connected compile is always snapshot-first.** The compiler *never* talks to the server mid-compile; a fetch step (explicit or auto) refreshes the local snapshot, then every compile is local. Unreachable server = stale snapshot, nothing else. (B-1-β + B-1-δ hybrid taken to its conclusion.)

**Lean: δ, which makes β automatic** — fetch-then-compile as the *only* connected shape: the compiler is always offline at heart (D-g preserved verbatim); "connected" = a managed, fresh, verified snapshot. Failure semantics collapse into freshness policy (max-age knobs, `--frozen` for CI).

**RESOLVED 2026-07-08 → B-5 = δ (fetch-then-compile is the only connected shape; the compiler never talks to the server mid-compile; unreachable server = stale snapshot, nothing else; failure semantics = freshness policy).** Freshness policy/UX → BQ-3 dive below. Rejected: α fail-hard (planes); β-as-primary (subsumed — δ makes it automatic); γ silent fallback to repo files (P3 violation, rejected loudly).

## B-6 · Where does connected compilation run?

**Question:** client-side, platform-side, or both?

- **B-6-α · Client-only.** CLI/IDE compiles locally against the (snapshot of the) metadata server. Designer writes still go text→commit (G-γ); the Platform runs the *compiler-as-library* only for validation of deploys.
- **B-6-β · Platform-only for connected features.** Rich features (stats optimization, deploy) require server-side compile; local connected compile doesn't exist.
- **B-6-γ · Both, one core.** The compiler is a library (it already is — JVM, embeds ttr-metadata); the CLI binds it locally, the Platform's compile/deploy service binds the same version server-side. B-3-α parity makes location irrelevant by construction.

**Lean: γ** — trivially available once B-1-β + B-3-α hold; the *same snapshot ⇒ same artifact* rule makes "where" a deployment convenience, not a semantic question. (This also quietly resolves LF-5.)

**RESOLVED 2026-07-08 → B-6 = γ (one compiler core, bound locally by CLI/IDE and server-side by the Platform's compile/deploy service; B-3-α parity makes location a deployment convenience).** Resolves LF-5. Rejected: α client-only (Platform deploy wants server-side validation/compile anyway); β platform-only (breaks local connected development, P1 spirit).

---

## Cross-links out

- **→ C:** the metadata server must *serve the SPI* (snapshot endpoints, world resolution, stats overlay, registries) — that's its compiler-facing contract.
- **→ E:** registered orchestrators surface to the compiler as manifests through the seam (B-4 item 1).
- **→ F:** deploy pipeline = compile (pure) + platform phases (identity, secrets, registration, schedule) — B-4's "never identity, never side effects" line drawn here.
- **→ H:** policy = advisory at compile, enforced at deploy/run (B-4 item 3) — H must confirm.
- **→ A:** if the lean set holds, the *entire compiler incl. optimizer* is MIT-side (A-α compatible); the Platform's edge is data + operations, not algorithms.

## BQ-2 dive · Statistics keying & versioning (2026-07-08)

**Question:** under B-2-β + B-3-α, how are stats entries keyed, and what makes a stats snapshot *valid* against a given world?

First, a constraint worth stating: the **F-f-ii world fingerprint must stay stats-free.** It is *semantic* ("comment reflow doesn't change the world") and the T6 runtime-verification key; row counts drifting hourly must not read as "the world changed."

- **BQ2-α · Stats versioned WITH the world.** One key: any stats refresh bumps the world fingerprint.
  - *Buys:* single version to record.
  - *Costs:* fingerprint churns hourly; T6 runtime verification fails spuriously; violates the constraint above. Catalogued to reject.
- **BQ2-β · Independent snapshot versions, joined at compile.** The compile snapshot records a tuple `(models@M, world@W, stats@S, plugins@P)`; stats snapshots declare which W they were observed under; mismatch rules needed.
  - *Buys:* world stays stable; stats refresh freely.
  - *Costs:* whole-snapshot validity is too coarse — adding one engine instance to the world would invalidate *all* stats, including row counts of untouched tables.
- **BQ2-γ · Per-object keying.** A stats entry = `{object qname, object-schema-hash, values, observed-at}`. Validity is *per object*: the entry binds to the object's own declared shape (column set/types hash), not to the whole world. An entry whose schema-hash no longer matches ⇒ discarded *for that object only* → static cost model for it (B-2-β's defined degradation), rest of the program keeps its stats.
  - *Buys:* world edits invalidate only what they touch; degradation is granular and P3-explicit (a diagnostic names the stale objects); no global version dance.
  - *Costs:* needs a canonical per-object schema-hash definition (but world resolution already computes resolved object shapes — MD arc); slightly bigger entries.
- **BQ2-δ · Weird: no versions — values ARE the record.** Stats are unversioned observations; the compiler embeds the literal values it used into the compile snapshot/artifact manifest. Reproducibility by content, not by reference.
  - *Buys:* replay is trivial (values inline, they're tiny); the artifact *shows the numbers the optimizer saw* — reviewable-text instinct extended to optimization; no registry of stats snapshots to garbage-collect.
  - *Costs:* no shared cache key ("which stats state was this?" answered only per-artifact); server-side dedup impossible.

**Lean: γ + δ — per-object keyed entries (`qname + object-schema-hash + observed-at`), with the used values embedded verbatim in the compile snapshot.** γ gives granular validity, δ gives free replay and reviewability; they compose because entries are small. The world fingerprint stays untouched by stats forever. Freshness (how old may `observed-at` be) is policy, not validity → BQ-3.

**RESOLVED 2026-07-08 → BQ-2 = γ (per-object keying: `qname + object-schema-hash + observed-at`), with δ's inline recording composed** (used values embedded in the compile record — what B-3-α replay reads). World fingerprint stays stats-free forever. Rejected: α (fingerprint churn, spurious T6 failures); β whole-snapshot validity (too coarse — one world edit invalidates untouched objects' stats).

## BQ-3 dive · Fetch UX & freshness policy (2026-07-08)

**Question:** under B-5-δ (fetch-then-compile), who refreshes the snapshot, when, and what do CI and teams get?

The insight that reframes the fork: the snapshot has **two temperaments of content** — *canon* (models, worlds, manifests, plugin set: stable, review-worthy) and *observation* (stats: volatile, advisory). One freshness policy for both is wrong on arrival.

- **BQ3-α · Explicit-only.** `ttr fetch` like `git fetch`; compile never triggers network.
  - *Buys:* total predictability. *Costs:* stale-by-default; IDE diagnostics against old worlds confuse ("the table exists, I just added it!").
- **BQ3-β · Auto-fetch with max-age.** Pre-compile hook: snapshot older than max-age (project-config, e.g. 15 min) → refresh; unreachable → compile proceeds, staleness warned and recorded. Package-manager prior art (cargo/apt metadata).
  - *Buys:* fresh by default, still snapshot-first. *Costs:* compile latency spikes; team members compile against *different* snapshots at the same commit.
- **BQ3-γ · IDE push subscription.** LSP holds a live subscription; the server pushes snapshot deltas; compiles always fresh at zero latency. CLI falls back to β.
  - *Buys:* best interactive UX; live drift diagnostics in the Designer. *Costs:* push infra lands in C's server contract; two mechanisms day one.
- **BQ3-δ · Weird: the snapshot is COMMITTED — a lockfile.** `ttr.lock` in the repo pins content hashes; fetch = update the lock (a reviewable diff: "the platform's view of the world changed"); every compile — dev, CI, platform-side — resolves the lock against a content-addressed local cache (BQ-1's archive, fetched by hash). cargo.lock + registry-cache, exactly.
  - *Buys:* team-wide reproducibility for free (same commit ⇒ same snapshot ⇒ same artifact, B-3-α at team scope); CI is frozen by construction; world changes get *code review*.
  - *Costs:* stats in the lock = hourly diff noise (unacceptable); lock conflicts; freshness now requires a human to commit.

**Lean: layered, splitting by temperament — δ for canon, β for observation:**

1. **`ttr.lock` (committed) pins canon:** models, world, manifests, **emit-plugin versions (BQ-4 answered: yes, in the lock)** — content hashes into the BQ-1 archive cache. World changes become reviewable diffs.
2. **Stats float** under a max-age auto-refresh (β), never enter the lock, and the *used values* are recorded into the compile snapshot/artifact (BQ-2 δ-inline). Reproducibility: canon by lock, stats by artifact record — B-3-α holds because the recorded snapshot = lock-resolved canon + recorded stats.
3. **Flags:** `--frozen` (CI default: no network, fail if cache incomplete or stats absent-but-required) · `--offline` (no network, use cache, warn + record staleness).
4. **IDE:** background β-refresh + an *advisory* "live drift" diagnostic channel (world in lock ≠ server's current world). γ's push subscription = a later C capability, not v1-required.

**RESOLVED 2026-07-08 → BQ-3 = the layered policy as tabled:** (1) **`ttr.lock` committed, pins canon** (models/world/manifests/**emit-plugin versions — BQ-4 resolved with it**) by content hash into the BQ-1 archive cache; fetch = reviewable lock diff; (2) **stats float** under max-age auto-refresh, never in the lock, used values recorded per-compile (BQ-2); (3) `--frozen` (CI default) / `--offline`; (4) IDE background refresh + advisory drift diagnostics; push channel = later C capability. Rejected: α explicit-only (stale-by-default); one-policy-for-everything (canon and observation have different temperaments); stats-in-lock (hourly diff noise).

**Cross-link consequence for C:** the metadata server's compiler-facing contract = content-addressed snapshot archives (BQ-1) + a stats endpoint (per-object entries) + optionally a delta-push channel (later).

## Open questions (B)

- ~~**BQ-1:** Snapshot format & transport — repo-shaped file tree or dedicated archive/protocol?~~ **RESOLVED 2026-07-08 → archive/protocol** (content-addressed snapshot archives served by the metadata server; local cache by hash; `LocalFsStorage` stays the plain-repo binding). Rejected: repo-shaped tree (slower, sync-flavored; the lockfile discipline survives without it).
- ~~**BQ-2:** stats keying/versioning~~ → **RESOLVED 2026-07-08 = γ (+ δ inline recording), see dive.**
- ~~**BQ-3:** fetch UX~~ → **RESOLVED 2026-07-08 = layered policy (lock-for-canon / max-age-for-stats), see dive.**
- ~~**BQ-4:** plugin pinning~~ → **RESOLVED 2026-07-08 with BQ-3: emit-plugin versions are canon, pinned in `ttr.lock`, part of the recorded snapshot.**
- ~~**BQ-5:** `ttr.lock` scope — one lock per project root, or per program/bundle? Settles when multi-program repos meet deploy (→ F, D).~~ **RESOLVED 2026-07-08 with F-7 = γ: dissolved — the lock stays per project root; the deployment envelope cites `{lock hash, compile record}` as the program-scoped provenance slice (no new file kind; BQ-3's guarantee intact).** See `05-scheduler-options.md`.

## Convergence status

**🟢 B IS CONVERGED (2026-07-08)** — B-1 β · B-2 β · B-3 α · B-5 δ · B-6 γ · BQ-1 archive · BQ-2 γ(+δ) · BQ-3 layered · BQ-4 in-lock. Two confirmations owed by other workstreams (tracked in the control room): **C** confirms the compiler-facing server contract (snapshot archives + per-object stats endpoint) is buildable-cheap — **granted 2026-07-08 (C-6)**; **H** confirms compile-time policy = advisory-only (B-4's "data + diagnostics; never identity, never side effects" line) — **granted 2026-07-09 (H-3 = α)**. **B's confirmation ledger is clear.** BQ-5 rode with F/D — resolved (F-7). B-4 remains the standing seam-legality rule for every future connected feature.
