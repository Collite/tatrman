# SV-P1 · S2 — Gate 3a: tatrman-server library artifacts (0.9.0 to the registry)

> Repo: **tatrman-server** (publisher) → **kantheon** (consumer). Pre-flight: S1 done (server builds against tatrman 0.9.0). Publishes the moved shared libs + proto stubs that kantheon consumes — today `0.0.1-LOCAL` via `mavenLocal()` (the S5-enabler set, commit `ad6bad2`): `otel-config`, `logging-config`, `ktor-configurator`, `db-common`, `data-formatter`, `fuzzy-common`, `whois-common`, `keycloak-auth`, `ttr-meta-client`, `ttr-llm-client`, `capabilities-client`, `component-testkit`, `integration-harness`, `shared/proto` stubs — audit the exact set in T1, the toml is ground truth. This retires the review-input ⚑5 clean-machine wrinkle.

- [x] **T1 — Audit the artifact set.** From kantheon `gradle/libs.versions.toml`, list every module pinned to `version.ref = "tatrman-server"` (= `0.0.1-LOCAL`). Cross-check each has a `maven-publish` block in tatrman-server (the S5-enabler wired `publishToMavenLocal`; confirm group `org.tatrman`, artifact names functional, no persona). Record the definitive set + coordinates in findings.
- [x] **T2 — Publish workflow (TDD: dry-run first).** Add `.github/workflows/publish.yml` to tatrman-server (clone the tatrman shape): tag `server-libs/v<x.y.z>` publishes the whole T1 set to GH Packages `Collite/tatrman-server` (staging registry per RO-17; `GITHUB_TOKEN` suffices in Actions). First run the workflow's Gradle line locally with `-Pversion=0.0.2-LOCAL publishToMavenLocal` as the dry-run, inspect one POM (license block = Apache-2.0, no kantheon deps — contracts §7 arrow rule), then commit the workflow.
- [x] **T3 — Persona + dependency-rule gate on the publish path.** The publish workflow's first job = the existing enforcing grep gate + the P2/RO-6 dependency-rules check (both already in `ci.yml` — reuse, don't duplicate: make publish.yml `needs:` a called workflow or re-run the two steps). A publish with a red gate must be impossible by construction.
- [x] **T4 — Cut `server-libs/v0.9.0`. [GATE]** Preconditions: T2 dry-run inspected · T3 gates green · tag from folded `master`. Verify all packages appear under `Collite/tatrman-server` Packages with version 0.9.0.
- [x] **T5 — kantheon repoints; mavenLocal retired.** kantheon `settings.gradle.kts`: add the `Collite/tatrman-server` Maven feed next to the existing `Collite/tatrman` one (same `gpr.user`/`gpr.token` mechanism, `includeGroup("org.tatrman")`); `libs.versions.toml`: `tatrman-server = "0.9.0"`; **remove the `mavenLocal()` entry** (or scope it out of `org.tatrman`) so a clean machine builds from registries alone. Branch `sv-p1-server-artifacts`.
- [x] **T6 — Clean-machine proof (the ⚑5 test, executed).** `./gradlew --stop && rm -rf ~/.gradle/caches/modules-2/files-2.1/org.tatrman && cd kantheon && ./gradlew build -x test --refresh-dependencies` — green with no prior tatrman-server `publishToMavenLocal`. Also delete the clean-machine caveat from kantheon's README/CLAUDE.md (it rides S3·T6's prose sweep if easier — then note the handover here).
- [x] **T7 — Findings + register.** Plan §SV-P1 gate 3 row: library half done (staging). Record the coordinate list; note anything that turned out kantheon-private and did NOT need publishing (trim the set rather than publish speculatively — every published artifact is a maintenance promise).

**Verify block:**
```bash
git -C ~/Dev/collite-gh/tatrman-server tag | grep server-libs/v0.9.0
grep -n "mavenLocal" ~/Dev/collite-gh/kantheon/settings.gradle.kts   # nothing org.tatrman-scoped
grep -n 'tatrman-server *= *"0.9.0"' ~/Dev/collite-gh/kantheon/gradle/libs.versions.toml
# clean-machine build per T6 (run verbatim, record runtime in findings)
```

## Findings / ⚑

*(T1 coordinate table · T6 proof run · trims)*

---

## Findings (2026-07-11 — server-libs/v0.9.0 published, gate 3a library half DONE)

**All tasks done. ⚑5 (the clean-machine `0.0.1-LOCAL` wrinkle) retired.**

### T1 — the published set (11 modules) @ `org.tatrman:*:0.9.0` on GH Packages `Collite/tatrman-server`
`ttr-server-proto` · `otel-config` · `logging-config` · `ktor-configurator` · `db-common` · `data-formatter` · `fuzzy-common` · `whois-common` · `keycloak-auth` · `ttr-meta-client` · `ttr-llm-client`. Each verified present (HTTP 302 on the POM); no kantheon-scoped deps in any POM (§7 arrow); Apache-2.0 `<licenses>` block added to the publishing convention.

### T7 trim — `capabilities-client` NOT published
It was in the S5-enabler `publishableLibs` set but has **no external consumer** (kantheon keeps its own copy; nothing published depends on it). Removed from the set — verified `capabilities-client/0.9.0` returns **404**. (Rule: every published artifact is a maintenance promise.)

### T2/T3 — publish workflow + gate
`tatrman-server/.github/workflows/publish.yml`: `server-libs/v*` → the 11-module set to GH Packages; version via `-Ptatrman-server.version`. The publish job `needs:` a **gate job** (enforcing persona grep + P2/RO-6 dependency-rules) — a red gate makes publishing impossible. Run `29151209027`: gate ✅ + publish ✅.

### T6 — clean-machine proof (⚑5, executed)
`./gradlew --stop && rm -rf ~/.gradle/caches/modules-2/files-2.1/org.tatrman && cd kantheon && ./gradlew build -x test --refresh-dependencies` → **BUILD SUCCESSFUL in 3m 58s**; `ttr-server-proto@0.9.0` + siblings re-fetched from GH Packages; **0 resolution failures, 0 `0.0.1-LOCAL`**. kantheon `settings.gradle.kts` now has the `Collite/tatrman-server` feed and **no `mavenLocal()`**; `tatrman-server = "0.9.0"`.

### Deferred / handover
- ⚑ **metadata/modeler consumer bumps** (`0.8.6 → 0.9.1`, S1 carry-over) NOT done — the clean-machine build is green with 0.8.6 resolving from the registry, so they're pure freshness hygiene, not gate-blocking. Left for a later touch (or SV-P2).
- **Clean-machine caveat prose** in kantheon `README.md`/`CLAUDE.md` → **rides S3·T6's prose sweep** (T6 handover).

### Branches for the fold
tatrman-server `sv-p1-server-artifacts` (`27a5db5`) · kantheon `sv-p1-server-artifacts` (`dc48d9d`) · tatrman `sv-p1-server-artifacts` (docs).
