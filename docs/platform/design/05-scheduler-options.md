# Tatrman Platform — Scheduler & Job Execution Options (workstream F)

> Divergence catalogue for **F — the platform server that schedules and runs jobs**, in workers and via registered orchestrators. This is where **TTR-P F-proper** finally lives. Session opened 2026-07-08.
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) · [`03-service-architecture-options.md`](./03-service-architecture-options.md) (C-3/C-4 riders) · TTR-P [`08-orchestration-options.md`](../../ttr-p/design/08-orchestration-options.md) (F-lite, converged).
>
> **Scope guard:** F designs *scheduling and program execution* — triggers, the program door's executor, run semantics, run state, quotas. The emit-plugin mechanism and the registered-engine registry = **E** (LF-6 spans both; F names the seam and leans lightly). Policy content and identity = **H**. Designer run views = **G**.

## Inherited constraints

- **GI-4 / FI-4:** a platform server to schedule and run jobs — in the execution engines *and* by connecting to existing orchestration engines.
- **C-3-γ:** two doors, one hall. The **program door** is F-proper's home, a new build; the query door is synchronous single-plan. **F owns the two-door scheduling/quota problem** (this doc, F-5). **C-3-δ** (workers-pull reconciler) is recorded as the architecture F may *grow toward*.
- **C-4-β rider:** Charon-as-worker-kind re-examined here (F-8).
- **CQ-2:** run/lineage store ownership — scheduler or metadata server; metadata serves *reads* either way (F-6).
- **BQ-5:** `ttr.lock` scope — per project root or per program/bundle; bites at deploy (F-7).
- **Q-3:** deployed unit = the F-lite bundle or a richer platform artifact (F-2).
- **TTR-P F-lite (converged, 🟢):** the bundle (`run.sh` waves + islands + transfers + JSON manifest + semantic world fingerprint + sha256s); FS + SS only; fail-fast (`F-d`); **no retries/resume (F-e-α)**; **FF dropped from v1 (F-b)** — staging+swap (β) / compensation (γ) explicitly deferred to F-proper, i.e. *here*; cross-container `err` = compile error (F-d-i-α) with on-failure islands (β) sent to F-proper, i.e. *here*; `TTR_CONN_*` env credentials; record-in-artifact / verify-by-capable-invoker (F-f-ii).
- **TTR-P F-proper deferred list (the debt this workstream repays):** events · FF · retries/resume · on-failure islands · runtime parameters (F-4).
- **TTR-P B-T2/B-T6:** control vocabulary FS/SS(/FF), acyclic; execution engines carry **capability manifests** — what an executor cannot do is a *compile error*, not a runtime surprise. **The Platform itself becomes an execution engine with a manifest** ("Kantheon-the-executor" is now "Tatrman-the-executor") — F-4's v1 scope *is* that manifest's content.
- **B (converged):** B-3-α hard parity (compile = pure function of recorded snapshot — anything deploy adds is platform state, not compile input); B-4 seam legality (*data and diagnostics; never identity, never side effects* — identity, secrets, schedules live **outside** the artifact); BQ-3 (`ttr.lock` pins canon; stats float, recorded per compile).
- **P1** standalone is not a demo · **P2** one-way build-time arrow, runtime SPI plugins legal · **P3** no miracles.
- **Hero:** life 2 = same program, deployed, **scheduled nightly**, run on Arges+Steropes, Charon moves, runs/lineage in metadata. Life 3 = emitted as a Dagster DAG, scheduled *there*, lineage flows out.

---

## F-1 · Scheduler shape (LF-6) — who owns triggers, and is delegation the same thing?

**Question:** the map's four branches, sharpened by C-3-γ: the program door already exists as a concept — what does "scheduling" add on top of it, and who provides it?

- **F-1-α · Own full scheduler service.** One platform service owns cron + events + the orchestration semantics (F-4) end to end.
  - *Buys:* self-sufficient — hero life 2 runs with zero third-party software; quotas/priorities (F-5) designed natively for the two doors; every F-4 semantic has one owner.
  - *Costs:* the biggest build in the platform; re-derives mature orchestrator machinery (calendars, catch-up, sensors); the classic half-good-homemade-scheduler trap.
- **F-1-β · Delegate-only.** The Platform never schedules; deploying = handing the program to a **registered orchestrator** (E); the Platform harvests results.
  - *Buys:* thin; mature schedulers do what they're good at; E's registration mechanism does double duty; life 3 becomes the *only* life.
  - *Costs:* life 2 dies — "run the hero nightly on platform workers" *requires* installing Dagster even for worker-only jobs (GI-4 explicitly names running jobs *in the execution engines*); the quota problem (F-5) is not solved, just orphaned — agent bursts still hit the hall with no arbiter; the platform-flavored echo of P1 fails: the Platform should not be a demo that needs a chaperone.
- **F-1-γ · Thin trigger layer + F-proper executor.** Split scheduling from execution semantics: **triggers** (time/event/manual/API — small, boring) fire the **program door**, whose executor owns the semantics (F-3/F-4). External orchestrators are **alternative frontends** calling the same door (life 3's Dagster DAG nodes invoke door runs, or E emits a self-contained DAG — E's fork, not ours).
  - *Buys:* matches C-3-γ (the door already owns execution — scheduling is genuinely the small remainder); native (life 2) and delegated (life 3) scheduling become *the same door contract with different callers*; smallest thing satisfying GI-4's both halves.
  - *Costs:* the door contract must be genuinely public/stable from day one (versioning discipline); some orchestrator concepts (backfill windows, sensors, data-aware scheduling) don't reduce to "a trigger fired" — they leak into F-4-v/FQ-1.
- **F-1-δ · Weird: the metadata server schedules (map's F-δ).** Deployments are metadata objects carrying schedule properties; a reconciler loop inside the metadata server fires door calls when desired-state says so. No scheduler service exists.
  - *Buys:* no new service; schedule state lives beside the objects it describes; naturally pairs with F-3-γ (one reconciler mindset).
  - *Costs:* C-2-γ *just* scoped the metadata server around four consumer classes — "fires execution" is none of them; the catalog becomes runtime-critical for execution (blast radius, on-call surprise); quota arbitration (F-5) has no home at all.

*Interlock:* F-1 picks **who calls the program door**; F-3 picks **what the door does**. Under β, the F-4 semantics migrate into *external* engines' capability manifests — TTR programs get orchestration semantics only where the third party provides them (a T6-honest but thin world).

**RESOLVED 2026-07-08 → F-1 = γ (thin trigger layer + F-proper executor).** Triggers are the easy 10% and worth owning; the executor is where F-proper's real content lives; β survives *inside* γ as "an external frontend is a legal trigger source." Resolves **LF-6's F half** (E's half — what a registered orchestrator *is* — stays E's). Rejected: α (rebuilds mature scheduler machinery); β (kills hero life 2, orphans F-5); δ (contradicts C-2-γ's scoping; no quota home).

## F-2 · The deployed unit (Q-3)

**Question:** F-lite's bundle is the standalone artifact. What does the Platform accept at deploy time?

- **F-2-α · The bundle, verbatim.** Deploy = upload `<program>.bundle/`; the platform runs what standalone runs.
  - *Buys:* one artifact everywhere (a strong P1 resonance: the OSS artifact *is* the platform artifact); `ttrp-conform` coverage transfers; F-f's record/verify split works unchanged.
  - *Costs:* the bundle is **bash-shaped** — `run.sh` is its orchestrator, and the platform doesn't want to run bash, it wants the wave graph natively; no home for anything platform-flavored (schedule, identity, runtime-param bindings, policy refs) — those would have to be side-channel API state with no reviewable record.
- **F-2-β · Bundle + deployment envelope.** The deployed unit = the compiled bundle (unchanged, content-addressed) **wrapped in a platform-side deployment record**: `name@version`, bundle hash, schedule/triggers, runtime-param bindings (F-4-i), connection bindings, policy refs (H), lock/snapshot provenance (F-7). The program door executes from the **manifest's wave graph + islands/plans/transfers**, ignoring `run.sh`; `run.sh` remains the standalone/rescue path.
  - *Buys:* B-discipline made structural — compile stays pure (B-3-α: byte-identical artifact both modes) and everything B-4 banned from the seam (identity, side effects, schedules) lands in the envelope, *which is exactly where it belongs*; Q-3 answered without inventing an artifact format; the envelope is the natural attachment point for F-1's triggers and H's identity.
  - *Costs:* two objects kept coherent (cheap — envelope references bundle by hash); "the door ignores `run.sh`" needs a pinned authority rule: **the manifest's graph is authoritative; `run.sh` is a rendering of it** (drift between them = an emit bug caught by conformance).
- **F-2-γ · Richer platform artifact.** A new deployable format (plans + graph + resource declarations, platform-native); the bundle demotes to the bash emit target.
  - *Buys:* no bash residue; the format can carry platform concepts natively.
  - *Costs:* two artifact grammars with independent evolution = the **drift disease** B-1 rejected γ-two-compilers for; the platform runs something the standalone user can never inspect or reproduce — the parity story dies socially even if bytes match.
- **F-2-δ · Weird: deploy is a pointer.** No artifact upload: deploy = `{repo ref, commit, ttr.lock hash, program qname}`; the platform compiles server-side (same core, B-6-γ) on deploy or on schedule, caching by snapshot hash.
  - *Buys:* B-3-α makes this *sound by construction* — compile is a pure function of the recorded snapshot, so pointer and artifact are interchangeable; GitOps-flavored provenance for free; nothing to upload or store but sources the metadata server already has.
  - *Costs:* the platform needs repo access + toolchain on the run path; "what exactly ran" is a compile record, not a reviewable directory in hand; the emergency path ("run this patched bundle *now*") has no door; compile latency enters the schedule path.

*Interlock:* δ is really **β with the bundle materialized lazily** — the envelope exists either way; the fork inside is only *what the envelope references* (uploaded artifact vs source snapshot). F-7 decides which lock the envelope's provenance cites.

**RESOLVED 2026-07-08 → F-2 = β (deployment envelope wrapping the verbatim bundle), with δ recorded as a later, legal envelope variant** (`source:` instead of `artifact:` — same envelope schema, no design change). α's parity virtue is retained (the bundle inside is verbatim). **Resolves Q-3:** the deployed unit = bundle + envelope; the manifest's graph is authoritative, `run.sh` is a rendering of it. Rejected: α bare bundle (no home for platform state); γ richer artifact (the drift disease B-1 rejected).

**AMENDED 2026-07-09 (review-260708 §3.2, ratified): the δ pointer variant is sound only if the pointer cites the FULL recorded snapshot — `{lock hash + compile record}` (recompile-from-recorded-stats), never the lock alone.** Stats are not in the lock (BQ-3); a lock-only pointer recompiled server-side would float stats and deploy a *plan the author never reviewed* — exactly the reviewability failure B-2-δ was rejected for. Same provenance pair F-7 already established; resolves control-room **Q-7**.

## F-3 · Execution model — how a deployed program actually runs

**Question:** the program door owns F-proper semantics (C-3-γ). What machine walks the waves?

- **F-3-α · Central executor in the program door.** The door service walks the wave graph: per island Argos-validate → Kyklop-dispatch → worker; Charon for transfers; owns the run-state machine.
  - *Buys:* `run.sh`'s logic restated at service level (F-a-β waves, generalized) — the semantics are *already designed and conformance-tested*; one home for F-4; simplest to reason about and debug.
  - *Costs:* a stateful long-running orchestrator needs an HA story — run state must survive restart (pushes run state into a durable store from day one, see F-6); long programs occupy executor attention; scale-out = shard by run.
- **F-3-β · Executor as a work item.** The door only *intakes*: a program run is itself enqueued; an **executor worker kind** picks it up and drives the run through the hall.
  - *Buys:* the door stays stateless; executors scale exactly like workers; a crashed executor's run re-dispatches — retry machinery reused for the orchestrator itself.
  - *Costs:* durable run state is still required (β doesn't remove F-6, it just moves the writer); dispatch-the-dispatcher has real complexity (Kyklop routes islands *and* executors — CQ-4's vocabulary grows); two-hop latency on every run start.
- **F-3-γ · Reconciler / workers-pull (C-3-δ landed).** Runs and islands are rows in a desired-state store; workers and Charon **pull** ready items; the "executor" is a reconciler computing readiness (wave edges satisfied) — no imperative walker exists.
  - *Buys:* F-proper's hardest items (retries, resume) become *queue semantics*, nearly free; resilience by construction; if F grows toward this anyway (C-3-δ's recorded destiny), starting here skips a migration.
  - *Costs:* a paradigm shift from everything being transplanted (Kyklop *pushes* today — the hall would be rebuilt, not moved, contradicting C-1's α-leaves); the sync query door still needs push (two dispatch disciplines in one hall); reconciler observability/debugging is its own art; the heaviest possible v1.
- **F-3-δ · Weird: `run.sh` on a runner.** The bundle executes *literally*: a runner worker executes `run.sh` in a sandbox with `TTR_CONN_*` injected; the platform contributes scheduling, log capture, and a run record.
  - *Buys:* F-lite universalized — near-zero new semantics; the strongest conceivable parity (standalone and platform runs are the *same bytes executing*).
  - *Costs:* **the hall is bypassed** — islands never pass Argos or Kyklop, so validation/RLS/policy see nothing (a policy hole H cannot accept); no F-4 semantics beyond what bash has (none); "the platform is a cron for bash" is not a product. Catalogued to mark the parity endpoint of the spectrum; the hall bypass is disqualifying-shaped.

*Interlock:* γ here + F-1-δ = one reconciler (the compound "weird" architecture, worth naming once: **the platform as a desired-state system**). F-8 activates under γ. F-6 must give α/β their durable run store.

**RESOLVED 2026-07-08 → F-3 = α with run state externalized into the run store from day one** — which makes β a later deployment refactor (move the walker into a worker) rather than a redesign, and leaves γ as the recorded growth direction (C-3-δ's destiny honored, not started). Rejected: γ-now (rebuilds the hall instead of moving it — contradicts C-1's α-leaves); δ (hall bypass = policy hole, disqualifying).

## F-4 · The F-proper semantics package (the TTR-P debt, item by item)

Each item: does it enter platform v1, and by what mechanism? Whatever v1 ships **is the content of Tatrman-the-executor's capability manifest** (B-T6) — scoping here is manifest-writing, and F-lite's bash manifest stays untouched (programs using platform-only vocabulary get an ordinary T6 compile error against bash worlds — the manifest machinery finally does the both-worlds job it was designed for).

**F-4-i · Runtime parameters.**
- *α · None in v1* — programs run as compiled; parameterization = recompile. Cheap, but the hero's nightly wants at least a run date.
- *β · Declared, typed params bound at trigger time.* The program declares params (typed, defaulted-or-required — P3); the envelope/trigger supplies values; the executor injects per island (env/bindings, the `TTR_CONN_*` idiom generalized). Grammar impact routes to TTR-P (a small, already-anticipated surface).
- *γ · Computed bindings* — params derived from metadata/events at fire time. v2-shaped.
- **Lean: β**, minimal scalar types + run-date built-in.

**F-4-ii · Retries.**
- *α · None (F-lite parity).* Any failure = run failed.
- *β · Per-island retry counts, manifest-declared* (B-T6 names retries as an executor capability — the slot exists); transient/permanent error classification; requires island idempotency = per-island staging wipe before each attempt (the F-e-α wipe, narrowed).
- *γ · Backoff + poison/park states.* Operational richness, v1.x.
- **Lean: β.**

**F-4-iii · Resume.**
- *α · None* — rerun = whole program (F-e-α).
- *β · Wave-level resume from run state:* completed islands skip **iff** the envelope's snapshot/world fingerprint is unchanged (P3 kills the stale-staging miracle that made F-e decline resume in bash-land — the platform *has* the state to answer the question bash couldn't).
- *γ · Island-level resume + partial re-materialization.* Finer, bookkeeping-heavy.
- **Lean: β** — resume is precisely what the platform's run store buys over bash.

**F-4-iv · On-failure islands (F-d-i-β lands here).**
- *α · Keep the compile error* (cross-container `err` illegal everywhere).
- *β · On-failure islands in platform worlds:* an `err`-consuming container runs iff its source failed; the run still ends non-success (handled ≠ succeeded). Platform manifest declares error-flow; bash manifest still doesn't.
- *γ · β + `absorbs`* (handler converts failure to success). The semantic riches F-d called them.
- **Lean: β** — the first vocabulary where platform-vs-bash manifests genuinely diverge.

**F-4-v · Events (trigger vocabulary).**
- *α · Cron + manual only.*
- *β · + upstream-run events* (program B on success of program A) — an internal, small event source; chains without a third-party orchestrator.
- *γ · + external events* (data arrival, webhooks, orchestrator sensors) — connector-shaped; pairs with E's registered engines and I's harvest.
- **Lean: β for v1; γ arrives with E.**

**F-4-vi · FF (repaying the B-T2 amendment).** F-b deferred FF to exactly this table: staging+swap (F-b-β) and compensation (F-b-γ) "become real options" with an orchestrator.
- *α · Keep FF unshipped* — vocabulary stays reserved; no manifest declares it.
- *β · Staging+swap, executor-coordinated:* FF endpoints write staging; the executor runs the serial publish/swap step; honest guarantee = "no observer sees one without the other, up to the swap window."
- *γ · Single-transactional-domain FF only* (F-b-α): real atomicity, narrow legality, manifest-checked.
- *δ · Compensation* (F-b-γ): still per-effect-type machinery, still can't compensate the irreversible.
- **Lean: α for platform v1, β recorded as the designed mechanism** — ship FF when a program demands it, not before; the manifest omission keeps it a compile error, honestly.

**RESOLVED 2026-07-08 → F-4 package ratified as the leans, in one line: v1 executor manifest = FS + SS + params(β, typed scalars + run-date) + retries(β, manifest-declared per island) + resume(β, wave-level, snapshot-guarded) + on-failure islands(β, no `absorbs`) + events(cron/manual/upstream); FF stays reserved** (F-b-β staging+swap = the designed mechanism when a program demands it; B-T2's amendment remains in force). Writing this as a concrete manifest artifact = FQ-4.

## F-5 · The two-door quota problem (C-3-γ's rider)

**Question:** nightly program waves and agent query bursts share one Argos → Kyklop → workers hall. Who arbitrates?

- **F-5-α · Nothing in v1.** FIFO at Kyklop; doors unthrottled.
  - *Buys:* zero build. *Costs:* one fat nightly program brownouts every agent (or a burst starves the nightly past its window); C-3 flagged this *explicitly* — letting FIFO decide is decision-by-drift, the exact thing the anti-rush rules exist for.
- **F-5-β · Static worker pools.** Workers labeled `interactive` / `batch`; Kyklop routes by door of origin.
  - *Buys:* trivially predictable; hard isolation. *Costs:* wasteful (batch pool idles all day); pool sizing becomes a standing ops chore; small deployments can't afford two pools.
- **F-5-γ · Priority + admission at dispatch.** One pool; work items carry door-derived priority; **running items always finish** (no preemption of a running SQL statement — that way lies madness); batch items *yield at the dispatch slot* when interactive work queues; optionally a weighted-fair floor so batch never fully starves.
  - *Buys:* one pool, self-balancing; the mechanism is Kyklop routing logic, not new architecture; degenerates to α when idle.
  - *Costs:* long-running batch islands still block (admission control can't shorten a 40-minute island — mitigations like island-size hints are v1.x); fairness tuning is real ops surface.
- **F-5-δ · Weird: identity-priced admission.** Doors spend **quota tokens** issued per principal by the security server (H); scheduling becomes governance (teams buy/receive capacity).
  - *Buys:* multi-tenant-shaped; quota policy lives with the policy engine, where governance belongs. *Costs:* drags H into the dispatch hot path; v2-at-earliest; parked-shaped until H exists.

*Interlock:* β and γ are both *Kyklop configuration*, not architecture — they compose (labels for hard isolation where wanted, priorities within a pool).

**RESOLVED 2026-07-08 → F-5 = γ minimal** (two priorities: interactive > batch; batch yields at dispatch; running work finishes), with β's labels kept as a deployment option (both are Kyklop config). δ → **parking lot**, revisit with H/multi-tenant. Rejected: α (decision-by-drift on an explicitly flagged problem).

**Known hole recorded 2026-07-09 (review §3.8.1): Charon is outside the quota** — transfers are executor→Charon direct, not Kyklop-dispatched, so a fat transfer saturates connections unarbitrated. Accepted for v1; revisit rides with C-4-β's condition (F-3-γ growth) or first observed contention.

## F-6 · Run & lineage store ownership (CQ-2)

**Question:** the executor produces run state (hot, transactional) and run history/lineage (cold, queryable). One store or two, and whose?

- **F-6-α · Metadata server owns runs.** Runs are metadata; the executor writes run/island transitions through the metadata server's API; the lineage organ (C-2-γ) is fed directly.
  - *Buys:* one store, one query surface — Designer/catalog get runs for free; "runs and lineage in metadata" (hero life 2) is literal.
  - *Costs:* the metadata server enters the execution hot path (every island completion = a write RPC to the catalog); availability coupling (catalog down ⇒ runs stall); the server C-2-γ scoped around four consumer classes quietly gains a fifth, write-heavy one.
- **F-6-β · Executor owns the run store; metadata ingests and serves reads.** Run state lives in the execution spine's own store (the same store F-3-α's durable run state needs — one store, two jobs); the executor emits **run-completion/lineage events**; the metadata server ingests them into the lineage graph and serves all catalog/Designer reads (CQ-2's parenthetical, honored).
  - *Buys:* hot path stays inside the spine (failure isolation: catalog down ≠ execution down); the metadata server stays a catalog; the ingest is connector-shaped — the same discipline I's harvest uses (the platform eats its own connector food).
  - *Costs:* two stores; lineage reads lag by ingest latency (seconds — fine for catalog reads, and operational "is it running" queries hit the executor's own API anyway); an event contract to version.
- **F-6-γ · One physical store, two owners** (shared DB, schema-per-service). Catalogued as the trap it usually is: schema coupling without an API, the worst of both.
- **F-6-δ · Weird: runs are TTR documents** — run records written back into a repo as family documents (runs as canon, Designer-viewable via the ordinary path).
  - *Buys:* poetic; zero new stores. *Costs:* wrong layer — runs are high-frequency operational data, repos are reviewed canon (F-f-i already fought this: the machine record wants a machine store); a nightly hero = 365 commits/year of noise. Rejected-shaped.

*Interlock:* under F-3-γ the run store *is* the desired-state store — even more emphatically executor-owned; under F-6-β, F-3-α's HA story and CQ-2 are solved by the same table.

**RESOLVED 2026-07-08 → F-6 = β**, read contract pinned: executor owns writes + operational queries ("what is running now"); metadata server ingests run-completion/lineage events and owns catalog/lineage reads ("what ran, touching what"). The event contract is a platform-internal proto (D-3: service-internal ⇒ platform-owned). **Resolves CQ-2.** Rejected: α (catalog on the execution hot path); γ (schema coupling without an API); δ (runs are operational data, not canon).

**Rider added 2026-07-09 (column-lineage v1, C-2 amendment):** the run-completion/lineage event contract must carry (or reference) **column-level** lineage, not just object grain. If CQ-5 resolves compiler-side (the lean), the event cites the manifest's lineage section rather than re-deriving — the event stays thin.

## F-7 · `ttr.lock` scope (BQ-5)

**Question:** one lock per project root, or per program/bundle? Deploy makes it concrete: the envelope must cite provenance.

- **F-7-α · One lock per project root.** Whole-project canon; every program in the repo compiles against the same pinned world.
  - *Buys:* one fetch/diff discipline; BQ-3's team-wide guarantee ("same commit ⇒ same canon") stays simple; matches the cargo/npm-lockfile intuition users bring.
  - *Costs:* coarse invalidation — one world touch churns the lock for forty co-resident programs (though: deploy pins by *hash*, so already-deployed envelopes are untouched; the churn is diff noise, not redeploys).
- **F-7-β · Per-program locks.** Finest granularity; each program pins only what it uses.
  - *Buys:* deploy provenance is exact by construction; no cross-program diff noise.
  - *Costs:* N lockfiles per repo; **cross-program canon skew inside one commit** (two programs in the same repo, same commit, compiled against different worlds — a P3-flavored smell BQ-3 fought); fetch orchestration multiplies.
- **F-7-γ · Layered: project lock + per-program compile record.** The lock stays project-scoped (α's discipline); the **compile record already lists exactly which objects/stats each program's compile consumed** (B-3-α / BQ-2-δ); the deployment envelope cites `{lock hash, compile record}` — a program-scoped provenance *slice* with no new file kind.
  - *Buys:* α's simplicity + β's granularity precisely where deploy needs it; nothing new is invented — BQ-5 *dissolves* into machinery B already ratified.
  - *Costs:* the compile record becomes a load-bearing provenance document (it was already reproducibility-load-bearing per B-3-α — this adds a consumer, not a burden).

**RESOLVED 2026-07-08 → F-7 = γ** — really "α plus what we already have": project-root `ttr.lock` stays the only lock; the deployment envelope cites `{lock hash, compile record}` as program-scoped provenance. **BQ-5 dissolves** (no new file kind; consistent with BQ-3's team-wide guarantee and D-3's ownership rule — the D-joint flag is satisfied by construction, nothing D decided is touched). Rejected: β per-program locks (cross-program canon skew inside one commit).

## F-8 · Charon re-exam (C-4-β's rider)

Not an independent fork — a conditional:

- Under **F-3-α/β** (imperative executor): the executor calls Charon per transfer edge; **C-4-α holds unchanged**, no new information.
- Under **F-3-γ** (reconciler/pull): transfers become pulled work items and **Charon-as-worker-kind becomes the natural shape** (movement capability advertised, dispatched like islands).

**RESOLVED 2026-07-08 → F-8 rider closed conditionally:** with F-3 = α ratified, **C-4-α persists unchanged**; C-4-β's revisit condition is re-pinned precisely = *the platform grows toward F-3-γ (reconciler/pull)*. No decision was needed, and none was made.

---

## Hero rendering ("one program, three lives")

- **Life 1 (standalone):** untouched by everything above — F-lite bundle, `run.sh`, by hand. F changes nothing MIT-side (P1 holds).
- **Life 2 (platform, under the leans):** `ttr deploy` wraps the compiled bundle in an **envelope** (name@version, nightly cron trigger, param binding `run_date`, connection bindings, `{lock hash, compile record}` provenance) → the **trigger service** fires the **program door** → the **executor** walks waves, per island Argos → Kyklop (batch priority, yields to agent queries) → Arges/Steropes; Charon moves the staging edges; a failed island retries per manifest, then the run parks resumable; run events flow to the **executor's run store** and are ingested into the **metadata server's lineage graph**, where the Designer reads them.
- **Life 3 (federated):** Dagster is a registered engine (E); the program is emitted as a Dagster DAG whose nodes call the program door (or E's emit target produces a self-contained DAG — E's fork); Dagster is a **frontend** in F-1-γ's sense; lineage still flows through F-6-β's event path and out via C-2-γ's export connectors.

## Cross-links out

- **→ E:** LF-6's other half — registered orchestrators as trigger frontends vs emit targets; whether "registered engine" is one registry concept (E's open hunch); F-4-v-γ external events ride E's connectors.
- **→ H:** the envelope carries policy refs and *whose identity a scheduled run executes under* (the deploy-time principal question — flagged for H, F only reserves the envelope field); F-5-δ quota governance; whois-descendant.
- **→ G:** Designer run/lineage views read the metadata server (F-6-β's read contract), never the executor directly.
- **→ D:** strangler step ⑦ is this workstream's build; F-7-γ needs D's nod (BQ-5 was joint).
- **→ B:** the envelope is B-4 made structural — everything the seam bans is exactly what the envelope carries; F-2-δ is legal only because of B-3-α.
- **→ TTR-P:** F-4-i params and F-4-iv error-flow have (small) grammar/manifest surfaces — route as TTR-P amendments when converged, same discipline as the `.ttrl` amendment.

## Open questions (F-local) — dispositions 2026-07-08

- ~~**FQ-1 · Backfill semantics**~~ **OUT OF V1 (Bora's call)** → parking lot; revisit on first real demand for schedule catch-up or windowed reruns (likely with F-4-v-γ external events / E).
- **FQ-2 · Run identity scheme** — **work item, not a fork**; settles when F-6-β's run-store schema is written (planning/implementation). Candidate shape recorded: run id = `{envelope name@version, trigger id, fire-time}`; island attempt ids under it (retries make attempts first-class).
- **FQ-3 · Trigger service topology** — **explicitly a non-decision**: the design content is the *contract* split (F-1-γ); process packaging (in the door binary or beside it) is an implementation/deployment choice. Naming → J.
- **FQ-4 · Write "Tatrman-the-executor"'s capability manifest** — **work item, part of `design.md`'s deliverables**: F-4's resolution *is* its content; B-T6 requires the artifact to exist for compiles against platform worlds to be checkable.
- **FQ-5 · Scheduled-run principal** — **routed to H** (joins C-5-ii/iii and the advisory-compile-policy IOU); F's contribution = the envelope reserves a principal field, nothing more.
- ~~**FQ-6 · Staging lifecycle vs resume**~~ **RESOLVED 2026-07-09 → TWO STAGING SCOPES:** *attempt-scoped* staging is wiped on every retry (F-4-ii unchanged); *run-scoped* staging holds completed islands' outputs and is **retained while the run is parked-resumable** under a retention policy (envelope-declared; platform default, e.g. N days); at expiry the run **degrades from "resumable" to "restart-only"** — P3-explicit, the refusal names the reason; Charon Evict executes cleanup on success, abandonment, or expiry. F-4-iii's snapshot-guard unchanged — this supplies the data it resumes *with*.
- ~~**FQ-7 · Door compiler-version policy**~~ **RESOLVED 2026-07-09 → DISSOLVES: the door executes MANIFESTS, not compilers.** Compatibility keys on the **E-5 manifest schema version** (the documented-stable contract): the door accepts any bundle whose schema version it supports (current + N-1 minimum; exact window = planning); deployed envelopes are untouched by toolchain upgrades (pinned by hash); server-side compiles use the platform's pinned toolchain, recorded in the compile record.

## Convergence status

**🟢 F RE-CONVERGED 2026-07-09 (de-dirty pass, Bora):** F-2-δ provenance amended (Q-7) · FQ-6 = two staging scopes with retention + degrade-to-restart · FQ-7 dissolved (compatibility keys on the E-5 manifest schema version) · F-6's lineage rider settled (events cite the manifest's compiler-derived lineage section, per CQ-5). The F-5 Charon hole stays recorded-accepted (revisit condition pinned).

**Original convergence (2026-07-08)** — F-1 γ thin-triggers + door executor (LF-6's F half) · F-2 β envelope-wrapping-verbatim-bundle (Q-3 resolved; δ a later envelope variant) · F-3 α executor with externalized run state (γ = recorded growth direction) · F-4 package (params/retries/resume/on-failure/events in; **FF stays reserved**) · F-5 γ-minimal priorities (δ parked → H/multi-tenant) · F-6 β executor-writes/metadata-reads (CQ-2 resolved) · F-7 γ lock+compile-record (BQ-5 dissolved) · F-8 conditional close (C-4-α persists). FQ dispositions above; FQ-1 out of v1. **Riders out:** FQ-5 + F-5-δ → H; FQ-2/FQ-4 → planning-stage work items; E owes the registered-orchestrator-as-frontend contract pressure-test (LF-6's other half).
