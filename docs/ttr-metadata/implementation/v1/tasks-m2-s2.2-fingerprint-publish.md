# Tasks · M2 · Stage 2.2 — Fingerprint + hardening + first publish

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

Plan M2.2 — the stage that **closes R2**: the semantic world fingerprint (contracts §5, F-f-ii) implemented once for compiler + conformance harness, with kotest-property stability/sensitivity suites; the `LoadIssue` taxonomy finalized (id-free, MD5); a written API-shape review walking TTR-P `tasks-p1-s1.3` and `tasks-p2-s2.2` step-by-step against the real API (`notes-api-review.md` beside this file — every divergence either fixed here or recorded as a TTR-P task-list amendment per plan §6); and the first real publish, tag **`kotlin-metadata/v0.1.0`**, after which TTR-P s1.3's pre-flight passes verbatim.

## Pre-flight (all must pass before T2.2.1)

- [x] Stage M2.1 DONE bar checked off; `./gradlew :packages:kotlin:ttr-metadata:test` green.
- [x] `grep -n "kotest-property" gradle/libs.versions.toml` — present (it is in the `kotest` bundle today; property tests below rely on it).
- [x] `grep -n "kotlin-metadata" .github/workflows/publish.yml PUBLISHING.md` — plan M1.2 was tasked with the `kotlin-metadata/v*` tag wiring + PUBLISHING.md rows. If **absent**, T2.2.6 step 1 adds it (small, in-scope here — do not blocker); if present, T2.2.6 step 1 is a no-op check.
- [x] Publisher access: either CI does the publish (tag push → `GITHUB_TOKEN`, nothing needed) or, for a manual dry-run, `gpr.user`/`gpr.token` with `write:packages` in `~/.gradle/gradle.properties` (PUBLISHING.md "Local-developer authentication").

## Tasks

### T2.2.1 · Fingerprint spec skeletons + golden canonical form (TEST-FIRST)

- [x] New specs in `org.tatrman.ttr.metadata.world`, red: `FingerprintCanonicalFormSpec.kt`, `FingerprintStabilitySpec.kt` (property), `FingerprintSensitivitySpec.kt` (property).
- [x] `FingerprintCanonicalFormSpec` pins the canonical serialization per contracts §5 with a **golden file** `src/testFixtures/resources/fixtures/golden/acme-worlds-dev.canonical.json` (checked in; goldens live beside the shared fixtures so the conformance harness and TTR-P can reuse them). Cases:
  - `"canonical form of acme.worlds.dev matches the golden byte-for-byte"`
  - `"keys are lexicographically sorted at every nesting level"`
  - `"arrays of qnames are qname-sorted"`
  - `"properties at default values are elided"`
  - `"source locations and doc comments never appear"`
  - `"fingerprint is sha256 of the canonical UTF-8 bytes, spelled sha256:<hex>"` (recompute with `java.security.MessageDigest` in the spec — independent of the implementation).
  - `"world qname is excluded from the hashed form"` — **REVIEWABLE:** F-f-ii says "hash of the resolved world model + world qname **in clear**"; read here as: the hash covers the resolved world *content*, the qname travels beside it unhashed (as in the TTR-P bundle's `manifest.json.world` `{qname, fingerprint}` pair). If review overturns to qname-in-hash, only this case and T2.2.2 flip. Flag in the PR.
- [x] Property-test harness: an `Arb<WorldDocVariant>` that renders the SAME semantic world to TTR text with shuffled def order (`Arb.shuffle`), randomized whitespace, and injected comments — parse → resolve → fingerprint. And a mutation `Arb` producing exactly-one-semantic-field edits (used by sensitivity).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*Fingerprint*'` — compiles, runs red; M2.1 suite still green.

### T2.2.2 · Canonical-JSON writer + sha256 — `WorldFingerprint`

- [x] `org.tatrman.ttr.metadata.world.WorldFingerprint`: `fun of(world: ResolvedWorld): String` (spelled `sha256:<hex>`) + `fun canonicalForm(world: ResolvedWorld): String` (exposed — the conformance harness and any debugging surface diff the canonical form, not the hash).
- [x] Canonicalization per contracts §5, implemented as a **hand-rolled deterministic JSON emitter** (sorted keys via `TreeMap`, qname-sorted arrays, defaults elided, locations/doc-comments excluded, RFC 8785-style minimal escaping, no float formatting surprises — world properties are strings/ints/bools/lists). **No new runtime dependency**: the module-roster dep set (architecture §2.1: ttr-parser/writer/semantics, jgrapht-core, slf4j-api) is enforced by the M1.1 dependency-rules test — kotlinx-serialization stays out of the core artifact. Note RFC 8785 (JCS) in KDoc as the guiding shape.
- [x] Replace M2.1's placeholder: `WorldResolver` now populates `ResolvedWorld.fingerprint` via `WorldFingerprint.of(...)`; delete the placeholder canonicalizer.
- [x] Property suites green:
  - `FingerprintStabilitySpec` (insensitivity): `"declaration order shuffles never change the fingerprint"`, `"whitespace edits never change the fingerprint"`, `"comment insertion or removal never changes the fingerprint"` (F-f-ii's rejected text-hash: "comment reflow changes the world" must be FALSE here), `"explicit default equals omitted property"`, `"canonical form is idempotent (canonicalize twice = once)"`.
  - `FingerprintSensitivitySpec` (sensitivity — each property flips exactly one semantic field and asserts the hash changes): `"engine version/property change changes the fingerprint"`, `"hosts list change changes the fingerprint"`, `"staging flag moved to another storage changes the fingerprint"`, `"manifest entry added, removed, or edited changes the fingerprint"`, `"storage added or removed changes the fingerprint"`, `"overlay result drives the hash: editing the extends TYPE def changes the instance world's fingerprint"` (semantic = post-overlay, contracts §5 "resolved (post-overlay) world").
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*Fingerprint*'` — all three specs green (property tests at default iteration counts).

### T2.2.3 · Load-issue taxonomy finalization

- [x] Audit every `LoadIssue`/`LoadWarning` emission point inherited from Ariadne (`source/` loaders, `reconcile/ModelReconciler`, `resolve/ReferenceResolutionPass`, plus the new world routing from M2.1 T2.1.2) and finalize ONE sealed hierarchy in `org.tatrman.ttr.metadata`: `sealed interface LoadIssue { val severity: Severity; val file: String?; val location: SourceLocation?; val message: String }` with a closed `category` set (e.g. `ParseError`, `ReconcileError`, `DuplicateQname`, `UnresolvedReference`, `BindingInconsistency`, `WorldMalformed`, `StorageUnreadable` — final list from the audit). Structured fields per subclass; **no diagnostic-id minting** (MD5) — `category` is an enum, not an id string; consumers (Ariadne `ValidateModel`, Designer server `getStatus`, ttrp-frontend) map categories to their own surfaces.
- [x] `LoadResult` contract holds everywhere: model errors NEVER throw (contracts §2); a spec case per category proving a fixture produces it (extend the moved `A5DiagnosticsSpec` or add `LoadIssueTaxonomySpec` with one case per category, e.g. `"duplicate qname across files yields DuplicateQname with both locations"`).
- [x] If the finalized shape drifts from what M1 ported (renames, field additions): update the moved specs here, and note the shape in `notes-api-review.md` §taxonomy (T2.2.4) — Ariadne's M4 swap consumes exactly this shape.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*LoadIssue*' --tests '*A5*'` green; full module green.

### T2.2.4 · API-shape review — walk s1.3 + s2.2 against the real API

- [x] Produce `docs/ttr-metadata/implementation/v1/notes-api-review.md`. Method: walk **every step that consumes ttr-metadata** in `../../../ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` (pre-flight #2, T1.3.1, T1.3.3, T1.3.4, T1.3.5, T1.3.6) and `../../../ttr-p/implementation/v1/tasks-p2-s2.2-manifests-world.md` (pre-flight #2, T2.2.1 world fixture, T2.2.4 WorldBinder, T2.2.7 staging inputs), quoting each expectation, and tick it against the actual Kotlin surface. Required format — a table per consumer list:
  `| step | expectation (quoted) | real API element | ✓/✗ | divergence + disposition |`
- [x] Seed rows the walk MUST adjudicate (found during M2.1 — dispositions decided in T2.2.5):
  1. contracts §8 fixture path `src/test/resources` vs. shipped `src/testFixtures/resources` (contracts changelog entry).
  2. s1.3 T1.3.3 materializes `ResolvedWorld(engines, executors, storages(+hosts, staging flags, schemas), worldQname)` — field spelling vs contracts §3 (`qname`, `staging`, `fingerprint` present?). ttrp's local `WorldResolver` wrapper vs library `WorldResolver` name collision — flag for the s1.3 amendment.
  3. s2.2 T2.2.4: `extends: postgres-16` (compiler-manifest id) vs the M2.1 dotted-vs-bare rule; also s2.2's `+functions` additive delta vs M2.1's replace-not-merge list rule — the two overlays are different layers; state which layer owns what, or record the TTR-P fixture amendment.
  4. s2.2 rides manifest *content* on `ResolvedEngine/Executor.manifest` (plan §6 row for tasks-p2-s2.2) — confirm the manifest field shape (JsonObject-shaped per contracts §3) is consumable by kotlinx-serialization on the ttrp-graph side.
  5. s1.3/s2.2 TTRP-WLD id renumbering collision (s1.3 WLD-004 vs s2.2 WLD-002 both = two-staging) — TTR-P-side amendment, record only (MD5 keeps the library out of it).
  6. `MetadataRefresher` contract shape (`tryRefresh()/forceRefresh(): RefreshOutcome`, contracts §2) vs the Ariadne-inherited `refresh(sourceId, force): List<…>` signature — whatever M1 shipped, pin the final shape now: **M4.1 consumes it from `RefreshScheduler`** (kantheon), so a drift here is a cross-repo break.
  7. `staging = null` legality (M2.1 T2.1.4) vs s2.2 T2.2.7's deferred `TTRP-WLD-003` — confirm the caller-side deferral works with a null field.
- [x] Every row ends with a disposition: **FIX-HERE** (goes to T2.2.5) or **TTR-P-AMENDMENT** (exact edit text drafted in the notes file, to be applied per plan §6 when TTR-P lists are next touched).
  - **Verify:** `notes-api-review.md` exists with zero unadjudicated rows (`grep -c "✗" notes-api-review.md` — every ✗ has a disposition); reviewed rows cover all seven seeds.

### T2.2.5 · Divergence resolution — FIX-HERE items + contracts changelog

- [x] Implement every FIX-HERE disposition from T2.2.4 (API renames, field additions, fixture adjustments), updating specs in the same commit.
- [x] Contracts hygiene per the contracts.md header rule ("any drift that changes a shape needs a changelog entry"): add changelog entries to `../../architecture/contracts.md` for (at minimum) the testFixtures path correction (§8) and any §2/§3 shape drift that survived review; bump nothing else.
- [x] Re-run the two consumer walk-tables in `notes-api-review.md` and flip their ✗ marks to ✓ (or to a linked TTR-P amendment).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test :packages:kotlin:ttr-metadata-git:test` green; `git diff docs/ttr-metadata/architecture/contracts.md` shows the changelog entries.

### T2.2.6 · Publish `kotlin-metadata/v0.1.0`

- [x] Step 1 — tag wiring (per pre-flight finding): `.github/workflows/publish.yml` must handle `kotlin-metadata/v*` → modules `:packages:kotlin:ttr-metadata:publish :packages:kotlin:ttr-metadata-git:publish` (lockstep per contracts §1), mirroring the existing `kotlin-parser/v*` branch in the `Resolve version + modules from tag` step; PUBLISHING.md gets the two artifact rows + the `kotlin-metadata/v<x.y.z>` tag row. Both `build.gradle.kts` files must carry the `maven-publish` blocks matching `ttr-semantics` (GitHubPackages repo, POM boilerplate; ttr-metadata re-exports parser/semantics as `api` deps per contracts §1).
- [x] Step 2 — local rehearsal: `./gradlew -Pversion=0.0.2-LOCAL :packages:kotlin:ttr-metadata:publishToMavenLocal :packages:kotlin:ttr-metadata-git:publishToMavenLocal` and inspect `~/.m2/repository/org/tatrman/ttr-metadata/0.0.2-LOCAL/` — POM lists ttr-parser/semantics as `compile` (api) deps; `-git` POM depends on core.
- [ ] Step 3 — cut the tag (semver: first minor of a pre-1.0 line; PUBLISHING.md discipline — no SNAPSHOTs):
  ```bash
  git tag kotlin-metadata/v0.1.0 && git push origin kotlin-metadata/v0.1.0
  ```
  Watch the `Publish Kotlin artifacts` workflow run to green; confirm both packages appear under github.com/Collite/tatrman packages.
- [ ] Step 4 — consumer smoke from the feed (not Maven Local): a scratch Gradle project (throwaway dir, PUBLISHING.md "Consumer setup" repo block + `gpr.*` PAT) with `implementation("org.tatrman:ttr-metadata:0.1.0")` and `testImplementation("org.tatrman:ttr-metadata:0.1.0:test-fixtures")` — resolves, and a 10-line main() loads the erp fixture project via `LocalFsStorage` → prints `WorldResolver.resolve("acme.worlds.dev")`'s fingerprint. (This scratch project is throwaway — do NOT commit it into the repo.)
  - **Verify:** workflow green; scratch `./gradlew run` prints a `sha256:<hex>` fingerprint pulled entirely from GitHub Packages artifacts.

### T2.2.7 · Stage DONE sweep — TTR-P s1.3 pre-flight verbatim

- [x] Run TTR-P s1.3's pre-flight #2 exactly as written there: `org.tatlman… org.tatrman:ttr-metadata` consumable (now true via `0.1.0` on GitHub Packages AND in-repo `project(":packages:kotlin:ttr-metadata")`), providing model-graph load from a `.ttrm` directory, qname queries, er2db traversal, world-doc resolution — tick each capability against a spec name proving it.
- [x] Run s1.3 pre-flight #3: `grep -n "world" packages/grammar/src/TTR.g4` (R3 closed by M0 — confirm still true post-M2 changes).
- [ ] WLD/RES negative-roster gate (plan M2.2 DONE bar): `WorldFailureSurfaceSpec` + `KindTypedResolveSpec` + `ErBindingChainSpec` green against the **published** coordinates too — temporarily point the scratch consumer's test at `0.1.0` and run one roster case (proves the artifact, not just the tree).
- [x] Sweep: `./gradlew build` green (Gradle domain); `pnpm -r test` untouched/green (TS domain unaffected — sanity only).
  - **Verify:** all boxes above; then mark plan.md M2 stages' DONE lines and hand the R2-closed signal to the TTR-P overview (amendment applied separately per plan §6 — task #11 of the planning arc, not here).

## Definition of DONE (stage)

- [x] `WorldFingerprint` implements contracts §5 exactly: canonical JSON (sorted keys, qname-sorted arrays, defaults elided, locations/comments excluded) → sha256, spelled `sha256:<hex>`; golden canonical form checked in beside the shared fixtures.
- [x] Property tests pin insensitivity (order/whitespace/comments/default-elision) and sensitivity (engine properties, hosts, staging flag, manifest entries, post-overlay type edits).
- [x] `LoadIssue` taxonomy final, sealed, id-free, one spec case per category; `LoadResult` never throws on model errors.
- [x] `notes-api-review.md` complete: every ttr-metadata-consuming step of s1.3 + s2.2 ticked; all seven seed divergences adjudicated; FIX-HERE items shipped; TTR-P amendments drafted verbatim; contracts changelog updated.
- [ ] `kotlin-metadata/v0.1.0` live on GitHub Packages (both artifacts, test-fixtures variant included); scratch consumer resolves and runs against it.
- [x] TTR-P s1.3 pre-flight items R2/R3 pass verbatim (plan M2 DONE bar).

## Blockers

_(empty — coder records here)_

## References

- **Contracts:** `../../architecture/contracts.md` §1 (coordinates, lockstep, `kotlin-metadata/v*`, api re-export), §2 (`LoadResult`/`LoadIssue`, `MetadataRefresher`), §3 (shapes the review walks), §5 (fingerprint canonicalization — normative), §8 (fixture sharing).
- **MD decisions:** MD5 (id-free taxonomy; no policy in the library), MD3 (`-git` split publishes in lockstep), MD7 (M4 consumes `v0.1.x` — the refresher-shape seed row protects it).
- **TTR-P decisions:** F-f-ii = β (semantic fingerprint; hash of resolved world, qname in clear; text-hash rejected because "comment reflow changes the world") · T6-b (post-overlay = the semantic object) · D-f/D-d-i (fields the sensitivity suite flips).
- **Consumer lists walked:** `../../../ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` · `../../../ttr-p/implementation/v1/tasks-p2-s2.2-manifests-world.md` · amendments catalogue: plan.md §6.
- **Publishing mechanics:** `PUBLISHING.md` (tag flow, PAT, gotchas — GitHub Packages needs auth even to read) · `.github/workflows/publish.yml` (tag→module resolution step to extend).
