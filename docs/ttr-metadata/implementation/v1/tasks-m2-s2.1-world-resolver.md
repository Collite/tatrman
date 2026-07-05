# Tasks · M2 · Stage 2.1 — WorldResolver + ResolvedWorld

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The TTR-P-facing resolution API (plan M2.1; closes the mechanism half of R2): `WorldResolver` + `ResolvedWorld` with instance-`extends`-type overlay, exactly-one-staging validation (D-f), `hosts:` package mapping (D-d-i), and structured id-free failures (contracts §3, MD5); kind-typed `MetadataQuery.resolve(qname, expectedKind)` with `KindMismatch(expected, found)` (D-b position-typing support); `erToDb` binding traversal returning the full `BindingStep` provenance chain incl. `definitionLocation` (E-d support); and the **shared fixture project established in its single home** (contracts §8) wired through Gradle `java-test-fixtures` so `ttrp-frontend` Stage 1.3 consumes it without duplication. Everything proven by Kotest suites against the `acme.worlds.dev` fixture from TTR-P `tasks-p1-s1.3-resolution.md` T1.3.1.

## Pre-flight (all must pass before T2.1.1)

- [ ] **M1 DONE bar:** `./gradlew :packages:kotlin:ttr-metadata:test :packages:kotlin:ttr-metadata-git:test` green; `settings.gradle.kts` includes both modules; the ≈19+ ported core specs pass (plan M1.1/M1.2).
- [ ] **M0 DONE bar (world grammar):** `grep -n "world" packages/grammar/src/TTR.g4` shows the `world` schema code; a `schema world` doc with `def world`/`def engine`/`def executor`/`def storage`, `extends`, `hosts: [pkg]`, `staging: true` parses via the Kotlin ttr-parser (M0.1 conformance fixture). If absent, STOP — world resolution cannot start (MD4).
- [ ] Record what M1 shipped for `WorldSchema` (contracts §2 marks it NEW): `grep -rn "WorldSchema\|world" packages/kotlin/ttr-metadata/src/main/kotlin --include=*.kt -il | head`. If M1 shipped only a stub (world docs load but produce no typed world objects), T2.1.2 builds the typed layer; if M1 shipped nothing world-related, T2.1.2 also adds reconciler routing — note which case applies here.
- [ ] `./gradlew build` green repo-wide (Gradle domain) at baseline.

## Tasks

### T2.1.1 · Fixture home + `java-test-fixtures` wiring + spec skeletons (TEST-FIRST)

- [ ] Wire `java-test-fixtures` into `packages/kotlin/ttr-metadata/build.gradle.kts`. Current Gradle usage (verified via context7 `/websites/gradle_current_userguide`, java_testing.html — the plugin creates a `testFixtures` source set; `main` is visible to fixtures, `test` sees fixtures automatically; consumers use the `testFixtures(...)` wrapper):

  ```kotlin
  plugins {
      // existing: base, alias(libs.plugins.kotlin.jvm), alias(libs.plugins.ktlint),
      `java-library`
      `java-test-fixtures`          // NEW — creates the testFixtures source set
      `maven-publish`
  }
  dependencies {
      // fixture helper code may reference parser types:
      testFixturesApi(project(":packages:kotlin:ttr-parser"))
  }
  ```

  Consumer side (goes into ttrp-frontend at TTR-P T1.3.1, recorded here for the API-shape review in M2.2):

  ```kotlin
  testImplementation(testFixtures(project(":packages:kotlin:ttr-metadata")))
  ```

  > **Contracts §8 divergence (record, don't improvise):** contracts §8 spells the home as `src/test/resources/fixtures/` — but the `test` source set is **not consumable** cross-project; only `testFixtures` is. Place fixtures under **`src/testFixtures/resources/fixtures/`** (the module's own specs still see them — test sees testFixtures per Gradle docs). Log this as a contracts changelog item in M2.2's API-shape review (T2.2.5).
- [ ] Build the fixture project at `packages/kotlin/ttr-metadata/src/testFixtures/resources/fixtures/erp-project/` — content transcribed from TTR-P `tasks-p1-s1.3-resolution.md` T1.3.1 (this is the **single home**, contracts §8; ttrp-frontend will consume, not copy):
  - `modeler.toml` — carried verbatim for ttrp-frontend's manifest tests; **ttr-metadata itself never reads it** (MD5). Content:
    ```toml
    [ttrp]
    world           = "acme.worlds.dev"
    bare-target     = "erp_pg"
    bare-shell      = "bash"
    split-policy    = "warn"
    display-default = "arrow"
    rls-egress      = "warn"
    default-imports = ["erp.*"]
    ```
  - `models/erp/db.ttrm` — `schema db`, package `erp`: table `accounts` (`account_id: integer`, `branch_code: string`, `region: string`, `status: string`); table `SALES_TXN` (`CUSTOMER: string`, `BRANCH: string`, `AMOUNT: decimal`) — physical-cased on purpose (E-d rendering depends on the case gap).
  - `models/erp/er.ttrm` — `schema er`, package `erp.er`: entity `customer` (attr `customerType`, …), entity `sales_txn` (attrs `customer`, `branch`, `amount`, `region`), relation `customer_sales` (customer↔sales_txn); plus `schema binding` er2db defs mapping `sales_txn → SALES_TXN`, `sales_txn.amount → SALES_TXN.AMOUNT`, `customer.customerType → accounts.status`-style attribute maps (enough rows that a **multi-hop chain** exists for ErBindingChainSpec).
  - `models/acme/worlds.ttrm` — `schema world`, package `acme.worlds`: `def world dev` with `def engine erp_pg { type: postgres }`, `def engine polars { type: polars }`, `def executor sh { type: bash }`, `def storage erp_db { type: postgres, via: erp_pg, hosts: [erp] }`, `def storage files { type: local_dir }`, `def storage stage { type: local_dir, staging: true }`, and a named `def schema sales_csv` on `files` (D-c world home). **Add one overlay pair for T2.1.3:** a world-level type def (e.g. `def engine pg_base { type: postgres, version: "16", options: {…} }`) with `erp_pg` written as `extends: pg_base` plus instance deltas — exact spellings follow the M0 grammar as shipped; keep the object roster, note deltas in a fixture-header comment.
  - The `programs/*.ttrp` files from s1.3 T1.3.1 stay in ttrp-frontend (TTR-P documents are not this library's concern); contracts §8 scopes the shared home to db/er/binding models + world doc + world-level negatives.
- [ ] Negative world fixtures at `.../fixtures/worlds-negative/` (each a self-contained parseable model dir or single `.ttrm`, header comment naming the expected structured failure):
  - `two-staging/worlds.ttrm` — storages `stage` **and** `stage2` both `staging: true` → `StagingConflict` (D-f; backs TTRP-WLD-004).
  - `hosts-unknown-package/worlds.ttrm` — `def storage erp_db { …, hosts: [nosuchpkg] }` with no `nosuchpkg` model package loaded → `HostsUnknownPackage` (D-d-i).
  - `extends-unresolved/worlds.ttrm` — `def engine erp_pg { extends: acme.worlds.nosuch }` (dotted, model-qname-shaped, resolves to nothing) → `ExtendsUnresolved`.
  - `not-a-world/worlds.ttrm` — a well-formed world doc; the negative is exercised by resolving `acme.worlds.dev.erp_pg` (an engine qname) as a world → `NotAWorld(foundKind=ENGINE)` (backs TTRP-WLD-003). Plus resolving `acme.worlds.prod` (absent) → `WorldNotFound` (backs TTRP-WLD-002).
- [ ] Fixture accessor in `src/testFixtures/kotlin/org/tatrman/ttr/metadata/fixtures/MetadataFixtures.kt`: `object MetadataFixtures { fun erpProjectRoot(): Path; fun worldsNegativeRoot(name: String): Path; fun loadErpSnapshot(): RegistrySnapshot /* LocalFsStorage → MetadataLoader → registry swap */ }` — so ttrp-frontend never does classpath-resource gymnastics.
- [ ] Spec skeletons (Kotest, package `org.tatrman.ttr.metadata.world` / `.query`), red: `WorldResolverSpec.kt`, `KindTypedResolveSpec.kt`, `ErBindingChainSpec.kt`, `WorldFailureSurfaceSpec.kt`. Case rosters pinned in T2.1.3–T2.1.7.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*World*' --tests '*KindTyped*' --tests '*ErBinding*'` runs red; a smoke case in `WorldResolverSpec` init proves every fixture file parses standalone via the Kotlin ttr-parser (fixtures are TTR-M-valid before resolution exists); `./gradlew :packages:kotlin:ttr-metadata:compileTestFixturesKotlin` succeeds (fixtures source set wired).

### T2.1.2 · Typed world model — `WorldSchema` contents + reconciler routing

- [ ] Per the pre-flight recording, add/complete the typed world layer in `org.tatrman.ttr.metadata.model`: `WorldSchema : SchemaContents` (contracts §2) containing `WorldDef` objects; each `WorldDef` holds `EngineDef`/`ExecutorDef`/`StorageDef`/`SchemaDef` children. Every def carries: qname, `extendsRef: String?` (raw spelling), property bag, `manifest` (the free-form property blocks — **transported as data, never interpreted**, T6/MD5; JsonObject-shaped per contracts §3), `hosts: List<String>` (storage only), `staging: Boolean` (storage only), and `SourceLocation` (needed for failure fields and E-d-adjacent rendering).
- [ ] Route `schema world` documents through `ModelReconciler` into `WorldSchema`; world defs appear in `Model.objectByQname()` with world-kind `ObjectKind` entries (`WORLD`, `ENGINE`, `EXECUTOR`, `STORAGE`, `WORLD_SCHEMA` — extend the kind enum as needed; this is what `resolve(qname, expectedKind)` keys on in T2.1.5).
- [ ] Malformed world content that parses but can't reconcile (e.g. `hosts:` value not a list) → `LoadIssue`, never a throw (`LoadResult` contract, contracts §2).
- [ ] `WorldResolverSpec` cases green after this task: `"listWorlds enumerates every def world in the loaded model"` (finds exactly `acme.worlds.dev`), `"world defs appear in objectByQname with world kinds"`.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*WorldResolverSpec*'` — the two cases above green, rest still red; all M1 specs still green (`./gradlew :packages:kotlin:ttr-metadata:test`).

### T2.1.3 · `WorldResolver.resolve` happy path — instance-`extends`-type overlay

- [ ] `org.tatrman.ttr.metadata.world.WorldResolver(snapshot: RegistrySnapshot)` with `listWorlds(): List<QualifiedName>` and `resolve(worldQname: QualifiedName): WorldResolution` returning `Ok(ResolvedWorld)` per contracts §3 (`qname`, `engines`, `executors`, `storages`, `staging`, `fingerprint`). `fingerprint` is populated by a clearly-`internal` placeholder canonicalizer in this stage (M2.2 T2.2.2 replaces it with the normative contracts-§5 implementation); spell it `sha256:<hex>` from day one so the field shape never changes.
- [ ] **Overlay semantics (the merge rule — REVIEWABLE, flag in the stage PR; the docs specify the mechanism (T6-b two-layer `extends`) but not the property-merge rule):**
  1. `extends:` targets are resolved **within the loaded model** (world-schema defs, same doc or any loaded world doc), transitively, cycle-detected.
  2. Property-level merge: **instance key wins; type fills gaps** (property absent on instance → type's value).
  3. **List-valued properties are NOT element-merged — the instance value replaces the type's wholesale** (`hosts`, any list property, list-valued manifest entries).
  4. `manifest` blocks merge per-key at the top level only (instance key wins); a nested block under a colliding key is replaced, not deep-merged.
  5. Source locations on the resolved object point at the **instance** def (the thing the user wrote).
  > **Known divergence to carry into M2.2's API review (do not solve here):** TTR-P `tasks-p2-s2.2` T2.2.4 overlays instance deltas onto **compiler-shipped type manifests** (`extends: postgres-16`) with additive `+functions` semantics — a different overlay at a different layer. Rule shipped here: **dotted (qname-shaped) `extends` refs must resolve in the model** (else `ExtendsUnresolved`, T2.1.4); **bare-identifier refs pass through unresolved** on `ResolvedEngine.extendsRef` for the compiler's `ManifestSource` join (TTR-P TTRP-WLD-001 territory). Record both the rule and the alternative (TTR-P amends its fixture to world-level base types) in `notes-api-review.md` at T2.2.4.
- [ ] `WorldResolverSpec` positive cases (full roster, all green by end of task):
  - `"resolve acme.worlds.dev returns Ok with engines erp_pg and polars, executor sh, storages erp_db files stage"`
  - `"extends overlay: instance property wins over type property"`
  - `"extends overlay: type fills properties absent on instance"`
  - `"extends overlay: list-valued property is replaced not merged"`
  - `"extends overlay: transitive chain resolves; cycle yields ExtendsUnresolved not a hang"`
  - `"bare-identifier extends passes through verbatim on extendsRef"`
  - `"manifest block transported verbatim as data"`
  - `"world-declared schema sales_csv rides on storage files"` (D-c world tier)
  - `"fingerprint field is spelled sha256:<hex>"`
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*WorldResolverSpec*'` — positive roster green (negative cases may still be red until T2.1.4).

### T2.1.4 · Staging validation + `hosts:` mapping + structured failures

- [ ] Exactly-one-staging (D-f α): zero `staging: true` → `ResolvedWorld.staging = null` (legal — a program with no crossing needs none; the **caller** decides when absence is an error, MD5); exactly one → that storage; more than one → `StagingConflict(storages)` failure.
- [ ] `hosts:` mapping (D-d-i): every `hosts:` entry must name a loaded model package; expose the mapping on `ResolvedStorage.hosts: List<PackageName>` plus a derived `ResolvedWorld.hostsByPackage(): Map<PackageName, ResolvedStorage>`; unknown package → `HostsUnknownPackage(pkg)` failure.
- [ ] Complete the failure surface per contracts §3, all as one sealed type (spelling normative in shape): `sealed interface WorldResolution { data class Ok(world: ResolvedWorld); sealed interface Failure : WorldResolution }` with `WorldNotFound(worldQname, knownWorlds)`, `NotAWorld(worldQname, foundKind, definitionLocation)`, `StagingConflict(storages: List<QualifiedName>, locations: List<SourceLocation>)`, `HostsUnknownPackage(pkg, storageQname, definitionLocation)`, `ExtendsUnresolved(typeRef, onDef: QualifiedName, definitionLocation)`. **No message strings with policy, no diagnostic ids, no "TTRP-" anywhere** (MD5) — fields only; consumers render.
- [ ] `WorldResolverSpec` negative cases (all green): `"unknown world qname yields WorldNotFound with knownWorlds populated"`, `"engine qname yields NotAWorld carrying foundKind ENGINE and its location"`, `"two staging true yields StagingConflict listing both storage qnames and locations"`, `"hosts unknown package yields HostsUnknownPackage naming pkg and storage"`, `"dotted unresolvable extends yields ExtendsUnresolved carrying typeRef and location"`, `"hosts mapping erp package resolves to erp_db"`.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*WorldResolverSpec*'` — entire spec green.

### T2.1.5 · Kind-typed lookup — `MetadataQuery.resolve(qname, expectedKind)`

- [ ] Extend `org.tatrman.ttr.metadata.query.MetadataQuery` (home of the MD2 pull-down from M1.2) with `resolve(qname: QualifiedName, expected: ObjectKind): ResolveOutcome` per contracts §2: `Found(obj)`, `NotFound(qname, expected)`, `KindMismatch(qname, expected, found, definitionLocation)`, `Ambiguous(qname, candidates: List<QualifiedName>)`. `expected` covers model kinds AND the world kinds from T2.1.2 — this is the library face of D-b position typing (`target`→ENGINE, `store`→STORAGE, `uses world`→WORLD, …); the position→kind table itself is **TTR-P compiler policy** and must not appear here (MD5).
- [ ] `KindMismatch` carries both kinds as enum values plus the found object's `definitionLocation` — TTR-P renders "expected an engine; `<x>` is a `<kind>`" (TTRP-RES-003/TTRP-MOV-001) purely from these fields.
- [ ] `KindTypedResolveSpec` cases: `"resolve erp.accounts as TABLE returns the DbTable"`, `"resolve erp.er.customer as ENTITY returns the Entity"`, `"resolve acme.worlds.dev.erp_pg as ENGINE returns the engine def"`, `"resolve acme.worlds.dev.files as ENGINE yields KindMismatch expected ENGINE found STORAGE"` (TTRP-RES-003 seed), `"resolve acme.worlds.dev.erp_pg as STORAGE yields KindMismatch expected STORAGE found ENGINE"` (TTRP-MOV-001 seed), `"resolve files.nope member as STORAGE_OBJECT yields NotFound echoing the expected kind"` (TTRP-RES-001 seed), `"ambiguous short name yields Ambiguous with sorted candidate qnames"`, `"relation customer_sales resolves as RELATION and exposes both endpoint entities"` (TTRP-RES-004 needs endpoint data to check "between the joined entities" caller-side).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*KindTypedResolveSpec*'` — green.

### T2.1.6 · `erToDb` binding traversal with full provenance chain (E-d)

- [ ] `MetadataQuery.erToDb(erQname: QualifiedName): ErBindingResult` per contracts §3: `dbQname: QualifiedName?` + `chain: List<BindingStep>`, each step `{erQname, dbQname, definitionLocation}` — `definitionLocation` = where the er2db binding def that justifies the hop lives (E-d: the consumer renders the er spelling first and can point at the binding). Works for entities (`erp.er.sales_txn → erp.SALES_TXN`), attributes (`erp.er.sales_txn.amount → erp.SALES_TXN.AMOUNT`), and relations (endpoint key-column pairs exposed so TTR-P T1.3.5 can synthesize the join `Expression`).
- [ ] Miss → `BindingMissing(erQname, searchedPackages)` (contracts §2 shape; `searchedPackages` = the binding-bearing packages actually consulted, so TTR-P can render "no er2db binding reachable in world `acme.worlds.dev`" — the world-hosted-package **scoping** of the search is caller policy; the library takes the package set or searches all loaded packages, whichever M1's binding index already does — record which in KDoc).
- [ ] `ErBindingChainSpec` cases: `"erToDb erp.er.sales_txn yields erp.SALES_TXN with a non-empty chain"`, `"erToDb erp.er.sales_txn.amount yields erp.SALES_TXN.AMOUNT"`, `"every BindingStep carries erQname dbQname and definitionLocation"`, `"multi-hop chain preserves hop order"`, `"relation customer_sales exposes bound key-column pairs"`, `"unbound er attribute yields BindingMissing with erQname and searchedPackages"` (TTRP-RES-005 seed).
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*ErBindingChainSpec*'` — green.

### T2.1.7 · Failure-surface completeness — the s1.3 roster walk (MD5 proof)

- [ ] `WorldFailureSurfaceSpec` — a table-driven spec that walks the TTR-P s1.3 negative roster and asserts, per row, that the library failure object **carries every field TTR-P needs to render the diagnostic**. The mapping table (reproduce it in the spec as the test table AND keep it in this file as the review artifact):

  | TTR-P id (s1.3 roster) | s1.3 fixture seed | Library surface | Structured failure + required fields |
  |---|---|---|---|
  | TTRP-WLD-001 | no world selected anywhere | **none — caller-side** (selection precedence is TTR-P contracts §2; MD5) | n/a; spec asserts `listWorlds()` is sufficient input for the caller's message |
  | TTRP-WLD-002 | `uses world "acme.worlds.prod"` resolves to nothing | `WorldResolver.resolve` | `WorldNotFound(worldQname, knownWorlds)` — knownWorlds powers "did you mean" |
  | TTRP-WLD-003 | qname names a non-world def | `WorldResolver.resolve` | `NotAWorld(worldQname, foundKind, definitionLocation)` |
  | TTRP-WLD-004 | two `staging: true` storages | `WorldResolver.resolve` | `StagingConflict(storages, locations)` — both names, both locations |
  | TTRP-RES-001 | `load(files.nope)` | `MetadataQuery.resolve` | `NotFound(qname, expected)` |
  | TTRP-RES-002 | same-level import ambiguity | caller (import tier) + `MetadataQuery.resolve` | `Ambiguous(qname, candidates)` when the model itself is ambiguous |
  | TTRP-RES-003 | `target files` (wrong kind) | `MetadataQuery.resolve(…, ENGINE)` | `KindMismatch(qname, expected, found, definitionLocation)` |
  | TTRP-RES-004 | relation missing / wrong endpoints | `MetadataQuery.resolve(…, RELATION)` + Relation endpoint data | `NotFound` for the miss; endpoint entities exposed for the caller's between-check |
  | TTRP-RES-005 | er attr with no er2db binding | `MetadataQuery.erToDb` | `BindingMissing(erQname, searchedPackages)` |
  | TTRP-RES-006 | `import erp.nosuch.*` | `Model` package enumeration | package-existence query returns empty; no dedicated failure object needed — assert the query exists |
  | TTRP-SCH-001/002/003 | declared-schema conflicts/absence/types | **caller-side** (D-c precedence + S23 are compiler policy) | library only serves world-declared schemas on `ResolvedStorage` — assert they're readable |
  | TTRP-MOV-001 | `store(erp_pg)` | `MetadataQuery.resolve(…, STORAGE)` | `KindMismatch(expected=STORAGE, found=ENGINE, …)` |
  | (world neg.) hosts unknown pkg | fixture `hosts-unknown-package` | `WorldResolver.resolve` | `HostsUnknownPackage(pkg, storageQname, definitionLocation)` |
  | (world neg.) extends unresolved | fixture `extends-unresolved` | `WorldResolver.resolve` | `ExtendsUnresolved(typeRef, onDef, definitionLocation)` |

- [ ] Add the MD5 guard case: `"no library-produced type or rendering contains the string TTRP-"` — reflect over the sealed `WorldResolution.Failure` / `ResolveOutcome` subclasses and their `toString()`s; plus a source-level guard: `git grep -l "TTRP-" packages/kotlin/ttr-metadata/src/main` must be empty (assert via spec or record as a CI-greppable rule in the module README).
- [ ] Note in the spec header: s1.3 and s2.2 assign **colliding TTRP-WLD ids** (s1.3 WLD-004 = two-staging; s2.2 WLD-002 = two-staging). Library is insulated by MD5 (no ids here); the collision is recorded for the TTR-P side in M2.2's `notes-api-review.md`.
  - **Verify:** `./gradlew :packages:kotlin:ttr-metadata:test --tests '*WorldFailureSurfaceSpec*'` — green; then full module `./gradlew :packages:kotlin:ttr-metadata:test` green.

## Definition of DONE (stage)

- [ ] Fixture project lives at its contracts-§8 single home under `testFixtures`, consumable via `testFixtures(project(":packages:kotlin:ttr-metadata"))`; `MetadataFixtures` accessor compiles in the testFixtures source set.
- [ ] `WorldResolver.resolve("acme.worlds.dev")` returns a `ResolvedWorld` with 2 engines (+ overlay applied), 1 executor, 3 storages, `staging = stage`, `hosts: erp → erp_db`, world-declared schema on `files`, `sha256:`-spelled fingerprint field.
- [ ] All five contracts-§3 failure shapes produced by fixtures and field-complete per the T2.1.7 table; zero diagnostic ids in library code (MD5).
- [ ] `resolve(qname, expectedKind)` and `erToDb` green per their spec rosters; every `BindingStep` carries `definitionLocation` (E-d).
- [ ] Overlay merge rule + dotted-vs-bare `extends` rule documented in KDoc and **flagged reviewable in the stage PR description**.
- [ ] `./gradlew build` green repo-wide; all pre-existing M1 specs untouched and green.

## Blockers

_(empty — coder records here)_

## References

- **Contracts:** `../../architecture/contracts.md` §2 (core API, `ResolveOutcome`), §3 (NORMATIVE `WorldResolver`/`ResolvedWorld`/`ErBindingResult` + failure shapes + manifest-as-data), §8 (fixture sharing / java-test-fixtures).
- **MD decisions:** MD2 (MetadataQuery facade), MD5 (mechanism/policy split — no ids, no manifest interpretation, no position table), MD4 (M0 grammar gate).
- **TTR-P decisions:** D-b (position typing — caller maps position→kind; library returns `KindMismatch(expected, found)`) · D-b-iii (model objects in load position) · D-c-δ (world-declared schemas one of three homes) · D-d-α/D-d-i (`schema world`; `hosts:`) · D-f-α (exactly-one staging; >1 = conflict) · D-g (offline, embedded) · E-d-γ (er rewrite provenance — `BindingStep.definitionLocation` feeds it) · B-T6 (two-layer type/instance `extends`; manifests transported not interpreted).
- **Fixture source of truth:** `../../../ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` T1.3.1 (transcribed above; spellings follow the M0 grammar as shipped).
- **Consumer walk-throughs served:** s1.3 T1.3.3 (WorldResolver inputs), T1.3.4 (kind-typed resolve), T1.3.5 (erToDb provenance); s2.2 T2.2.4 (WorldBinder rides `ResolvedWorld` + `extendsRef`).
- Gradle `java-test-fixtures`: docs.gradle.org/current/userguide/java_testing.html (snippet embedded in T2.1.1; fetched via context7 2026-07-05).
