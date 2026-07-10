# Progress · Phase M2 · World resolution + TTR-P API (closes R2)

> `[x]` = developer intent; verify against runtime before trusting.

## Status

**M2 code-complete & green locally 2026-07-05.** `./gradlew build` green (all modules
+ ktlint); ttr-metadata suite green. R2 is closed at the code level — WorldResolver,
kind-typed resolve, erToDb, and the semantic world fingerprint all work and are
Maven-Local consumable (incl. the `test-fixtures` variant). The only deferred piece
is the external `kotlin-metadata/v0.1.0` GitHub Packages publish (held for feature
review, same policy as M0/M1); TTR-P s1.3 can consume in-repo via
`project(":packages:kotlin:ttr-metadata")` + `testFixtures(project(...))` meanwhile.

## M2.1 — WorldResolver + ResolvedWorld

- Typed world tier (`WorldSchema`/`World`/`WorldEngine`/`WorldExecutor`/`WorldStorage`/
  `WorldSchemaObject`) + reconciler routing; `SchemaCode` gains library-only `WORLD`.
- `WorldResolver`: listWorlds + resolve → `ResolvedWorld` with instance-⊕-extends-type
  overlay (instance wins, type fills, lists/manifest replaced; dotted extends resolve
  in-model else `ExtendsUnresolved`, bare pass through), exactly-one-staging (D-f),
  hosts→package (D-d-i), 5 structured id-free failures (MD5).
- `MetadataQuery.resolve(qname, expectedKind): ResolveOutcome`; `erToDb → ErBindingResult`
  with `BindingStep` provenance (E-d).
- Shared fixture home under `src/testFixtures/resources/fixtures/` + `MetadataFixtures`.
- Specs: WorldResolverSpec, KindTypedResolveSpec, ErBindingChainSpec, WorldFailureSurfaceSpec.

## M2.2 — fingerprint + taxonomy + API review + publish

- **`WorldFingerprint`** (contracts §5, F-f-ii): hand-rolled canonical JSON (sorted keys,
  qname-sorted arrays, defaults elided, locations/comments excluded — no new dep) → sha256,
  `sha256:<hex>`; world qname excluded from the hash (reviewable). `FingerprintSpec` pins
  stability (order/whitespace/comments/default-elision insensitive) + sensitivity (version,
  hosts, staging move, manifest edit, storage add) + canonical determinism + independent sha256.
- **`LoadIssue`** finalized sealed taxonomy (id-free enum categories, MD5); `LoadResult.issues`
  now `List<LoadIssue>` (+ errors/warnings views). `LoadIssueTaxonomySpec`.
- **`notes-api-review.md`**: all 7 seed divergences adjudicated (FIX-HERE vs TTR-P-AMENDMENT) +
  the s1.3/s2.2 consumer walk. FIX-HERE items shipped; contracts changelog **v1.2** added.
- **Publish wiring** (`publish.yml` `kotlin-metadata/v*` + PUBLISHING.md rows) from M1.2;
  Maven-Local rehearsal at `0.0.2-LOCAL` publishes both artifacts + the `test-fixtures`
  variant, POM references the closed ttr-* set.

## Deferred (external — reviewer action)

- **`kotlin-metadata/v0.1.0`** tag push (→ CI publishes to GitHub Packages). Once approved:
  `git tag kotlin-metadata/v0.1.0 && git push origin kotlin-metadata/v0.1.0`; then the T2.2.6
  scratch-consumer-from-feed smoke + T2.2.7 published-coordinate roster gate. Record the
  resolved version here. Also the M0 `kotlin/v*` + `python/v*` tags remain deferred (M0 doc).

## Reviewable flags (carried to review)

- Overlay property-merge rule (instance wins / lists replaced) — contracts left it unspecified.
- Dotted-vs-bare `extends` rule (dotted resolves in-model, bare passes through).
- Fingerprint excludes the world qname from the hash (F-f-ii "qname in clear").
- `LoadIssue` categorization is prefix-based over the inherited `LoadWarning`s (additive; full
  per-emission-point migration deferred — messages already carry the info).
