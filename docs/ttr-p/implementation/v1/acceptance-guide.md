# TTR-P v1 — Acceptance Guide

> **Purpose.** All eight implementation phases (P0–P7) are code-complete and merged to `master`.
> What is left before v1 can be called "done" is **human-gated acceptance** — the checks that need
> a live environment (a Postgres + local Python data stack) or a real model API key, and a pair of
> hands to confirm the result. This guide is the step-by-step runbook for those checks: what to run,
> in what order, and **exactly what a pass looks like**.
>
> Companion to [`next-steps.md`](./next-steps.md) (the deferral/owner register) and
> [`tasks-overview.md`](./tasks-overview.md) (the master checklist). When you finish an acceptance
> here, record it in the matching `progress-phase-NN.md` and tick the phase-DONE row in the overview.

Legend used below: 🐳 needs a live Postgres + Python stack · 🔑 needs a model API key · 🖐 hands-on /
visual confirmation · ⏱ ~how long.

---

## 0. What you are accepting (the four checks)

| # | Check | Proves | Gates |
|---|---|---|---|
| **A** | Live conform — hero, two engines | "One program, two engines, identical results" (the A4 core) | 🐳 |
| **B** | Designer A4 exit seal (T5.4.8) | The graphical surface can author → run → render the hero end-to-end | 🐳 🖐 |
| **C** | VS Code assist demo (T7.2.6) | The NL→validate→repair→Apply loop works with a real model | 🔑 🖐 |
| **D** | Eval baseline (T7.2.7) | A committed, CI-replayable assist-quality baseline exists | 🔑 🖐 |

**A** is the fastest and the strongest single signal — do it first. **B** is the headline "is v1
real" milestone (it's the last unchecked phase-DONE box). **C** and **D** need a model key and can
be done independently once you have one.

---

## 1. One-time environment setup

You need this for checks **A** and **B**. Do it once; keep the Postgres container running for both.

### 1.1 Toolchains

- **JDK 17+** and the repo's Gradle wrapper (`./gradlew`). Verify: `./gradlew --version`.
- **Node 20+ / pnpm 11** (only for check B's Designer UI). Verify: `pnpm --version`.
- **Python 3** with **`polars`**, **`pyarrow`**, and **`adbc-driver-postgresql`** importable on the
  same `PATH` the CLI runs under. This is the executor manifest's package list — the run scripts
  shell out to `python3`. Verify:
  ```bash
  python3 -c "import polars, pyarrow, adbc_driver_postgresql; print('runtime OK')"
  ```

### 1.2 Postgres

Bring up a Postgres 16 (this is the same setup used for the Phase-3.5 live proof — Rancher-Desktop
`postgres:16` on `localhost:55432` last time):

```bash
docker run -d --name ttrp-pg -e POSTGRES_PASSWORD=ttrp -p 55432:5432 postgres:16
```

Seed it with the hero schema + data. The seed script lives in the conform module:

```bash
psql "postgresql://postgres:ttrp@localhost:55432/postgres" \
  -f packages/kotlin/ttrp-conform/src/test/resources/seed/hero_seed.sql
```

> The seed creates the `accounts` table (with `account_id` as **text** and a populated `region`
> domain — the two data shapes the live proof depends on; see progress-phase-03.md's "4 latent bugs").

### 1.3 The connection env var

Everything that touches PG reads the connection from a `TTR_CONN_ERP_PG` environment variable
(the `run.sh` pre-flight requires it; `ttrp conform` forwards every `TTR_CONN_*` var into the bundle):

```bash
export TTR_CONN_ERP_PG="postgresql://postgres:ttrp@localhost:55432/postgres"
```

Adjust host/port/password to match your container.

### 1.4 Build once

```bash
./gradlew build          # Kotlin: compiles + runs the offline regression suites
pnpm -r build            # TS: only needed for check B (the Designer UI)
```

`./gradlew build` should be **green repo-wide** before you start — the offline emit/bundle/conform
suites are the standing regression gate, and a red bar here means fix that first.

---

## 2. Check A — Live conform (🐳 ⏱ ~5 min)

**Goal.** The hero pipeline, built two ways — `authored` (accounts pulled from PG as Arrow, crunched
in Polars) and `crunch-pg` (the whole crunch retargeted to run inside Postgres via ADBC) — produces a
**byte-identical `out/main_result.arrow`** under the Q9 seven-point comparison.

### Path A1 — the gated test (simplest)

The acceptance is already encoded as `HeroConformLiveTest` (in `ttrp-cli`), gated by
`TTRP_CONFORM_PG=1`. With the environment from §1 up:

```bash
TTRP_CONFORM_PG=1 \
TTR_CONN_ERP_PG="postgresql://postgres:ttrp@localhost:55432/postgres" \
./gradlew :packages:kotlin:ttrp-cli:test --tests "*HeroConformLiveTest" --rerun-tasks
```

**✅ Expected:**
- The test **runs** (does not print `SKIP: TTRP_CONFORM_PG != 1`) and **passes**.
- On failure, the assertion clue prints the conform outcome summary (which of the seven points
  diverged and on which column) — that's your diagnostic.

> If a golden or result looks stale, the Kotlin daemon can hold old test classes — add `--rerun-tasks`
> (already above) or run `./gradlew --stop` first. (Noted in progress-phase-03.md.)

### Path A2 — the CLI (what an operator would actually type)

To confirm the *shipped command* (not just the test) behaves, run `ttrp conform` against the hero
fixture directly:

```bash
./gradlew :packages:kotlin:ttrp-cli:installDist
export PATH="$PWD/packages/kotlin/ttrp-cli/build/install/ttrp-cli/bin:$PATH"

ttrp conform packages/kotlin/ttrp-cli/src/test/resources/fixtures/hero.ttrp
echo "exit=$?"
```

**✅ Expected:**
- Printed conform summary reports **all seven points pass** across the placement variants.
- **Exit code 0.** (`1` = a comparison mismatch; `2` = an invocation/pre-flight failure — usually a
  missing `TTR_CONN_ERP_PG` or a Python import missing on `PATH`.)
- The `--tolerance <col>=<eps>` flag is available if a float column needs a documented epsilon; a
  clean pass should not need it.

**Record:** tick the corresponding note in `progress-phase-03.md` / `tasks-overview.md` Phase-3 row
(already marked DONE from the earlier local run — this is your independent re-confirmation).

---

## 3. Check B — Designer A4 exit seal, T5.4.8 (🐳 🖐 ⏱ ~20 min)

**Goal — the single biggest "is v1 real" checkpoint.** Build the hero **from an empty Designer
canvas**, run it, see the Arrow result render, and confirm PG↔Polars conformance. This exercises the
*second* authoring surface (the graphical one) end-to-end.

Keep the Postgres from §1 running.

### 3.1 Start the Designer server (the WS-LSP host)

```bash
./gradlew :packages:kotlin:ttr-designer-server:run
```

This serves the TTR-P LSP over WebSocket at **`ws://127.0.0.1:9257/lsp`** (the `/lsp` route mounted
on the existing designer server, Stage 5.1).

### 3.2 Start the Designer UI

In a second terminal:

```bash
pnpm --filter @tatrman/ttrp-designer dev
```

Vite serves the canvas at **http://localhost:5173**. It connects to `ws://127.0.0.1:9257/lsp` by
default (override with `VITE_TTRP_LSP_URL`; the document URI is `VITE_TTRP_DOC_URI`, default
`file:///hero.ttrp`). Open the page — if it shows *"Connect the Designer server on
ws://127.0.0.1:9257/lsp…"*, the server in 3.1 isn't up.

### 3.3 Author the hero on the canvas

Starting from an empty canvas, use the additive build vocabulary (the v1 edit cut) to construct the
hero flow:

1. **createContainer** — the crunch container.
2. **addNode** — the hero node chain: Load (sales CSV) · Load (accounts) → **Join** → **Filter** →
   **Aggregate** (sum by region) → **Sort** → **Limit** → **Display**.
3. **connect** — wire the ports along that chain.
4. **assignTarget** — set the container's engine target.

**✅ Expected while authoring:**
- Each edit round-trips as a whole-document `WorkspaceEdit` (formatter-owned) — the text stays
  canonical and re-parses cleanly.
- Diagnostics surface as **badges** on nodes; a well-formed hero ends with **no error badges**.
- Mutating/rename ops are intentionally **not** in the v1 canvas cut — those return `TTRP-EDIT-003`
  (rename goes through `textDocument/rename`). Seeing that on a rename attempt is correct, not a bug.

### 3.4 Run and render

Trigger the run from the canvas (the loopback `GET /out/{name}` route serves the result back).

**✅ Expected:**
- The pipeline runs against the live PG + Polars and the **Arrow result renders in the canvas grid**.
- For the seeded hero data the top result row is **`north, 150000.5, 75000.25`** (the value the
  Phase-3.5 live proof pinned).

### 3.5 Conform from the graphical build

Finally, confirm the graphically-authored program conforms across engines — either re-run **Check A's
`ttrp conform`** against the `.ttrp` the canvas produced, or use the in-app conform if wired.

**✅ Expected:** PG↔Polars identical, seven points pass — **identical to Check A's result, but
reached from the canvas.** That equivalence *is* the A4 exit criterion.

**Record:** this is the one still-open phase-DONE box. On success, tick **Phase 5 "Phase DONE"** in
`tasks-overview.md` and note the T5.4.7 review checkpoint + T5.4.8 acceptance in
`progress-phase-05.md`.

---

## 4. Check C — VS Code assist demo, T7.2.6 (🔑 🖐 ⏱ ~15 min)

**Goal.** Confirm the NL-assist loop (generate → validate → repair → **Apply**) works end-to-end with
a real model, and that the never-apply-on-failure gate (C4-d-iii) holds.

### 4.1 Offline dry run first (no key, proves the wiring)

1. Open `packages/ttrp-vscode-ext` in VS Code and press **F5** — launches an Extension Development Host.
2. Open the hero `.ttrp` file.
3. The default `ttrp.assist.endpoint` is **`mock:`** — a deterministic offline provider (no network,
   no key). Run **`TTR-P: Generate with Assist`** (`ttrp.assist.generate`) with the cursor inside the
   crunch container.

**✅ Expected (mock):** the mock model returns a candidate with an invalid `==`, the loop feeds the
`EQ-001` diagnostic back, repairs it once, and shows a `vscode.diff` with a modal **Apply / Discard**.
**Apply** inserts the validated text; **Discard** changes nothing.

### 4.2 Real-key run

1. Set the key: **`TTR-P: Set Assist API Key`** (`ttrp.assist.setApiKey`) — stored in VS Code
   **SecretStorage** (the LSP/compiler never sees it).
2. In settings, set `ttrp.assist.endpoint` to your HTTPS endpoint and `ttrp.assist.model` to the
   model id. `ttrp.assist.maxRepairs` defaults to **3**.
3. Run `ttrp.assist.generate` with a natural-language request at the cursor.

**✅ Expected (real key):**
- A **diff appears** and **Apply** inserts model-generated text that **passes `ttrp/validate`**.
- **Failure path (must also hold):** if the model can't produce a valid candidate within
  `maxRepairs`, the loop returns `ok=false`, shows the diagnostics, and **applies nothing** — no
  partial/invalid text ever lands. This structural gate is the point of the check.

**Record:** note the real-key confirmation (both the Apply-success and the repair-exhaustion paths) in
`progress-phase-07.md`.

---

## 5. Check D — Eval baseline, T7.2.7 (🔑 🖐 ⏱ ~30 min)

**Goal.** Produce and commit the first assist-quality baseline: model candidates, scored by the
engine-free `ttrp eval` comparator, pinned as a CI-replayable regression.

> ⚠️ **One wiring gap first.** Step 5.2 below needs the host to *dump* accepted candidates to disk.
> The setting `ttrp.assist.dumpCandidatesDir` is **not yet wired** (next-steps §3b — a small task I
> can pick up). Until it is, dump candidates manually (copy each Apply result to
> `<dir>/<corpus-id>.ttrp`). Ping me to wire it and this step becomes one setting.

### 5.1 Prerequisites
- Check **C** working (real key + endpoint).
- A resolvable project world (same requirement as `ttrp check` — `ttrp eval` compiles both candidate
  and expected through the front-half).

### 5.2 Generate candidates
Run `ttrp.assist.generate` over the corpus prompts, writing each accepted candidate to a working dir
as `<corpus-id>.ttrp`. The corpus lives at
`packages/kotlin/ttrp-conform/src/test/eval/` (`corpus.toml` + `fixtures/`).

> The seed corpus is **2 representative entries**, not the full A5 ten (next-steps §3c). A meaningful
> baseline wants the expansion — flag it and I'll add the remaining eight (they need the PG world to
> compile, so it pairs with §1 being up).

### 5.3 Score and commit

```bash
ttrp eval \
  --corpus packages/kotlin/ttrp-conform/src/test/eval \
  --candidates <your-dump-dir> \
  --report baselines/001/report.toml
```

**✅ Expected:**
- Each candidate scores **`pass`** (shape-match against the expected graph — SSA renaming is ignored
  intrinsically; interposed Calc/Project are tolerated) — or a reported `shape-mismatch` / `invalid`
  you consciously accept into the baseline.
- The report writes to `baselines/001/report.toml`. (There is **no `baselines/` dir yet** — this run
  creates it.)

Then:
1. Commit `baselines/001/` (the report **and** the candidate files).
2. Add an `EvalBaselineSpec` — a CI-safe regression that re-scores the committed candidates **with no
   model** (pure comparator), so the baseline can never silently rot.
3. Tick **Phase 7 "Phase DONE"** in `tasks-overview.md` → **v1 complete.**

---

## 6. Two known blockers you may hit (not defects)

- **Bare `.ttrb` hero run** (`ttrp run hero-sentences.ttrb`) — the wrapper synthesis exists (Phase
  6b), but the specific `.ttrb` hero fixture references a schema (`erp.sales_schema`) **not present in
  the shared test world**. A live bare-run needs fixture/world alignment first (next-steps §1a). The
  embedded `"""ttrb` surface *does* build the correct graph today — only the bare + live run is gapped.
- **Three-way `ttrp conform`** (canonical vs SQL vs pandas authorings, T6.3.6) — same 🐳 PG gate as
  Check A; not blocking the A4 seal, but the natural next thing to run once PG is up.

---

## 7. Quick reference — commands

| Purpose | Command |
|---|---|
| Offline regression (must be green first) | `./gradlew build` |
| Live conform (test) | `TTRP_CONFORM_PG=1 TTR_CONN_ERP_PG=… ./gradlew :packages:kotlin:ttrp-cli:test --tests "*HeroConformLiveTest" --rerun-tasks` |
| Install `ttrp` CLI | `./gradlew :packages:kotlin:ttrp-cli:installDist` → `packages/kotlin/ttrp-cli/build/install/ttrp-cli/bin/ttrp` |
| Live conform (CLI) | `ttrp conform <file>.ttrp [--tolerance col=eps]` |
| Build a bundle | `ttrp build <file>.ttrp --out <dir>` |
| Run a bundle / file | `ttrp run <file>.ttrp` \| `ttrp run <bundle-dir>` |
| Designer server (WS-LSP :9257) | `./gradlew :packages:kotlin:ttr-designer-server:run` |
| Designer UI (:5173) | `pnpm --filter @tatrman/ttrp-designer dev` |
| VS Code assist | F5 in `packages/ttrp-vscode-ext` → `TTR-P: Set Assist API Key` → `TTR-P: Generate with Assist` |
| Eval scorer | `ttrp eval --corpus … --candidates … --report …` |

Environment: `TTR_CONN_ERP_PG` (PG connection, required by run/conform); `TTRP_CONFORM_PG=1` (un-skip
the gated live test); `VITE_TTRP_LSP_URL` / `VITE_TTRP_DOC_URI` (Designer overrides).
