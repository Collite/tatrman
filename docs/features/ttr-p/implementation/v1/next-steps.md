# TTR-P v1 — Next steps (deferrals + what needs Bora)

> Companion to [`tasks-overview.md`](./tasks-overview.md) and the `progress-phase-NN.md` docs.
> Written 2026-07-08 at the close of Phase 7 (PR #21). Everything below is either **deferred
> implementation** (I can do it — it needs scheduling or an unblock) or **needs you** (a
> decision, a credential/environment, or a hands-on manual check). Owners are marked
> **[you]** / **[me]** / **[you+me]**.

Legend: 🔑 needs a credential/secret · 🐳 needs a live environment (PG/Polars) · 🖐 hands-on
manual · 🧭 a decision/sign-off · ⛓ blocked by another item.

---

## 0. Immediate

- **[you] Review + merge PR #21** (`feature/ttr-p-v1-phase7` → master). Phase 7 both stages
  code-complete; ~95 new specs; all touched modules green. Consider a `/code-review` pass first.
- **[you] 🧭 Sign off (or wave through) the Stage-7.1 reconciliations** recorded in
  `progress-phase-07.md` (package `dialect.b`, TOML tables in main resources, `ttrp eval` named
  top-level not `conform eval`, ref-word/binding leftovers decided in the `TTRB.g4` header). These
  were conscious calls, not drift — but they're the kind of thing a phase review should ratify.

---

## 1. The two Phase-7 hard gates (deferred by agreement)

### 1a. Bare `.ttrb` program run — `⛓ 🐳`
`ttrp run hero-sentences.ttrb` (T7.1.7) is **blocked on Phase-6 work that was never built**:
the **bare-program wrapper synthesis** (T6.3.3 / T6.3.4 — a `.ttrb`/`.ttr.sql`/`.ttr.py` file →
a synthesized derived container from `[ttrp]` bare-target / bare-shell / display-default /
default-imports, S18). See item **3a**. It also needs a dockerized PG+Polars to actually run.
- Today: the `.ttrb` hero **parses, folds its verbose comparisons, and decomposes**; the embedded
  `"""ttrb` island builds the correct hero graph. Only the bare *wrapper + live run* is missing.

### 1b. Full byte-identical identity gate for TTR-B — ✅ DONE (branch `feature/ttr-p-v1-fragment-identity`)
The two shared-infra deltas are fixed in `GraphBuilder`: `refText` now stringifies a literal load
source, and the single default DATA out auto-maps **uniformly** for a FlowBody and a fragment.
`TtrbGraphIdentitySpec` proves **embedded `"""ttrb` ≡ canonical — byte-identical normalized graphs**;
the P6 SQL/pandas gate is unaffected. The **bare** third surface lands once T6.3.3 (§3a / PR #22)
merges — it reuses the embedded path, so bare ≡ embedded holds by construction.

### 1c. Eval baseline (T7.2.7) — `🔑 🖐`
The scorer (`ttrp eval`) and corpus exist; the **baseline run is inherently manual/off-CI**:
1. **[you] 🔑** set a real model key via the VS Code command **`ttrp.assist.setApiKey`** and set
   `ttrp.assist.endpoint`/`model`.
2. **[you] 🖐** run `ttrp.assist.generate` over the corpus prompts with `ttrp.assist.dumpCandidatesDir`
   pointed at a working dir (note: the dump-mode setting is **not yet wired** — see item 3b).
3. **[you+me]** score with `ttrp eval --corpus packages/kotlin/ttrp-conform/src/test/eval
   --candidates <dump> --report baselines/001/report.yaml`, commit `baselines/001/` (report +
   candidates), and add `EvalBaselineSpec` (a CI-safe regression pin — re-scores with no model).
4. Then mark **plan P7 DONE** ("v1 complete") in `tasks-overview.md`.

---

## 2. Manual acceptance you owe (human-gated, carried across phases)

- **[you] 🖐 🔑 Phase 7 — VS Code assist demo.** F5 the Extension Dev Host → `ttrp.assist.setApiKey`
  → open the hero → `ttrp.assist.generate` → confirm the diff appears and **Apply** inserts validated
  text (and that repair-exhaustion shows diagnostics and applies **nothing**, C4-d-iii). The `mock:`
  endpoint already proves the loop offline; this is the real-key confirmation. Record it in
  `progress-phase-07.md`.
- **[you] 🖐 🐳 Phase 5 — T5.4.8 acceptance (the v1 A4 exit seal), still pending.** Build the hero
  from an empty Designer canvas → run → Arrow render → `ttrp conform` PG↔Polars identical. Plus the
  **T5.4.7** review checkpoint. Recorded as pending in `progress-phase-05.md` even though the code
  merged. This is the single biggest "is v1 real" checkpoint.

---

## 3. Deferred implementation I can pick up (needs your go / scheduling)

- **3a. [me] ✅ DONE (Phase 6b, branch `feature/ttr-p-v1-p6b-bare-wrapper`).** Bare-program wrapper
  synthesis + fragment scope resolution: a marked `.ttr.sql`/`.ttr.py`/`.ttrb` file is a valid TTR-P
  program (`WrapperSynthesizer` → canonical wrapper → normal front-half). 9 specs green; see
  `progress-phase-06b.md`. Remaining bare-run gate is now purely the **🐳 PG env + fixture/world
  alignment** for the specific `.ttrb` hero schema ref (item 1a).
- **3b. [me] Wire `ttrp.assist.dumpCandidatesDir`** in the VS Code host (needed for item 1c step 2).
  Small — write each accepted candidate to `<dir>/<corpus-id>.ttrp`.
- **3c. [me] Expand the eval corpus** from the 2-entry seed to the full A5 ten (T7.2.4). The expected
  fixtures must compile through a real world; pairs naturally with 3a/the PG env.
- **3d. [me] Phase-6 T6.3.6 `ttrp conform` hero-three-ways** (needs 🐳 PG + Polars) and **T6.3.7
  Designer fragment drill-in** (`ttrp/getGraph` on a fragment container; the getGraph path already
  serves derived containers — needs the P5 designer stack).
- **3e. [me] TTR-B roster fidelity tails** (Stage 7.1 shipped these as best-effort): `Keep … except`
  → a real negative Select (needs static-schema expansion, currently a plain project of the excepted
  names); `Rename`/`Convert` currently lower to `calc` (approximate — a dedicated rename/cast lowering
  would be cleaner). No hero exercises them; fine to defer.
- **3f. [me] authoringContext still-empty sections** (schema-final, present-but-empty since P4):
  capability **node/function rosters per engine** (from the T6 manifests) and **`modelObjects`**
  (db+er object enumeration). The dialect rosters + insertionTarget + diagnostics landed in 7.2; these
  two remain.

---

## 4. Decisions only you can make (design/scope)

- **🧭 Sort direction / `LIMIT … OFFSET` in the derived graph.** The dialects parse `DESC`/`NULLS
  FIRST|LAST`/`OFFSET` and **drop them from the derived graph** — execution is verbatim so *results
  are correct*, and the identity gate relies on the drop (canonical TTR-P has no syntax for them). If
  you want the *graph* to carry ordering (for the Designer / a future optimizer), that's a **language
  extension**: canonical grammar + `Sort`/`Limit` node-model fields + all three surfaces. Flagged in
  the Phase-6 review; parked here as a v1.x call.
- **🧭 Bare-fragment surface priority** — see 3a. Is the bare `.ttrb`/`.ttr.sql`/`.ttr.py` surface a
  v1 must-have (do 3a now), or a v1.1 item?
- **🧭 Erroneous-rows-in-SQL producer semantics** — ✅ **DESIGNED 2026-07-15**: the design effort
  ran and closed in `docs/features/ttr-p/design/rejects/` (decision log in `00-control-room.md`;
  `design.md` is the `/planning` input). Result in one line: catalogue-defined canonical validity
  (R-P2) + graph-rewrite guard-and-branch elaboration; SQL rejects = one more terminal SELECT,
  Polars = mask-and-split; conform gains an eighth (partition) check. Next: `/planning` (spikes
  R-Q9..R-Q12 first). No longer a design blocker — building it is a schedulable implementation arc.

---

## 5. Environment / credentials you'd need to provide (summary)

- **🐳 Dockerized Postgres + local Polars** — for: bare `.ttrb` run (1a), `ttrp conform` three-ways
  (3d), the eval-corpus expansion's compile (3c), and the Phase-5 A4 seal (2). Same gate as the
  Phase-3.4 / 3.5 live conform (you ran `postgres:16` on Rancher-Desktop @ `localhost:55432` before).
- **🔑 A model API key** (any endpoint the host can POST to) — for the eval baseline (1c) and the
  real-key VS Code acceptance (2). It lives only in VS Code SecretStorage; the toolchain never sees it.

---

## 6. Cross-cutting / external (from `tasks-overview.md` §Cross-cutting — unchanged, listed for completeness)

- **[you-side] Kantheon Phase B** (adopt the published `org.tatrman:ttr-translator` + delete the
  in-repo copy) — off the critical path (TR-8).
- **[trivial] Fork-ops residue** — old-repo freeze README; rename `~/Dev/tatrman` → `tatrman-poc`.
- **[post-v1] TTR-M convergence** — `.ttrl` migration + Designer-server convergence + `modeler/*` →
  `ttrm/*` (C1-f). Explicitly not in the v1 arc.
- **[v2 register] F proper, events, FF, retries, optimizer Z, md-sugar** (architecture §10).

---

## 7. Suggested order

1. You: review/merge **PR #21** (§0).
2. Decide **3a** (bare-wrapper synthesis). If yes → I build it; it unblocks 1a + the bare identity
   surface + pairs with 3c.
3. When you have the **🐳 PG env** up: I finish 3d + the eval-corpus expansion; you do the **Phase-5
   A4 seal (2)** — the real v1 milestone.
4. When you have a **🔑 model key**: I wire 3b, you run the VS Code acceptance + the eval baseline
   (1c) → **mark v1 complete**.
5. Park §4 design calls and §6 cross-cutting for their own sessions.
