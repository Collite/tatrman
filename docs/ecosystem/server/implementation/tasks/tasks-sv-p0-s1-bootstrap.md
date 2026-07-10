# SV-P0 · S1 — `tatrman-server` repo bootstrap

> New repo, born Apache-2.0, empty of services (S3 fills it). Interim GitHub home: `Collite/tatrman-server` (private until SV-P6). Model everything on kantheon's build conventions (same Gradle version, same catalog style) — divergence is a bug.

- [x] **T1 — Create the repo.** `Collite/tatrman-server` on GitHub (private, default branch `master` to match the family). Local clone beside the others (`~/Dev/collite-gh/tatrman-server`). Commit 1: `README.md` (one paragraph: "Tatrman Server — the open runtime of the Tatrman ecosystem; see docs/ecosystem/server/ in the tatrman repo"), `.gitignore` (copy kantheon's), `LICENSE` = full Apache-2.0 text, `NOTICE` = `Tatrman — Copyright 2026 Collite` (RO-18).
- [x] **T2 — Gradle skeleton.** Copy from kantheon: `gradlew`/`gradlew.bat`/`gradle/wrapper` (same Gradle version), `gradle/libs.versions.toml` (verbatim start — prune later, never fork versions), `gradle.properties`, root `build.gradle.kts` and `settings.gradle.kts` with `rootProject.name = "tatrman-server"`, the same `dependencyResolutionManagement` block (mavenCentral + mavenLocal-TEMPORARY + the `Collite/tatrman` GitHub Packages repo with `includeGroup("org.tatrman")`). Group for all modules: `org.tatrman`. No modules included yet.
- [x] **T3 — Header policy.** Add `docs/HEADERS.md`: every new source file starts with `// SPDX-License-Identifier: Apache-2.0` + `// Copyright 2026 Collite` (RO-18); moved files get headers in SV-P2's sweep, not in SV-P0 (keep the move diff pure).
- [x] **T4 — Dependency-rule test FIRST (TDD).** Add `scripts/check-dependency-rules.sh` implementing contracts §7, and wire it as CI job `dependency-rules` before it can pass trivially:
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  fail=0
  # 1. no cz.tatrman anywhere in build files
  grep -rn --include='*.gradle.kts' --include='*.toml' 'cz\.tatrman' . && fail=1
  # 2. no kantheon module/artifact deps
  grep -rn --include='*.gradle.kts' -E 'project\(":(agents|frontends)' . && fail=1
  grep -rn --include='*.toml' --include='*.gradle.kts' 'org\.tatrman\.kantheon' . && fail=1
  # 3. no proto imports from the kantheon namespace
  grep -rn --include='*.proto' 'org/tatrman/kantheon/' shared/ services/ workers/ tools/ infra/ 2>/dev/null && fail=1
  exit $fail
  ```
  Run it; it must PASS on the empty skeleton (nothing to violate yet) — its real bite comes in S4's verify.
- [x] **T5 — CI skeleton.** `.github/workflows/ci.yml` cloned from kantheon's shape: JDK 21 setup, `./gradlew build`, plus the `dependency-rules` job (T4) and a `grep-gate` job running the ledger §5 regex (see S6 T5 for the exact command; wire it now, allow-empty until S3).
- [x] **T6 — Smoke module.** `tools/_smoke-test` (copy kantheon's pattern): one Kotlin file + one Kotest test asserting true, proving toolchain + catalog + CI wiring. `./gradlew :tools:_smoke-test:test` green locally and in CI.
- [ ] **T7 — Record.** Commit series pushed; repo link + CI run link recorded in the findings section below; check the S1 row in `00-task-management.md`.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman-server
./gradlew build                       # green, includes smoke test
bash scripts/check-dependency-rules.sh && echo RULES-OK
gh run list --limit 1                 # CI green on the pushed commit (or check Actions tab)
```

## Findings / ⚑

- **T1–T6 done locally, all green.** `./gradlew build` BUILD SUCCESSFUL (Gradle 9.3.0,
  JDK 21; smoke test + ktlint pass); `bash scripts/check-dependency-rules.sh` → RULES-OK.
  Commit series (4 commits) on branch `master`:
  `5ab0ed7` identity · `ed37a22` gradle skeleton · `dde763d` dep-rule + CI · `b542314` smoke.
- **Divergences from a verbatim kantheon copy (all justified, recorded):**
  - Root `build.gradle.kts`: dropped the Hebe subtree convention block and the `detekt`
    plugin alias (no `agents/` in the server repo; the block references
    `agents/hebe/config/detekt/*` that will never exist). Kept the main subprojects
    convention (kotlin toolchain 21, ktlint, component/integration test tiers + the
    unit-classpath isolation guard) so S3-moved modules "just work". `group = "org.tatrman"`.
  - `libs.versions.toml` copied **verbatim** (per T2 "prune later, never fork"); it still
    carries kantheon-only pins (Hebe, Spring/Prometheus, Kleio) — pruned in a later phase.
  - `.gitignore` copied verbatim (carries kantheon-specific ignore lines — harmless).
  - CI `ci.yml` is the slimmed shape (JDK 21 + `./gradlew build` + `dependency-rules` +
    persona `grep-gate`); it does NOT clone kantheon's `just`/python/iris/sysifos/helm jobs
    (none of that exists here yet).
  - Smoke module is the minimal form (one fn + one Kotest spec asserting true), not
    kantheon's Ktor `/health` app — T6 explicitly allows "one Kotlin file + one test".
- ⚑ **T7 BLOCKED — no write access to push.** `git push origin master` →
  `Permission to Collite/tatrman-server.git denied to BoraPerusic`. `gh repo view` reports
  `viewerPermission: READ` for the authenticated account. This is the OQ-9/RO-17
  account-recovery situation (the `tatrman`/Collite account is Bora's, recovery pending).
  **Action for Bora:** grant the working account write on `Collite/tatrman-server` (or push
  from an account that has it), then `git push -u origin master` from
  `~/Dev/collite-gh/tatrman-server` and record the repo + CI-run links here. All commits
  are staged locally and ready; nothing else in S1 is outstanding.
