# SV-P0 · S1 — `tatrman-server` repo bootstrap

> New repo, born Apache-2.0, empty of services (S3 fills it). Interim GitHub home: `Collite/tatrman-server` (private until SV-P6). Model everything on kantheon's build conventions (same Gradle version, same catalog style) — divergence is a bug.

- [ ] **T1 — Create the repo.** `Collite/tatrman-server` on GitHub (private, default branch `master` to match the family). Local clone beside the others (`~/Dev/collite-gh/tatrman-server`). Commit 1: `README.md` (one paragraph: "Tatrman Server — the open runtime of the Tatrman ecosystem; see docs/server/ in the tatrman repo"), `.gitignore` (copy kantheon's), `LICENSE` = full Apache-2.0 text, `NOTICE` = `Tatrman — Copyright 2026 Collite` (RO-18).
- [ ] **T2 — Gradle skeleton.** Copy from kantheon: `gradlew`/`gradlew.bat`/`gradle/wrapper` (same Gradle version), `gradle/libs.versions.toml` (verbatim start — prune later, never fork versions), `gradle.properties`, root `build.gradle.kts` and `settings.gradle.kts` with `rootProject.name = "tatrman-server"`, the same `dependencyResolutionManagement` block (mavenCentral + mavenLocal-TEMPORARY + the `Collite/tatrman` GitHub Packages repo with `includeGroup("org.tatrman")`). Group for all modules: `org.tatrman`. No modules included yet.
- [ ] **T3 — Header policy.** Add `docs/HEADERS.md`: every new source file starts with `// SPDX-License-Identifier: Apache-2.0` + `// Copyright 2026 Collite` (RO-18); moved files get headers in SV-P2's sweep, not in SV-P0 (keep the move diff pure).
- [ ] **T4 — Dependency-rule test FIRST (TDD).** Add `scripts/check-dependency-rules.sh` implementing contracts §7, and wire it as CI job `dependency-rules` before it can pass trivially:
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
- [ ] **T5 — CI skeleton.** `.github/workflows/ci.yml` cloned from kantheon's shape: JDK 21 setup, `./gradlew build`, plus the `dependency-rules` job (T4) and a `grep-gate` job running the ledger §5 regex (see S6 T5 for the exact command; wire it now, allow-empty until S3).
- [ ] **T6 — Smoke module.** `tools/_smoke-test` (copy kantheon's pattern): one Kotlin file + one Kotest test asserting true, proving toolchain + catalog + CI wiring. `./gradlew :tools:_smoke-test:test` green locally and in CI.
- [ ] **T7 — Record.** Commit series pushed; repo link + CI run link recorded in the findings section below; check the S1 row in `00-task-management.md`.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman-server
./gradlew build                       # green, includes smoke test
bash scripts/check-dependency-rules.sh && echo RULES-OK
gh run list --limit 1                 # CI green on the pushed commit (or check Actions tab)
```

## Findings / ⚑
_(record anything unexpected here)_
