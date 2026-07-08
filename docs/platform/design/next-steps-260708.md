# Tatrman Platform design — next steps (session handoff, 2026-07-08)

> Cold-start file for the next session. Read [`00-control-room.md`](./00-control-room.md) first, then this.

## Where the effort stands

One day, five workstreams converged: **A · B · C · D 🟢** (+ framing). Remaining: **E, F, G, H, I ⚪ · J** (naming, deliberately last).

The converged core, in one breath: a **mode-blind MIT compiler** behind a source SPI (connected = a binding; `ttr.lock` pins canon, stats float per-object and are recorded per-compile; hard parity — compile is a pure function of its recorded snapshot); a **new `tatrman-platform` repo** (Gradle-only, commercially licensed, license=repo boundary) hosting a **new metadata server on `ttr-metadata`** (+ lineage + exports), **two doors one hall** (program door = F-proper's home; query door = slimmed Theseus; Argos+validator-SPI → Kyklop → workers; Charon home; Proteus dissolves), built by **strangler sequence ①–⑦**; edition rule = **"compile vs operate"**; dependency chain **`tatrman → platform → kantheon`**, build-time one-way, runtime SPI plugins legal.

## Next session: F (scheduler & job execution) — recommended first divergence

F is the biggest undesigned surface and everything it needs is now in place. F inherits:

- **TTR-P F-proper's deferred list** (its design home is here): events, FF (staging+swap / compensation options from F-b), retries/resume, on-failure islands, runtime parameters.
- **The two-door scheduling/quota problem** (C-3-γ): nightly programs vs agent query bursts on one hall.
- **CQ-2:** run/lineage store — scheduler-owned or metadata-server-owned (reads served by metadata either way).
- **BQ-5:** `ttr.lock` scope (per project root vs per program/bundle) — bites at deploy.
- **C-3-δ** (workers-pull/reconciler) recorded as the architecture F may *grow toward*; **C-4-β** (Charon-as-worker-kind) re-examines here.
- **Q-3:** is the deployed unit the F-lite bundle or a richer platform artifact? (Deploy pipeline = compile (pure) + platform phases — identity, secrets, registration, schedule — per B-4.)
- Map branches already sketched: F-α own scheduler · F-β delegate-only · F-γ thin triggers + F-proper executor · F-δ metadata-server-reconciler.

## Then

- **E (orchestration integration)** — pairs naturally with F (LF-6 spans both); the "registered engine = one registry concept for DBs and orchestrators?" hunch needs pressure-testing; emit-plugin SPI mechanism (MIT) vs plugin placement (either side, per A).
- **G (Designer evolution)** — much is pre-shaped: Q-4-a extension surface, metadata server as backend, G-γ (writes-through-git) vs live co-editing is the real fork.
- **H (security)** — inherits C-5-ii/iii (whois-descendant shape, ingress identity), Q-1 (standalone security meaning), and owes B the advisory-only-compile-policy confirmation (the last open B rider).
- **I (external metadata & megaproviders)** — the outbound half already landed in C-2 (export connectors); inbound harvest + megaprovider connectors remain; pick one anchor system for v1.
- **J (naming)** — last. Queued demands: platform repo name, infra repo name ("Tatry" parked), extension-surface name, license name, door names, metadata-server name.

## Standing rules to carry (cite by ID)

- **P1** standalone is not a demo · **P2** one-way build-time arrow, `tatrman → platform → kantheon`, runtime SPI plugins legal · **P3** no miracles.
- **B-4 seam-legality:** the mode seam admits *data and diagnostics; never identity, never side effects*.
- **D-3 ownership:** toolchain-touched ⇒ tatrman-owned (MIT); service-internal ⇒ platform-owned.
- Hero: **"one program, three lives"** (standalone bash bundle · platform deploy+schedule · Dagster/OpenMetadata federation) — render every F/E/G/H/I option against it.

## Process notes

- After E+F converge, consider an **independent review session** (fresh re-read, decision-by-decision confirmation — the ttr-p `review-260702.md` pattern).
- A **consolidation sweep** should batch-ratify accumulated micro-items before wrap-up (currently queued: CQ-4 capability-vocab mapping; pinakes verify-then-place; kantheon-repo arc stubs ⑥ + doc sweep; metis engine-by-manifest revisit condition).
- When all 🟢: write `design.md` (for `/planning`) + `detailed-design.md`, then a planning session — architecture, contracts, phased plan.

## Open questions snapshot (rolling, from the control room)

- **Q-1 (H):** does any security concept exist standalone? (LF-8 remnant)
- **Q-3 (F):** deployed unit = bundle or richer artifact?
- **BQ-5 (F/D):** lock scope.
- **CQ-2 (F):** run-store ownership.
- **CQ-4 (work item):** worker manifests vs Kyklop routing vocabulary.
- **LF-4 (E), LF-6 (F/E), LF-7 (I), LF-8 (H)** — the four unresolved load-bearing forks.
