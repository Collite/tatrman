# Tasks · P3 · Stage 3.3 — Bundle + executor (`<program>.bundle/`, run.sh, `ttrp` CLI)

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`ttrp build` assembles the full `<program>.bundle/` **exactly per contracts §5** — `run.sh` + `manifest.json` + `islands/` + `transfers/` + `schemas/` + `plans/` (Kantheon worlds only), sha256 per file, **semantic world fingerprint** (F-f-ii β) — and `run.sh` implements the F-lite executor: wave parallelism (`&` + pid-checked `wait`, `wait -n` early abort), `set -euo pipefail`, exit contract 0/1/2, `TTR_CONN_*` pre-flight, wipe of `logs/ staging/ out/` on restart (F-e α), display file drops `out/<name>.<fmt>`. The `ttrp` CLI binary (S2, clikt) wires `build`/`run`/`explain`/`conform`. **DONE bar:** `ttrp build hero.ttrp` produces a bundle whose tree, manifest fields, hashes, and run.sh content all pass the assertion suite — **all verified by content-asserting unit tests, never by executing against live engines** (a stubbed-PATH smoke run and `bash -n` are the ceiling; live execution is Stage 3.4's dockerized-PG CI job only).

## Pre-flight (all must pass before T3.3.1)

- [x] Stages 3.1 + 3.2 DONE: `./gradlew :packages:kotlin:ttrp-emit:test` → BUILD SUCCESSFUL (island + transfer emitters are this stage's inputs).
- [x] `./gradlew :packages:kotlin:ttrp-cli:build :packages:kotlin:ttrp-conform:build` → BUILD SUCCESSFUL (Phase 0 skeletons present).
- [x] `bash --version` ≥ 4 available on the dev machine (`wait -n` requires bash ≥ 4.3) — record the found version in the progress doc; run.sh must declare `#!/usr/bin/env bash` and check `BASH_VERSINFO` in pre-flight.
- [x] `grep -n "manifest.json" docs/ttr-p/architecture/contracts.md` → §5 present; re-read §5 in full before T3.3.1 (it is the normative spec for this stage).

## Tasks

### T3.3.1 · `RunManifest` model — contracts §5 fields verbatim (TEST-FIRST)

- [x] Write `packages/kotlin/ttrp-cli/src/test/kotlin/org/tatrman/ttrp/bundle/RunManifestTest.kt` FIRST: serializes a fully-populated manifest and asserts the JSON contains **exactly** the contracts §5 keys — `ttrpVersion` (int, = 1), `toolchain` (`"org.tatrman:ttrp:<semver>"`), `program` (filename), `world` (`{qname, fingerprint}` with fingerprint matching `^sha256:[0-9a-f]{64}$`), `islands[]` (`{name, engine, executor, invocation, file, sha256}`; `invocation` ∈ {`psql`, `python3`}), `transfers[]` (`{from, to, via, file, sha256}`), `waves[][]` (array of arrays of island names), `connections[]` (`TTR_CONN_*` names), `displays[]` (`{name, file}` with `file` = `out/<name>.<fmt>`), `files{}` (path → `sha256:...`). Round-trip decode test. Unknown-key rejection test (strict decoding — the manifest is a contract, not a grab bag).
- [x] Implement `org.tatrman.ttrp.bundle.RunManifest` (kotlinx-serialization `@Serializable` data classes, module `ttrp-cli` — or `ttrp-emit` if Phase 0 placed bundle assembly there; wherever it lands, record the module in this file). JSON output: pretty-printed, stable key order (the manifest is committed/diffed by users).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test --tests "org.tatrman.ttrp.bundle.RunManifestTest"` → green.

### T3.3.2 · Semantic world fingerprint (F-f-ii β) — canonicalization mini-spec + tests

The fingerprint is a **semantic hash of the resolved world model** — comment/formatting-immune; two worlds are fingerprint-equal iff they mean the same. Canonicalization spec (implement exactly; this block is the mini-spec of record until promoted into contracts):

1. Input = the **resolved** world (post `extends`-overlay, Stage 2.2 output) — never the source text.
2. Build a canonical JSON document: top-level `{qname, engines[], executors[], storages[]}`.
3. `engines[]` / `executors[]` / `storages[]` sorted by `name` (codepoint order). Each entry: `name`, `type`, `version`, plus its **capability-manifest content** as sorted key→value pairs (node support, function support, control vocabulary, interpreter+packages for executors), storage extras: `kind`, `staging` (bool), `rls` (bool), `hosts` (sorted list of model package qnames), connection **name** (never credentials — the fingerprint must be computable from the secret-free world doc).
4. **Excluded:** trivia/comments, source locations, doc order, `.ttrm` formatting, anything presentation-shaped.
5. Serialize with compact separators (no whitespace), keys sorted lexicographically at every object level, UTF-8 encode, `sha256` over the bytes, render `sha256:<hex>`.

- [x] Write `WorldFingerprintTest.kt` FIRST: (a) fixture world → known stable hash (golden constant in the test); (b) **comment-reflow immunity** — two `.ttrm` world sources differing only in comments/whitespace/def order fingerprint identically; (c) **semantic sensitivity** — bumping an engine version, flipping `staging`, adding a hosted package each change the hash; (d) credentials absence — a world with a connection URI configured elsewhere yields the same hash.
- [x] Implement `org.tatrman.ttrp.bundle.WorldFingerprint` (`fun of(world: ResolvedWorld): String`). Unresolvable element during canonicalization → `TTRP-WLD-001` diagnostic (internal; means Stage 2.2 let something through).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test --tests "org.tatrman.ttrp.bundle.WorldFingerprintTest"` → green.

### T3.3.3 · `RunShGenerator` — bash-content unit tests, NO live execution (TEST-FIRST)

- [x] Write `RunShGeneratorTest.kt` FIRST, asserting on the **generated text** of run.sh for a 3-island/2-wave fixture (waves `[["a","b"],["c"]]`, connections `TTR_CONN_ERP_PG`, `TTR_CONN_FILES`):
  - Header: `#!/usr/bin/env bash` then `set -euo pipefail` (first non-comment statement).
  - **Pre-flight block, in order:** bash-version guard (`BASH_VERSINFO[0]` ≥ 4 with minor check for 4.3/`wait -n`, else `exit 2`); for each manifest connection `[[ -z "${TTR_CONN_ERP_PG:-}" ]] && { echo "missing TTR_CONN_ERP_PG" >&2; exit 2; }` (exit **2** = pre-flight failure; env/connection checks ONLY — no fingerprint re-derivation in bash, F-f-ii record/verify split).
  - **Wipe-on-restart (F-e α):** `rm -rf logs staging out && mkdir -p logs staging out` before wave 1.
  - **Per wave:** each island launched with `&`, stdout+stderr to `logs/<island>.log`, pid captured into an array; then a **`wait -n` loop** that on first nonzero exit kills remaining sibling pids (`kill … 2>/dev/null || true` + reap) and exits **1** with a failure summary line `FAILED island=<name> exit=<code> log=logs/<name>.log`.
  - Invocations exactly per F-c: `psql "$TTR_CONN_ERP_PG" -v ON_ERROR_STOP=1 --no-psqlrc -f islands/<name>.sql` · `python3 islands/<name>.py` · transfers `python3 transfers/<name>.py` (transfers run in their own wave slots per the Stage 2.3 wave computation — assert placement matches the manifest `waves`).
  - Display notice: after the final wave, one `echo "display <name>: out/<name>.<fmt>"` per manifest display.
  - Final `exit 0`.
  - `bash -n <generated file>` passes (syntax check — offline, allowed).
- [x] Implement `org.tatrman.ttrp.bundle.RunShGenerator` (pure function: manifest + bindings → script text; deterministic).
- [x] Optional-but-cheap stub smoke test (still no live engines): run the generated script in a temp dir with a PATH containing stub `psql`/`python3` scripts that log-and-exit-0 / exit-3; assert exit 0 on the happy path, exit 1 + sibling-kill on the failure path, exit 2 with an unset connection. Tag it `EnabledIf(bashAvailable)`.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test --tests "org.tatrman.ttrp.bundle.RunShGeneratorTest"` → green.

### T3.3.4 · `BundleAssembler` — tree, sha256, manifest tie-out

- [x] Implement `org.tatrman.ttrp.bundle.BundleAssembler`: takes the Phase-2 execution graph + Stage 3.1/3.2 emit results + resolved world → writes `<program>.bundle/` (S1) with exactly:
  ```
  <program>.bundle/
  ├── run.sh                  # T3.3.3, chmod +x
  ├── manifest.json           # T3.3.1
  ├── islands/<name>.{sql,py} # SSA/CTE names preserved (Stage 3.1/3.2 outputs, byte-identical)
  ├── transfers/<name>.py
  ├── schemas/*.json          # per staging boundary — see below
  └── plans/*.pb              # ONLY when the world's execution target is Kantheon (E-a world-driven)
  ```
  `logs/`/`staging/`/`out/` are **never** created at build time (runtime-created, wiped on restart — F-e).
- [x] sha256: every written file hashed; `islands[].sha256` / `transfers[].sha256` + the complete `files{}` map (relative path → `sha256:<hex>`, including `run.sh` and `schemas/*`; `manifest.json` itself excluded from `files{}` — it can't contain its own hash; note this exclusion in the manifest KDoc and propose it as a contracts §5 clarification line).
- [x] `schemas/*.json`: one per staging boundary — Arrow schema JSON (field name, Arrow type, nullability) **plus** a `fingerprint` field (Q9-1's comparison key). Note: contracts §5 calls these "Arrow schema fingerprints", F-f calls them "declared/staging schemas" — emit BOTH (full schema + fingerprint) and flag the wording for contracts consolidation.
- [x] `plans/` gating test: hero world (bash executor) ⇒ no `plans/` dir; a Kantheon-target fixture world ⇒ `plans/<island>.pb` present via ttr-translator's plan.v1 path (E-a; if the translator's plan emission API is not yet published, assert the gating logic + record a §Blockers entry scoped to the `.pb` write only — do NOT block the rest of the stage).
- [x] Assembly determinism: building twice → identical `files{}` hashes.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test --tests "org.tatrman.ttrp.bundle.BundleAssemblerTest"` → green (tree assertions on a temp dir; `find`-style listing compared to expected).

### T3.3.5 · `ttrp` CLI (S2) — clikt wiring for build/run/explain/conform

- [x] Add clikt to the version catalog: `gradle/libs.versions.toml` → `clikt = { module = "com.github.ajalt.clikt:clikt", version = "<current 5.x>" }`; `implementation(libs.clikt)` in `ttrp-cli`. (Clikt 5: `main` is an extension — `import com.github.ajalt.clikt.core.main`.)
- [x] Implement in `org.tatrman.ttrp.cli`:
  ```kotlin
  class TtrpCommand : CliktCommand(name = "ttrp") { override fun run() = Unit }

  fun main(args: Array<String>) = TtrpCommand()
      .subcommands(BuildCommand(), RunCommand(), ExplainCommand(), ConformCommand())
      .main(args)
  ```
  - `BuildCommand` (`ttrp build <file>.ttrp [--out <dir>]`) → front-half + graph + emit + `BundleAssembler`; prints the bundle path; process exit ≠ 0 on any diagnostic of error severity.
  - `RunCommand` (`ttrp run <file>.ttrp | <program>.bundle`) → builds if given source, then executes `bash run.sh` in the bundle dir via `ProcessBuilder`, **propagating the child's exit code verbatim** (0/1/2 contract surfaces unchanged) and streaming stdout/stderr.
  - `ExplainCommand` → delegates to the Stage 2.3 explain rendering (S4) — wiring only, no new logic.
  - `ConformCommand` → delegates to `ttrp-conform` (Stage 3.4; until then prints `conform: not yet implemented` and exits 3 — a distinct code outside the run contract, documented in `--help`).
- [x] Gradle `application` plugin on `ttrp-cli` (`mainClass = "org.tatrman.ttrp.cli.MainKt"`); `./gradlew :packages:kotlin:ttrp-cli:installDist` produces a runnable `ttrp` launcher.
- [x] CLI tests (Kotest + clikt's `test()` helper): `--help` lists the four subcommands; `build` on a fixture writes a bundle; `run` on a bundle with a stubbed run.sh (exit 2) propagates exit 2.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test` green AND `./gradlew :packages:kotlin:ttrp-cli:installDist && packages/kotlin/ttrp-cli/build/install/ttrp-cli/bin/ttrp-cli --help` prints the subcommand roster.

### T3.3.6 · Hero bundle end-to-end assembly (offline)

- [x] Integration-shaped test `HeroBundleTest`: `ttrp build hero.ttrp` (through the CLI command class, not a shell) into a temp dir; assert: bundle tree exactly matches T3.3.4's layout; manifest `waves` matches Stage 2.3's `ttrp explain` structure for the hero (SQL-prep + Polars-prep co-waved, crunch after — F-a β); `connections` lists the hero world's `TTR_CONN_*` names; `displays[0].file == "out/main_result.arrow"` (display-default `arrow`, contracts §2); every `files{}` hash re-verifies against disk; `bash -n run.sh` passes; island files byte-identical to the Stage 3.1/3.2 goldens.
- [x] `./gradlew :packages:kotlin:ttrp-cli:ktlintCheck` clean.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test --tests "*HeroBundleTest"` → green. **No live PG/Python data path was touched.**

## Definition of DONE (stage)

- [x] `manifest.json` fields verbatim per contracts §5, strict-decoded, round-trip tested; sha256 per file; manifest-self-hash exclusion flagged for contracts changelog.
- [x] Semantic world fingerprint implemented to the T3.3.2 mini-spec with comment-immunity + sensitivity tests (F-f-ii β; record-vs-verify split respected — bash pre-flight checks env only).
- [x] run.sh content-asserted: `set -euo pipefail`, wave `&`/pid-`wait`, `wait -n` early abort with sibling kill, exit 0/1/2, `TTR_CONN_*` pre-flight, wipe of `logs/ staging/ out/` on restart, display drops + notices. Zero live-engine execution in this stage.
- [x] `ttrp build|run|explain|conform` wired via clikt; `run` propagates bundle exit codes verbatim.
- [x] Hero bundle assembles end-to-end offline; island payloads byte-identical to Stage 3.1/3.2 goldens.
- [x] Progress recorded in `progress-phase-03.md`.

## Blockers

_(empty)_

## References

- `../../architecture/contracts.md` §5 (bundle — NORMATIVE for this stage), §2 (`display-default`), §6 (invocation bindings).
- `../../design/08-orchestration-options.md` — F-a β (waves), F-c (bindings, `TTR_CONN_*`), F-d (failure semantics, exit contract), F-e α (wipe-on-restart), F-f (bundle, JSON manifest, semantic fingerprint).
- `../../design/00-control-room.md` — S1 (bundle dir), S2 (`ttrp` CLI), F-a/F-c/F-d/F-e/F-f entries, E-a (plans/ gating).
- Clikt 5.x: `com.github.ajalt.clikt:clikt`; `CliktCommand`, `.subcommands(...)`, extension `main` from `com.github.ajalt.clikt.core.main`.
- CLAUDE.md — commit style, version-catalog conventions, Kotest.
