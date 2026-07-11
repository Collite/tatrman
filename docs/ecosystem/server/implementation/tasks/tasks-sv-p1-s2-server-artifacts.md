# SV-P1 · S2 — Gate 3a: tatrman-server library artifacts (0.9.0 to the registry)

> Repo: **tatrman-server** (publisher) → **kantheon** (consumer). Pre-flight: S1 done (server builds against tatrman 0.9.0). Publishes the moved shared libs + proto stubs that kantheon consumes — today `0.0.1-LOCAL` via `mavenLocal()` (the S5-enabler set, commit `ad6bad2`): `otel-config`, `logging-config`, `ktor-configurator`, `db-common`, `data-formatter`, `fuzzy-common`, `whois-common`, `keycloak-auth`, `ttr-meta-client`, `ttr-llm-client`, `capabilities-client`, `component-testkit`, `integration-harness`, `shared/proto` stubs — audit the exact set in T1, the toml is ground truth. This retires the review-input ⚑5 clean-machine wrinkle.

- [ ] **T1 — Audit the artifact set.** From kantheon `gradle/libs.versions.toml`, list every module pinned to `version.ref = "tatrman-server"` (= `0.0.1-LOCAL`). Cross-check each has a `maven-publish` block in tatrman-server (the S5-enabler wired `publishToMavenLocal`; confirm group `org.tatrman`, artifact names functional, no persona). Record the definitive set + coordinates in findings.
- [ ] **T2 — Publish workflow (TDD: dry-run first).** Add `.github/workflows/publish.yml` to tatrman-server (clone the tatrman shape): tag `server-libs/v<x.y.z>` publishes the whole T1 set to GH Packages `Collite/tatrman-server` (staging registry per RO-17; `GITHUB_TOKEN` suffices in Actions). First run the workflow's Gradle line locally with `-Pversion=0.0.2-LOCAL publishToMavenLocal` as the dry-run, inspect one POM (license block = Apache-2.0, no kantheon deps — contracts §7 arrow rule), then commit the workflow.
- [ ] **T3 — Persona + dependency-rule gate on the publish path.** The publish workflow's first job = the existing enforcing grep gate + the P2/RO-6 dependency-rules check (both already in `ci.yml` — reuse, don't duplicate: make publish.yml `needs:` a called workflow or re-run the two steps). A publish with a red gate must be impossible by construction.
- [ ] **T4 — Cut `server-libs/v0.9.0`. [GATE]** Preconditions: T2 dry-run inspected · T3 gates green · tag from folded `master`. Verify all packages appear under `Collite/tatrman-server` Packages with version 0.9.0.
- [ ] **T5 — kantheon repoints; mavenLocal retired.** kantheon `settings.gradle.kts`: add the `Collite/tatrman-server` Maven feed next to the existing `Collite/tatrman` one (same `gpr.user`/`gpr.token` mechanism, `includeGroup("org.tatrman")`); `libs.versions.toml`: `tatrman-server = "0.9.0"`; **remove the `mavenLocal()` entry** (or scope it out of `org.tatrman`) so a clean machine builds from registries alone. Branch `sv-p1-server-artifacts`.
- [ ] **T6 — Clean-machine proof (the ⚑5 test, executed).** `./gradlew --stop && rm -rf ~/.gradle/caches/modules-2/files-2.1/org.tatrman && cd kantheon && ./gradlew build -x test --refresh-dependencies` — green with no prior tatrman-server `publishToMavenLocal`. Also delete the clean-machine caveat from kantheon's README/CLAUDE.md (it rides S3·T6's prose sweep if easier — then note the handover here).
- [ ] **T7 — Findings + register.** Plan §SV-P1 gate 3 row: library half done (staging). Record the coordinate list; note anything that turned out kantheon-private and did NOT need publishing (trim the set rather than publish speculatively — every published artifact is a maintenance promise).

**Verify block:**
```bash
git -C ~/Dev/collite-gh/tatrman-server tag | grep server-libs/v0.9.0
grep -n "mavenLocal" ~/Dev/collite-gh/kantheon/settings.gradle.kts   # nothing org.tatrman-scoped
grep -n 'tatrman-server *= *"0.9.0"' ~/Dev/collite-gh/kantheon/gradle/libs.versions.toml
# clean-machine build per T6 (run verbatim, record runtime in findings)
```

## Findings / ⚑

*(T1 coordinate table · T6 proof run · trims)*
