# Tasks · P7 · Stage 7.2 — Assist finalization + eval corpus

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The assist contracts **complete and exercised**: `ttrp/authoringContext` content-complete per contracts §7 (all sections filled, **diagnostics catalogue included** — the P4 Stage 4.2 bundle schema gets its remaining sections); cursor-scoped dialect insertion (C4-d-i = γ: host declares the insertion target, assist emits that container's dialect); a **reference host demo** — VS Code command `ttrp.assist.generate` driving generate→validate→repair with a user-supplied model API key (the LLM call lives at the HOST, never the compiler — P2, C4-d-ii = γ); the **assist/agent eval corpus** (NL request → expected graph shape, C4-e) wired `ttrp-conform`-adjacent with a **baseline eval run recorded as a versioned results file**. With this, plan P7 DONE closes: **v1 complete**.

## Pre-flight (all must pass before T7.2.1)

- [ ] Stage 7.1 DONE (this stage consumes `ttrb-reject-table.yaml` + `ttrb-verbose-synonyms.yaml` and the `.ttrb` dialect registration).
- [ ] `ttrp/authoringContext` v1 + `ttrp/validate` exist from P4 Stage 4.2 (plan: "bundle schema finalized here — C4 leftover"). **Dependency flag:** `tasks-p4-s4.2-formatter-methods.md` did **not exist** when this list was drafted — this list aligns to `plan.md`. Locate the schema **in code** (`ttrp-lsp` module) and treat it as the source; if P4 shipped a schema doc, reconcile section names with it and record deltas here. If the methods don't exist at all, STOP → §Blockers.
- [ ] P6 dialect rosters + reject tables merged (cursor-scoped insertion and the diagnostics catalogue cover TTR-SQL/TTR-pandas, not just TTR-B).
- [ ] VS Code extension from P4 Stage 4.3 builds and connects: open a hero `.ttrp` in the Extension Development Host, diagnostics stream.
- [ ] Naming check: the method is `ttrp/authoringContext` (S8 rename) — older docs say `ttrp/assistContext`; if any shipped code still uses the old name, fix it as the first commit of this stage (S8 is ratified).

## Tasks

### T7.2.1 · TDD: authoring-context completeness specs + golden bundle

- [ ] Specs under `packages/kotlin/ttrp-lsp/src/test/kotlin/org/tatrman/ttrp/lsp/assist/` (Kotest FunSpec; same harness P4 used for `ttrp/*` methods):
  - `AuthoringContextCompletenessSpec` — for the hero document, the bundle contains ALL contracts §7 sections, each non-empty: `worldSummary` (engines, executors, storages, staging, rls flags) · `capabilities` (node/function support per engine, from the T6 manifests) · `modelsInScope` (db + er objects with schemas) · `inScopeNames` (imports, SSA variables, ports at cursor) · `grammarSummary` (statement/roster tables per dialect: canonical forms, TTR-SQL clause table, TTR-pandas method roster S17, **TTR-B sentence roster + verbose synonym table from Stage 7.1's fixtures**) · `diagnosticsCatalogue` (T7.2.2).
  - `ValidateLoopSpec` — `ttrp/validate` on: (a) a valid candidate → empty diagnostics; (b) a candidate with `==` → `TTRP-EQ-001` **with suggestion field populated** ("use ="); (c) an out-of-roster TTR-B candidate with `dialect: "ttrb"` param → `TTRP-B-001` + suggestion (the repair loop's food — contracts §4, §8).
- [ ] Golden fixture `packages/kotlin/ttrp-lsp/src/test/resources/assist/authoring-context-hero.golden.json` — the full serialized bundle for the hero at a program-scope position; snapshot-tested (deterministic serialization required — P2; stable key order).
- [ ] Both specs created RED (sections missing from the P4 v1 bundle).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.assist.*"` — compiles, new specs RED for the right reason (missing sections, not harness errors).

### T7.2.2 · Diagnostics catalogue — the repair vocabulary assembled

- [ ] Implement the `diagnosticsCatalogue` bundle section: aggregate ALL versioned reject-table fixtures — TTR-B (`ttrb-reject-table.yaml`, 7.1), TTR-SQL + TTR-pandas tables (P6), canonical-grammar rejects (S9 `TTRP-EQ-001`, S15 `TTRP-SQL-014`, `TTRP-CTL-001`, …) — into one catalogue: `{id, area, messageTemplate, suggestion}` per entry (contracts §8: areas EQ, SQL, PD, B, CTL, CAP, MOV, SCH, WLD, RLS). **Single source = the fixture files**; the catalogue is assembled at build/serve time, never hand-copied.
- [ ] Registry completeness test: every diagnostic id the front-half can emit (enumerate via the diagnostic-framework registry from P1 Stage 1.1) appears exactly once in the catalogue; every catalogue entry has a non-empty suggestion (the assist repair vocabulary guarantee, C4-b-iii/C4-d).
- [ ] Prompt-readiness: the catalogue serializes into the bundle's prompt-shaped rendering (contracts §7: "deterministic, prompt-ready serialization"). Resolve the C4 leftover "prompt-shaped text vs structured JSON the host renders" **if P4 didn't**: lean = structured JSON + a deterministic `renderPrompt()` producing markdown tables; record the ruling in the schema code + here.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.assist.AuthoringContextCompletenessSpec"` — the catalogue assertions GREEN (other sections may still be red until T7.2.3).

### T7.2.3 · Cursor-scoped dialect insertion (C4-d-i = γ)

- [ ] `ttrp/authoringContext {uri, position}`: when `position` falls inside a container/fragment, the bundle gains `insertionTarget: {containerName, dialect ("ttrp"|"sql"|"pandas"|"ttrb"), targetEngine, inPorts (with schemas), defaultOut, inScopeColumns}`. **The host declares the insertion target by sending the position — never a heuristic** (P2; C4-d-i γ: "assist inserts in the dialect of the container it's pointed at — including TTR-SQL/TTR-pandas, not just TTR-B"). Outside any container → `insertionTarget.dialect = "ttrp"` (program scope, C4-d-i α primary).
- [ ] The `grammarSummary` section narrows to the insertion dialect's roster + shared expression grammar when an `insertionTarget` is present (smaller prompt; full summary retained at program scope).
- [ ] Extend `AuthoringContextCompletenessSpec` with four cursor placements against `hero-embedded.ttrp`-style fixtures: program scope · inside a `"""sql` container · inside a `"""ttrb` container · inside a bare `.ttrb` document (dialect = ttrb, derived container).
- [ ] Make T7.2.1's specs fully GREEN; commit the golden bundle.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.assist.*"` — GREEN including golden snapshot.

### T7.2.4 · Eval corpus — format + seed entries

- [ ] Corpus home: `packages/kotlin/ttrp-conform/src/test/eval/` (`ttrp-conform`-adjacent per C4-e/architecture §9; NOT inside the conform runtime suites — eval never needs engines). Layout: `corpus/*.yaml` + `fixtures/*.{ttrp,ttrb,ttr.sql}`.
- [ ] Entry format (versioned; the schema itself is a fixture):

  ```yaml
  # corpus/eval-001.yaml
  id: eval-001
  prompt: "Load the sales CSV, keep only rows with a positive amount, and total amount by region."
  insertionTarget: null            # null = program scope; else {fixture, container} — the host-declared target (C4-d-i γ)
  expected: fixtures/eval-001-expected.ttrp
  tolerance:
    ssaNames: ignore               # label differences never fail shape
    extraCalcNodes: deny           # allow|deny interposed Calc/Project nodes
    sugarEquivalence: normalize    # compare AFTER T8 sugar expansion (Select/Calc/Distinct…)
  ```

- [ ] Seed 10 entries covering the C4 surface area (prompts fixed here; write the expected fixtures to match): 1 hero full-program (the A5 scenario, canonical expected) · 2 island-scoped filter/aggregate requests at program scope · 2 with `insertionTarget` into a `"""sql` container (expected fixture = the container with new SQL content) · 1 into a `"""ttrb` container · 1 into a bare `.ttrb` (expected = sentences) · 1 join-with-error-path · 1 negative-shaped ("update the accounts table…" — expected fixture uses Store; documents that assist output must survive validate, C4-d rejects live in the repair loop not the corpus) · 1 er-flavored (relation-join via the binding tier, D-a).
- [ ] Corpus lint spec `EvalCorpusSpec` (Kotest, in `ttrp-conform`): every YAML parses against the schema; every `expected` fixture **compiles clean through the front-half** (`ttrp/validate` equivalent, zero diagnostics); every `insertionTarget` resolves; ids unique.

**Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "*EvalCorpusSpec"` — GREEN.

### T7.2.5 · Graph-shape comparator + eval runner (deterministic half)

- [ ] Comparator in `ttrp-conform`: compile candidate + expected through the front-half → **normalized graphs** (post-T8 per `sugarEquivalence`) → compare node-kind multiset, edge structure (port-to-port, anonymized labels per `ssaNames: ignore`), and expression trees; apply `tolerance` knobs. Output: `pass | shape-mismatch(diff) | invalid(diagnostics)`.
- [ ] Runner: `ttrp conform eval --corpus <dir> --candidates <dir> --report <file>` (extends the S2/S3 CLI surface; conform-adjacent, same module). `--candidates` = one file per corpus id (`eval-001.ttrp` …) **produced by a host** — the toolchain NEVER generates candidates (no LLM in the compiler, P2/C4-d-ii; the runner only scores). Report = the T7.2.7 results-file schema, minus the host metadata block.
- [ ] `EvalRunnerSpec`: three hand-written candidate sets — one passing, one shape-mismatch (extra node), one invalid (out-of-roster TTR-B) — assert the three verdicts and that `invalid` carries the named diagnostics + suggestions (the repair vocabulary round-trips).

**Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "*EvalRunnerSpec"` GREEN; then `./gradlew :packages:kotlin:ttrp-cli:run --args="conform eval --corpus …/eval/corpus --candidates …/eval/testdata/passing --report /tmp/eval-report.yaml"` exits 0.

### T7.2.6 · Reference host demo — VS Code `ttrp.assist.generate`

The demo host lives ENTIRELY in the VS Code extension (TS); the Kotlin toolchain contributes only `ttrp/authoringContext` + `ttrp/validate` (C4-d-ii = γ: "no model dependency, no secrets in editor infrastructure").

- [ ] Command `ttrp.assist.generate` (palette title **"TTR-P: Generate with Assist"**), flow:
  1. Capture active editor + cursor position — **this is the host declaring the insertion target** (C4-d-i γ).
  2. `InputBox` for the NL request.
  3. Request `ttrp/authoringContext {uri, position}`.
  4. Build the prompt: rendered bundle + request + (on retries) prior diagnostics with their suggestions (the repair vocabulary).
  5. Call the user's model: endpoint/model from settings `ttrp.assist.endpoint` + `ttrp.assist.model`; **API key from VS Code `SecretStorage`** (command `ttrp.assist.setApiKey` to store it; never settings.json, never sent to the LSP).
  6. `ttrp/validate {source, dialect: insertionTarget.dialect}` on the candidate.
  7. Diagnostics → repair loop: re-prompt with diagnostics+suggestions, max `ttrp.assist.maxRepairs` (default 3).
  8. Exit gate (C4-d-iii): candidate parses + typechecks + capability-checks, **or nothing is presented** — on exhaustion show the diagnostics, offer no edit.
  9. On pass: present via `vscode.diff` (current vs proposed) + explicit modal **Apply / Discard**; only Apply calls `workspace.applyEdit`.
- [ ] **ACCEPTANCE (C4-d-iii, non-negotiable): generated text is NEVER applied silently** — verified by an integration test asserting no `applyEdit` occurs before the user confirmation resolves, and manually in the Extension Development Host. No mandatory provenance header; honor `[ttrp] assist-provenance = none|comment` (default `none` — the git diff is the review artifact).
- [ ] Candidate-dump mode for T7.2.7: setting `ttrp.assist.dumpCandidatesDir` — when set, every ACCEPTED candidate is also written as `<corpus-id>.ttrp` (id from an optional prompt prefix `eval-001:`). Zero coupling to the corpus format beyond the filename.
- [ ] Integration test (extension test host or the P4 paired-connection pattern): stub model provider (`ttrp.assist.endpoint = mock:` → canned responses, no network) exercising the full loop: first candidate invalid (returns a `TTRP-B-001` text), repaired candidate valid, diff presented, programmatic "confirm" applies. Keeps CI key-free.

**Verify:** `pnpm --filter @tatrman/vscode-ext test` (extension suite incl. the mock-model loop test) GREEN; manual: F5 Extension Development Host → set a real key via `ttrp.assist.setApiKey` → run the command on the hero → diff shown → Apply inserts validated text. Record the manual check in `progress-phase-07.md`.

### T7.2.7 · Baseline eval run — versioned results file

- [ ] Produce candidates for all 10 corpus entries via the T7.2.6 host (real model, user-supplied key, `dumpCandidatesDir` pointed at a working dir). This step is **manual and off-CI** (a key is required; the toolchain stays model-free).
- [ ] Score: `ttrp conform eval --corpus … --candidates <dump dir> --report baselines/001/report.yaml`.
- [ ] Commit the versioned baseline under `packages/kotlin/ttrp-conform/src/test/eval/baselines/001/`:
  - `report.yaml` — schema:

    ```yaml
    baseline: 1
    date: <run date>
    host: "vscode-ext ttrp.assist.generate"
    model: "<model id as configured>"        # host metadata; the toolchain never saw the key
    toolchain: "org.tatrman:ttrp:<version>"
    corpusVersion: 1
    entries:
      - {id: eval-001, verdict: pass, repairs: 0}
      - {id: eval-002, verdict: shape-mismatch, repairs: 2, note: "extra Project"}
      # … all 10
    summary: {pass: <n>, shapeMismatch: <n>, invalid: <n>}
    ```

  - `candidates/*.ttrp|.ttrb` — the exact scored candidates, so the report is **deterministically reproducible** by re-running the scorer without any model.
- [ ] `EvalBaselineSpec` in `ttrp-conform`: re-scores `baselines/001/candidates/` against the corpus and asserts the committed `report.yaml` verdicts — the baseline becomes a regression pin (CI-safe: no LLM, pure scorer).
- [ ] Update `progress-phase-07.md`: baseline numbers, model used, date — and mark plan P7 DONE ("eval corpus baseline recorded; v1 complete") for the `/review` cadence to verify.

**Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "*EvalBaselineSpec"` GREEN; `git log --stat` shows `baselines/001/` committed with report + candidates.

## Definition of DONE (stage)

- [ ] `ttrp/authoringContext` content-complete: all contracts §7 sections present, golden-snapshotted, diagnostics catalogue assembled from the versioned fixture tables with 100% id coverage + suggestions.
- [ ] Cursor-scoped insertion works for all four scopes (program, sql, ttrb, bare-ttrb); host-declared, no heuristics (P2).
- [ ] Reference host demo: `ttrp.assist.generate` runs generate→validate→repair end-to-end with a user key; **generated text never applied silently** (C4-d-iii) — test-asserted and manually confirmed.
- [ ] Eval corpus (10 entries) lint-green; runner scores deterministically; **baseline 001 committed** (report + candidates) and pinned by `EvalBaselineSpec`.
- [ ] No model dependency, key, or endpoint anywhere in `packages/kotlin/*` (grep `apiKey|endpoint|anthropic|openai` over the Kotlin modules = clean).
- [ ] **v1 complete per plan P7 DONE.**

## Blockers

*(record here — STOP on: `ttrp/authoringContext`/`ttrp/validate` absent or schema-divergent from P4, P6 reject tables missing/format-divergent, no model key available for the baseline run (everything up to T7.2.6's mock-tested demo can still land; only T7.2.7's generation step waits))*

## References

- **Primary:** `../../design/12-nl-options.md` (C4-d, C4-e, §Leftovers: context-bundle format, eval-corpus home — both closed by this stage) · `../../design/00-control-room.md` decisions C4-d-i/ii/iii, C4-e (Q1 resolved), S8, S2/S3.
- `../../architecture/contracts.md` §4 (`ttrp/authoringContext` + `ttrp/validate` rows), §7 (bundle content list), §8 (diagnostics/repair vocabulary), §2 (`assist-provenance` knob).
- `../../architecture/architecture.md` §2 (assist layer: contracts only, LLM at host), §9 (assist/agent eval corpus, diagnostics tables as fixtures).
- Stage 7.1 fixtures: `ttrb-reject-table.yaml`, `ttrb-verbose-synonyms.yaml` (consumed, not modified).
- `plan.md` Phase 4 Stage 4.2 (authoringContext v1 — per-stage list absent at drafting; code is source), Phase 7 Stage 7.2.
- **Future agent host (reference only):** JetBrains **Koog** clone at `~/Dev/view-only/koog` (graphify-out available) — the likely library for a kantheon-side agent host consuming the same two contracts (C4-e). The v1 demo host does **NOT** depend on it; noted for the post-v1 agent-authoring arc.
- Contradiction log (drafting): older docs use `ttrp/assistContext` (pre-S8) and `[pl] assist-provenance` (pre-S5 `[ttrp]`) — contracts.md spellings win.
