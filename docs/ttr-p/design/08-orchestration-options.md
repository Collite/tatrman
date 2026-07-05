# PL Design — F-lite Options (workstream F, v1 scope)

> **Naming note (consolidation sweep, 2026-07-04):** this doc predates the H rename + S-sweep. Read `PL_CONN_*` as `TTR_CONN_*`, `pl-conform` as `ttrp-conform` (S3), `[pl]` as `[ttrp]` (S5); the bundle dir `<program>.bundle/` is ratified (S1). Standing contracts live in `../architecture/contracts.md`.

> Divergence catalogue for **F-lite** — the last v1-blocking workstream. Session opened 2026-07-03.
> Companions: [Control Room](./00-control-room.md) · E → [`07-emit-options.md`](./07-emit-options.md) (E-h lands here).
>
> **Scope guard:** F *proper* (the Kantheon multi-step orchestrator — new service vs extend Theseus vs Pythia's DAG executor, events, runtime parameters) is **post-v1**. F-lite is only what v1 artifacts need: the **bash executor** emit + the **artifact bundle**. The orchestrator graph is *derived* (container-collapse, B-T6); this session decides how that graph becomes something runnable.

## Inherited constraints

- **B-T2**: control vocabulary = FS/SS/FF, hard-on-effect, acyclic; parallel-ok = absence of FS.
- **B-T6**: execution engines carry capability manifests (which control vocabulary, parallelism, retries, transactionality they support); **v1 execution engine = bash**; invocation bindings choose per-container delivery.
- **E-a**: artifacts carry concrete payloads (dialect SQL / Polars scripts); PlanNode emission is world-driven (Kantheon path ignores F-lite's script emit).
- **E-g**: Transfer is abstract; bash-land bindings = native tools (`psql \copy`, file copy).
- **E-h**: bundle = directory of reviewable text (`run.sh`, `islands/`, `transfers/`, manifest) — sketch to finalize here (§F-f).
- **P2**: no miracles — every executor behavior explicit or deterministic from manifests/defaults.
- v1 lean from A-scope: **fail-fast**, no streaming, no incremental.

---

## F-a · Script structure — how FS/SS/FF become bash

**Question:** what is the emitted `run.sh`'s execution model? Note the framing bonus: whatever we pick **is the content of bash's executor-type manifest** — a program whose control edges exceed it fails at compile time (P2), not at runtime.

- **F-a-α · Strictly sequential topological order.** `run.sh` = a linear list of island invocations in a deterministic topo order; FS = ordering; SS/FF unsupported (manifest omits them).
  - *Buys:* trivially reviewable artifact; failure attribution is exact; log = the script, top to bottom.
  - *Costs:* **SS becomes dead vocabulary in v1** (bash is the only executor — no program could ever use SS); no parallelism even where the hero's two prep islands are independent.
- **F-a-β · Wave (level) parallelism.** Topological levels; islands within a wave launch with `&`, `wait` (pid-checked) closes the wave. FS = wave ordering; **SS = same-wave co-launch** (manifest declares SS with "co-start ≈ same scheduling step" semantics).
  - *Buys:* SS demonstrable in v1; hero's SQL-prep + Polars-prep actually run concurrently; still emits as readable structured bash (one block per wave).
  - *Costs:* interleaved logs (needs per-island log files); fail-fast granularity = wave end (a sibling keeps running after a failure until the wave closes — or `wait -n` loop for earlier abort); wave assignment is scheduler-flavored codegen.
- **F-a-γ · Dependency-driven scheduler loop.** Emitted bash maintains a pid→island map and launches each island the moment its deps finish.
  - *Buys:* maximal parallelism; FS honored edge-precisely, not level-approximated.
  - *Costs:* the generated script is a small runtime, not a readable narrative — fights the artifact-as-reviewable-text instinct that drove E-a/E-b.
- **F-a-δ · Delegate to `make`.** Emit a `Makefile` encoding the DAG (sentinel files per island); `run.sh` = thin wrapper invoking `make -j`.
  - *Buys:* dependency-driven parallelism, fail-fast, **and free resume-from-failure** (F-e) from a 45-year-old tool; DAG is declaratively visible in the artifact.
  - *Costs:* a runtime dependency (ubiquitous, but a dep — E-c fought for dependency-free artifacts); file-sentinel plumbing; make's quoting/escaping around psql/python invocations; second artifact grammar to maintain; SS still not native (co-start not expressible).

*Interlock:* δ pre-answers F-e (resume) — if we want resume at all, δ is the cheap way; if v1 = pure fail-fast, δ's main edge evaporates.

**RESOLVED 2026-07-03 → F-a = β (wave parallelism).** Topological waves; `&` + pid-checked `wait` per wave; FS = wave ordering, SS = same-wave co-launch. Bash's executor-type manifest declares FS + SS with these semantics. Per-island log files follow (see F-d). Rejected: α (kills SS for all of v1), γ (artifact becomes a runtime), δ (re-imports a dependency; resume declined in F-e).

## F-b · Q10 — what FF actually guarantees

**Question:** B-T2 pinned FF as "atomic co-finish." Across two engines that's a distributed transaction. What does the bash executor (and any v1 world) actually promise?

- **F-b-α · Restrict FF to one transactional domain.** FF is legal only when both endpoints' *effects* land in the same storage/transaction scope (e.g., two Stores into the same PG database → one transaction). Cross-engine FF = **compile error**, driven by the (storage, executor) manifests — same P2 pattern as every other capability miss.
  - *Buys:* the guarantee is real (a DB transaction), never diluted; honest v1; the manifest machinery already exists (T6).
  - *Costs:* cross-engine FF — arguably FF's most motivating case — is simply unavailable until v2.
- **F-b-β · Staging + swap.** Both islands write to staging; a serial commit step publishes both (rename / `ALTER TABLE … RENAME` swap). Guarantee: "no observer sees one output without the other, up to a small swap window."
  - *Buys:* cross-engine FF works in v1; failure before the commit step leaves nothing published (compensation-free).
  - *Costs:* not actually atomic (the swap window); needs swap-capable storages (files rename; tables rename; what about a REST sink?); "near-atomic" must be documented as the honest guarantee — quietly weaker than B-T2's word.
- **F-b-γ · Compensation (saga).** Publish eagerly; on partial failure run compensating actions (deletes/drops).
  - *Buys:* no staging requirement.
  - *Costs:* compensations are per-effect-type machinery; irreversible effects (an egress, a notification) can't be compensated; this is v2-shaped orchestrator work, not a bash script's job.
- **F-b-δ · Redefine FF as graph-internal co-visibility.** FF guarantees only that *downstream nodes in this graph* observe both outputs or neither — enforceable by pure scheduling (everything downstream of either endpoint waits on both). External observers exempt.
  - *Buys:* free under any F-a choice; no storage machinery.
  - *Costs:* **consciously amends B-T2** — "atomic co-finish" becomes a scheduling barrier; external-visibility use cases (the reason to want FF) get nothing. Arguably this is what plain data edges already give, making FF near-redundant.

*Compound worth naming:* **α now, β as the documented v2 path** — v1 keeps FF real-but-narrow; staging+swap arrives when F proper designs the orchestrator.

**RESOLVED 2026-07-03 → Q10 dissolved: FF is DROPPED from v1 (Bora's call, a fifth option).** v1 control vocabulary = **FS + SS only**; FF moves to v2 alongside events/loops, to be designed with F proper's orchestrator (where β staging+swap / γ compensation become real options). **Consciously amends B-T2's v1 scope** (the conceptual model keeps FF; the v1 machinery doesn't ship it). Bash's manifest omits FF; a program using `finishes with` against a v1 world = ordinary T6 capability compile error. C3-e's `finishes with` keyword stays reserved in the grammar. Rejected-for-v1: α (real but narrow — still machinery for a vocabulary nothing demands yet), β/γ/δ per above.

## F-c · Invocation bindings, concretely (v1 table)

The uncontroversial rows (leans, to ratify):

| (data engine, executor) | Delivery |
|---|---|
| **pg × bash** | `psql "$<conn>" -v ON_ERROR_STOP=1 --no-psqlrc -f islands/<container>.sql` — payload is E-b's CTE-per-node SQL, untouched. |
| **polars × bash** | `python3 islands/<container>.py` — E-c straight-line script + inline prelude. Executor-type manifest declares the interpreter (`python 3.13`) **and required packages** (`polars >= X`): "dependency-free" (E-c) meant *no PL harness lib*, not *no polars*; the world's runtime-verification hook (T6) is where "is polars installed" gets checked. |
| **display × bash** | Headless rule: each Display sink writes `out/<display-name>.<fmt>` + prints a one-line notice with the path. Format = project display default. |

Two real forks inside F-c:

**F-c-i · Staging format for synthesized cross-engine movement** (the hero's spine: pg → staging → polars and back):

- **α · CSV + declared schema.** Transfers = `psql \copy … csv` / `cp`; the loader re-applies the declared schema (P2-legal — schema is declared, not sniffed).
  - *Buys:* pure native tools, zero extra runtime; human-inspectable staging.
  - *Costs:* types round-trip through text (decimal/timestamp/NULL-vs-empty pitfalls — exactly what Q9's seven points fight); every loader carries coercion code; the conformance surface doubles (CSV dialect quirks).
- **β · Arrow IPC at every staging boundary.** Transfers emit as tiny generated Python (ADBC/connectorx: pg→Arrow file; Polars reads Arrow natively).
  - *Buys:* **one staging format = Q9's fingerprint format** — the `pl-conform` harness and the runtime agree by construction; types exact; NULL unambiguous.
  - *Costs:* pg-side transfers stop being "native psql" (python becomes the de-facto transfer tool in bash-land); ADBC/connectorx joins the verified-runtime list.
- **γ · Per-pair choice from the manifests** (file→file copy, pg→pg `\copy`, cross-type → Arrow). Most T6-fractal, most codegen surface.

**F-c-ii · Credential delivery** (Q8 said artifacts run under the world's named connections; *how* do they arrive at runtime?):

- **α · Env-var convention:** each named connection = `PL_CONN_<NAME>` (URI); `run.sh` fails fast if unset. Artifact stays secret-free; convention documented in the bundle manifest.
- **β · pg service file / tool-native config** (`~/.pg_service.conf` etc.): idiomatic per tool, but per-tool mechanisms multiply and Polars/ADBC has no equivalent — two mechanisms immediately.
- **γ · A runtime config file passed to `run.sh`** (`run.sh --env prod.env`): explicit, reviewable, but it's just α with a file wrapper.

**RESOLVED 2026-07-03 → F-c ratified as tabled; F-c-i = β (Arrow IPC at every staging boundary — the staging format IS Q9's fingerprint format; transfers emit as generated Python via ADBC/connectorx, joining the runtime-verification list); F-c-ii = α (env-var convention `PL_CONN_<NAME>`, pre-flight fails exit-2 on missing; γ's file wrapper layerable later without design change).** Rejected: CSV staging (re-opens the type-fidelity war Q9 closed); per-pair γ (defer fast paths until they earn their codegen); tool-native config (two mechanisms day one).

## F-d · Failure semantics

Baseline (leans, to ratify): `set -euo pipefail` everywhere; `ON_ERROR_STOP=1`; island exit ≠ 0 = island failed; **wave abort = `wait -n` loop** — first failure kills remaining siblings (`kill` + reap) and exits nonzero (fail-*fast*, not fail-at-wave-end); per-island log files `logs/<container>.log`, run.sh echoes a failure summary (island, exit code, log path). Exit-code contract: `0` ok · `1` island failure · `2` pre-flight/world-verification failure.

**F-d-i · The one real fork — what does a *connected* `err` port mean at orchestration level?** (Unconnected ⇒ fail-fast is already C3-f law.)

- **α · Not supported in v1:** `err` may only be consumed *within* an island/engine; a cross-container `err` edge = compile error (bash manifest declares no error-flow support). Orchestration-level failure handling = fail-fast, full stop.
- **β · On-failure islands:** an `err`-consuming container runs iff its source failed (bash: conditional invocation on captured exit code); run still exits nonzero after the handler (handled ≠ succeeded).
- **γ · β + the handler can *absorb*:** an explicit `absorbs` marker lets a handler convert failure to success (exit 0). Maximum power, and exactly the kind of semantic riches v1 doesn't need.

**RESOLVED 2026-07-03 → F-d baseline ratified (set -euo pipefail; ON_ERROR_STOP; `wait -n` early wave abort; per-island logs + failure summary; exit contract 0/1/2); F-d-i = α (cross-container `err` = compile error in v1; fail-fast is the only orchestration-level failure behavior; `rejects` unaffected — data-shaped, flows cross-engine as normal synthesized movement, which is where the hero's error path lives).** With F-b's FF drop: **v1 orchestration-level control surface = FS + SS, period.** On-failure islands (β) go to F proper alongside events.

## F-e · Retries / resume

- **α · None (pure fail-fast):** rerun = rerun the whole artifact; `run.sh` begins by wiping its own staging/out dirs (idempotent restart, no stale-staging miracles).
- **β · Minimal resume:** sentinel files per island; `run.sh --resume` skips completed islands. Cheap-ish, but stale-staging correctness questions (world changed underneath?) arrive immediately.
- **γ · Per-island retry counts** (transient-failure retries, from the executor manifest): orchestrator-shaped; v2 with F proper.

**RESOLVED 2026-07-03 → F-e = α (none — pure fail-fast; rerun = whole artifact; `run.sh` wipes its own staging/out dirs at start).** Resume + retries are F-proper/v2 mechanisms (executor-manifest-declared, per B-T6's taxonomy). Consistent with dropping FF and rejecting make.

## F-f · Artifact bundle finalization (E-h)

With F-a…F-e fixed, E-h's sketch concretizes. **Proposed layout** (bundle name/extension → H):

```
<program>.plb/
├── run.sh              # wave-structured orchestrator (F-a-β); pre-flight: PL_CONN_* check, staging wipe
├── manifest.<fmt>      # run manifest — see F-f-i
├── islands/
│   ├── <container>.sql # CTE-per-node (E-b)
│   └── <container>.py  # straight-line + prelude (E-c)
├── transfers/
│   └── <edge>.py       # Arrow movement scripts (F-c-i-β)
├── schemas/            # declared/staging schemas (Arrow schema JSON)
└── plans/              # plan.v1 protos — present only when the world targets Kantheon (E-a)
```
`logs/`, `staging/`, `out/` are runtime-created, wiped at start (F-e-α). Single-file variant: **dropped for v1** (directory-of-reviewable-text is the point; `tar` exists). Checksums: **sha256 per file, recorded in the manifest** — cheap, do it.

**F-f-i · Run-manifest format** (the "what runs where, in what order" record + fingerprint + checksums):

- **α · TTR-family document** (`def artifact …`): one family everywhere, Designer-viewable. Cost: the family grammar grows a machine-output-only kind; every artifact consumer (Kantheon invoker, `pl-conform`, CI) needs the TTR parser to *read a build product*.
- **β · TOML**: matches the project manifest's idiom; human-reviewable; but bash can't read it and tools need a TOML lib anyway.
- **γ · JSON**: machine-first, every consumer reads it natively (incl. `jq` in scripts); reviewable-enough for a *generated* record (the reviewable *narrative* is run.sh + islands, not the manifest).

**F-f-ii · World fingerprint content** (T6's runtime-verification hook — the artifact must record *which world* it targets):

- **α · Text hash of the world doc**: brittle — a comment reflow "changes the world."
- **β · Semantic fingerprint**: hash of the *resolved* world model (engines/executors/storages + versions + capability content + hosted model packages), plus the world's qname recorded in clear. Comment-immune; two worlds equal iff they mean the same.
- **γ · β + per-island narrowing**: additionally record which manifest facts each island actually *relied on* (finest-grained compatibility: a Polars version bump only invalidates Polars islands). Precise, but v2-shaped bookkeeping.

*Enforcement in v1:* bash `run.sh` cannot re-derive a semantic fingerprint — its pre-flight checks are env/connection-shaped only; fingerprint *verification* is for capable invokers (Kantheon, `pl-conform`, CI). The artifact **records**; the invoker **verifies** (T6's split, restated).

**RESOLVED 2026-07-03 → F-f: layout ratified as proposed (naming → H); no single-file variant in v1; sha256 per file in the manifest; F-f-i = γ (JSON run manifest — the reviewable narrative is run.sh + islands, the manifest is a machine record); F-f-ii = β (semantic fingerprint of the resolved world model + world qname in clear; record-in-artifact / verify-by-capable-invoker; γ per-island narrowing layerable in v2).** E-h is closed.

---

## RESOLVED (2026-07-03) — F-lite converged 🟢

- **F-a = β** · wave parallelism; bash manifest = FS + SS (SS = same-wave co-launch).
- **F-b / Q10 dissolved** · **FF dropped from v1** (conscious B-T2 v1-scope amendment); FF designs with F proper in v2; `finishes with` stays reserved in the grammar.
- **F-c** · binding table ratified; **F-c-i = β** Arrow IPC staging everywhere (staging format = Q9 fingerprint format); **F-c-ii = α** `PL_CONN_<NAME>` env-var credentials.
- **F-d** · fail-fast baseline (`set -euo pipefail`, ON_ERROR_STOP, `wait -n` early wave abort, per-island logs, exit 0/1/2); **F-d-i = α** cross-container `err` = compile error in v1 (`rejects` unaffected — data-shaped).
- **F-e = α** · no retries/resume; rerun = whole artifact; staging/out wiped at start.
- **F-f** · bundle ratified; JSON manifest; semantic world fingerprint; checksums; E-h closed.

**With this, the v1-blocking design set is complete: A · B · G · C0 · C3 · D · E · F-lite all 🟢.**

---

## Open questions (F-local)

- ~~Log discipline~~ — resolved with F-a-β/F-d (per-island log files + run.sh summary).
- ~~run.sh world verification~~ — resolved with F-f-ii (record/verify split; bash pre-flight = env/connection checks only).
- Q1's runtime half (agent as *invoker* of compiled artifacts) — confirmed post-v1 (F proper / Kantheon path).
- Bundle + extension naming, `PL_CONN_` prefix bikeshed → H.
- `pl-conform` invokes artifacts — pin its invoker contract (reads manifest JSON, runs run.sh, collects `out/` Arrow) when the harness is specced (G/implementation).

## Cross-links

F-lite → B-T2/T6 (control vocabulary + executor manifests) · → E-a/E-g/E-h (payloads, Transfer bindings, bundle) · → D (staging storage feeds F-b-β; named connections feed F-c) · → Q9 (`pl-conform` runs artifacts — the harness is an invoker) · → F proper (events, runtime params, Kantheon orchestrator — post-v1).
