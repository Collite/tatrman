# Tasks · P1 · Stage 1.3 — Resolution

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The binding tier that turns names into objects, offline (D-g): the `[ttrp]` project-manifest reader (ALL contracts §2 keys, incl. `default-imports` with its S18 bare-fragments-only scoping); ttr-metadata embedding for model-repo + world-doc resolution; qname/import resolution with position typing (D-b: `target`→engine, `load`/`store`→storage|model object, `schema:`→schema); the er→db early rewrite with mandatory provenance (E-d); declared-schema handling (D-c precedence inline > program > world, same-level conflict = error; S23 types); world position checks; and a thin `ttrp check <file>` CLI entry in `ttrp-cli` (full CLI lands P3).
**DONE bar (= Phase 1 DONE):** `ttrp check` passes the hero AND the er-flavored variant; 25+ curated negative fixtures produce their named diagnostics.

## Pre-flight (all must pass before T1.3.1)

- [ ] `./gradlew :packages:kotlin:ttrp-frontend:test` fully green (Stage 1.2 DONE bar).
- [ ] **PHASE GATE (amended 2026-07-05 — R2 closed by the ttr-metadata feature): `:packages:kotlin:ttr-metadata` is DONE through its Phase M2** (`docs/ttr-metadata/implementation/v1/tasks-overview.md` — M2 row ticked; `kotlin-metadata/v0.1.0` published, Maven Local acceptable). Verify: `settings.gradle.kts` includes `:packages:kotlin:ttr-metadata` and `./gradlew :packages:kotlin:ttr-metadata:test` green. Consume the API per `docs/ttr-metadata/architecture/contracts.md` §2–3 (`MetadataLoader`/`MetadataQuery`/`WorldResolver`; structured id-free failures per MD5 — THIS stage maps them to `TTRP-*` ids; the mapping table lives in ttr-metadata `tasks-m2-s2.1` T2.1.7). If not DONE, STOP: record in §Blockers — do not hand-roll a model-repo reader inside ttrp-frontend.
- [ ] World grammar available (R3 closed by ttr-metadata Phase M0): world docs parse via `ttr-parser`. **Spelling note (RM2): grammar 4.0 uses `model world`, not `schema world`** — read the fixture `.ttrm` snippets below with that substitution (same for `schema db`/`schema er` mentions: follow the shipped 4.x directive). Verify: the M0 golden world fixture parses. If absent, STOP (gate = ttr-metadata M0 DONE).
- [ ] **Fixture sharing (ttr-metadata contracts §8):** the model/world fixture project below has its single home in ttr-metadata's `src/testFixtures/resources/fixtures/` — consume it via `testFixtures(project(":packages:kotlin:ttr-metadata"))`; create locally only the TTR-P-specific parts (`programs/*.ttrp`, `modeler.toml` with the `[ttrp]` table, negative program fixtures). Do NOT duplicate the models/world; propose model-fixture additions upstream.

## Tasks

### T1.3.1 · Resolution fixture project + spec skeletons (TEST-FIRST)

- [ ] Build a self-contained test project under `packages/kotlin/ttrp-frontend/src/test/resources/resolution/project/`:
  - `modeler.toml`:
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
  - `models/erp/db.ttrm` — `schema db`, package `erp`: table `accounts` (`account_id: integer`, `branch_code: string`, `region: string`, `status: string`), table `SALES_TXN` (`CUSTOMER: string`, `BRANCH: string`, `AMOUNT: decimal`), physical-cased on purpose.
  - `models/erp/er.ttrm` — `schema er`, package `erp.er`: entity `customer` (attr `customerType` etc.), entity `sales_txn` (attrs `customer`, `branch`, `amount`, `region`), relation `customer_sales` (customer↔sales_txn); `schema binding` er2db defs mapping `sales_txn.amount → SALES_TXN.AMOUNT` etc.
  - `models/acme/worlds.ttrm` — `schema world`, package `acme.worlds`: `def world dev` with `def engine erp_pg { type: postgres }`, `def engine polars { type: polars }`, `def executor sh { type: bash }`, `def storage erp_db { type: postgres, via: erp_pg, hosts: [erp] }`, `def storage files { type: local_dir }`, `def storage stage { type: local_dir, staging: true }`, plus a named schema `sales_csv` on `files` (D-c world home). Exact world-doc syntax follows whatever `ttr-parser` ships (pre-flight #3) — adjust spellings, keep the object roster.
  - `programs/hero.ttrp` — the Stage 1.1 hero, with refs aligned to this fixture world (`uses world "acme.worlds.dev"`, `target erp_pg`, `load(files.sales_2026, schema: sales_csv)`, `erp.accounts` inside the SQL fragment left as-is — fragment-interior resolution is P6; program-level refs must resolve NOW).
  - `programs/hero_er.ttrp` — the er variant from `06-model-binding-options.md` §"The er-flavored hero variant", adapted minimally: `import erp.er.*`; `c = load(customer)`; `c = filter(c, customerType = 'retail')`; `s = load(sales_txn)`; `j = join(left: c, right: s, on: relation customer_sales)`; aggregate + `rejects = j.rejects` (adaptation note in a fixture-header comment: bare-entity-in-arg-position from the doc example is deferred to D-b-iii review).
- [ ] Negative fixtures `resolution/negative/` — **25 minimum**, `# expect: TTRP-…` header each. Roster (≥1 fixture per id; pad to 25+ with variants):
  | id | fixture seed |
  |---|---|
  | `TTRP-WLD-001` | no `uses world`, `[ttrp] world` removed → "set [ttrp] world or pin with uses world" |
  | `TTRP-WLD-002` | `uses world "acme.worlds.prod"` — qname resolves to nothing |
  | `TTRP-WLD-003` | `[ttrp] world` names a def that is not a `def world` |
  | `TTRP-WLD-004` | two storages `staging: true` in the world (D-f: >1 = error) |
  | `TTRP-RES-001` | `load(files.nope)` — unresolved member on a resolvable storage |
  | `TTRP-RES-002` | two imports both exporting `customer` + bare `load(customer)` (same-level ambiguity, C2-d/D-b) |
  | `TTRP-RES-003` | `target files` — resolves, wrong kind (position typing: expected engine, got storage) |
  | `TTRP-RES-004` | `on: relation no_such_rel` / relation exists but not between the joined entities (2 fixtures) |
  | `TTRP-RES-005` | er attribute with no er2db binding in the hosted package |
  | `TTRP-RES-006` | `import erp.nosuch.*` — import path resolves to nothing |
  | `TTRP-SCH-001` | inline `schema: {…}` AND a second inline on the same load / two program-level `def schema sales_csv` (same-level conflict, D-c) |
  | `TTRP-SCH-002` | `load(files.adhoc_csv)` — no schema anywhere (schema-on-read banned, T7) |
  | `TTRP-SCH-003` | declared schema uses type `money` (not in S23 vocabulary) |
  | `TTRP-CFG-001` | `split-policy = "maybe"` in modeler.toml (enum violation; one fixture per bad key, ≥3) |
  | `TTRP-CFG-002` | unknown key `[ttrp] worlds = …` → "did you mean world?" (closed-table suggestion, P2) |
  | `TTRP-MOV-001` | `store(erp_pg)` — engine in store position (storage expected) |
- [ ] Spec skeletons (package `org.tatrman.ttrp.resolve`), red: `TtrpManifestSpec.kt`, `TtrpWorldResolutionSpec.kt`, `TtrpQnameResolutionSpec.kt`, `TtrpErRewriteSpec.kt`, `TtrpSchemaPrecedenceSpec.kt`, `TtrpResolutionNegativeSpec.kt` (table-driven over `negative/`), and in ttrp-cli `TtrpCheckCliSpec.kt`.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*Resolution*'` runs red; fixture world/model files parse standalone via `ttr-parser` (`TtrLoader.parseString` smoke inside `TtrpWorldResolutionSpec` init) — proves fixtures are TTR-M-valid before resolution exists.

### T1.3.2 · `[ttrp]` manifest reader (S5, S18)

- [ ] Add a TOML library to `gradle/libs.versions.toml` (recommend `org.tomlj:tomlj` — pure-JVM, spec-complete; pin the current release) + `implementation(libs.tomlj)` in ttrp-frontend.
- [ ] `org.tatrman.ttrp.project.TtrpManifest`:
  ```kotlin
  data class TtrpManifest(
      val world: String?, val bareTarget: String?, val bareShell: String?,
      val splitPolicy: SplitPolicy = SplitPolicy.WARN,          // warn | error
      val displayDefault: String = "arrow",
      val staging: String?,                                      // only if world doesn't declare staging: true
      val rlsEgress: RlsEgress = RlsEgress.WARN,                 // warn | error (Q8)
      val assistProvenance: AssistProvenance = AssistProvenance.NONE,  // none | comment (C4-d-iii)
      val defaultImports: List<String> = emptyList(),            // S18: BARE-FRAGMENT prelude ONLY
      val manifestDir: java.nio.file.Path,                       // anchor for model-repo-relative paths
  )
  object TtrpManifestReader {
      fun resolve(startDir: Path): TtrpManifestResult   // walk-up to modeler.toml (same rule as TTR-M);
                                                        // no file → all-defaults manifest + marker, NOT an error
  }
  ```
  All keys optional (contracts §2); enum violations → `TTRP-CFG-001` ("split-policy must be warn or error"); unknown keys under `[ttrp]` → `TTRP-CFG-002` with a closed-table nearest-key suggestion (exact table in code: `worlds→world`, `default-import→default-imports`, …; P2 — no fuzzy matching, only the listed pairs). Other tables in the file (e.g. TTR-M's own keys) are ignored, never diagnosed.
- [ ] S18 enforcement shape: `defaultImports` is EXPOSED but consumed only by the bare-fragment wrapper synthesis (P6, Stage 6.3). The resolver in T1.3.4 must NOT read it for `.ttrp` documents — encode that as a spec case now ("canonical doc does not see default-imports": `hero.ttrp` with `default-imports = ["erp.er.*"]` still requires its own `import` for short er names).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpManifestSpec*'` green — walk-up from `programs/`, all 9 keys parsed, defaults correct, `TTRP-CFG-001/002` fixtures green.

### T1.3.3 · ttr-metadata embedding — model repo + world resolution (D-g, offline)

- [ ] Wire the dependency per the pre-flight outcome: `implementation(project(":packages:kotlin:ttr-metadata"))` (if in-repo) or `implementation("org.tatrman:ttr-metadata:<version>")`. ALL model/world reading goes through it — ttrp-frontend must not parse `.ttrm` directly.
- [ ] `org.tatrman.ttrp.resolve.WorldResolver`:
  - Input: `TtrpManifest` + optional `uses world` pin. Precedence: pin > `[ttrp] world` > `TTRP-WLD-001` (contracts §2).
  - Load the model repo from the project (v1 rule: the model repo root is the project root's model directory as ttr-metadata defines it; path configuration beyond that = record in §Blockers if the hero fixture can't express it) — **offline, no service** (D-g).
  - Resolve the world qname → `def world` (else `TTRP-WLD-002`; non-world def → `TTRP-WLD-003`).
  - Materialize `ResolvedWorld(engines, executors, storages(+hosts, staging flags, schemas), worldQname)`; >1 `staging: true` → `TTRP-WLD-004`.
- [ ] `ModelIndex` façade over ttr-metadata: qname → db object | er object | binding chain | world object, package-derived kind (D-a sub-1: the qname path itself distinguishes tiers — no kind sigils).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpWorldResolutionSpec*'` green — `acme.worlds.dev` resolves; engines/storages/staging enumerated; `WLD-001..004` negatives green.

### T1.3.4 · Qname/import resolution with position typing (D-b)

- [ ] `org.tatrman.ttrp.resolve.NameResolver` over the Stage 1.1 `DottedRef`s. Resolution order for canonical documents: **document imports > full qnames** (C2-d's in-ports tier applies only inside fragments — P6); same-level ambiguity → `TTRP-RES-002`, never first-wins (P2).
- [ ] Position typing (each syntactic position checks its kind — D-b/decision 2026-07-03):
  | Position | Accepted kinds | Miss → |
  |---|---|---|
  | `target <x>` (container) | engine instance | `TTRP-RES-003` "expected an engine; `<x>` is a `<kind>`" |
  | `load(<x>, …)` | storage object OR model object (db table / er entity — D-b-iii) | `TTRP-RES-001`/`003` |
  | `store(<x>)` | storage object | `TTRP-MOV-001` |
  | `schema: <x>` | named schema (program-declared or world-declared) | `TTRP-RES-001` |
  | `uses world "<x>"` | world | `TTRP-WLD-002/003` |
  | `on: relation <x>` | er relation between the joined entities | `TTRP-RES-004` |
  | expression column refs | input columns only (Stage 1.2 `TTRP-EXP-001` already enforces) | — |
  Every miss message names BOTH the expected kind and the found kind (position-precise errors are the whole point of D-b-β).
- [ ] Output: `ResolvedDocument` — every `DottedRef` annotated with `ResolvedRef(kind, qname, target)`; unresolved heads → `TTRP-RES-001` ("no <expected-kind> named `<x>` — checked imports [`erp.er.*`] and the world `acme.worlds.dev`"); dangling imports → `TTRP-RES-006`.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpQnameResolutionSpec*'` green — hero refs all resolve with correct kinds; `RES-001/002/003/006`, `MOV-001` negatives green.

### T1.3.5 · er→db early rewrite with provenance (E-d)

- [ ] `org.tatrman.ttrp.resolve.ErRewriter`, running immediately after NameResolver (T8 sugar stratum — BEFORE any graph work): er entity refs → db table refs via er2db binding; er attribute refs in expressions → db column refs; `on: relation customer_sales` → the modeled join condition as a Stage 1.2 `Expression` (equality over the bound key columns, port-qualified `left.…`/`right.…`).
- [ ] **Mandatory provenance:** every rewritten node/expression carries `Provenance(originQname, originName, originLocation)` (e.g. `AMOUNT ← erp.er.sales_txn.amount`). Diagnostics raised on rewritten nodes MUST render the er spelling first: "…`customerType` (bound to `CUST_TYPE`)…" — add a `TtrpDiagnostic.render()` path that consults provenance; spec-case it (the E-d rationale: the analyst wrote `customerType`, never surface `CUST_TYPE` alone).
- [ ] Binding misses: er ref whose entity/attribute has no er2db binding under the world's hosted packages → `TTRP-RES-005` "entity `erp.er.customer` has no er2db binding reachable in world `acme.worlds.dev`; bind it in the model or reference the db object directly".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpErRewriteSpec*'` green — `hero_er.ttrp` fully rewritten to db refs; every rewritten ref has provenance; the relation-join expands to the expected `Expression` tree (snapshot); `RES-004/005` negatives green.

### T1.3.6 · Declared schemas — D-c precedence + S23 types

- [ ] Grammar addition to `TTRP.g4` (small, this stage): program-level `def schema <name> { col: type, … }` statement + inline `schema: { col: type, … }` literal as an `argValue` alt (the `schemaLiteral` placeholder from Stage 1.1 becomes real). Types reuse Stage 1.2's `typeName` production (S23).
- [ ] `SchemaResolver` — the three homes, P2-ordered (D-c-δ): **inline > named-in-program > world-declared**; same-level conflict (duplicate program `def schema` with one name; two inlines on one load) → `TTRP-SCH-001` listing both locations. Ad-hoc `load` of a non-modeled storage object with NO schema in any home → `TTRP-SCH-002` "declare a schema: inline, def schema in the program, or on the storage in the world (schema-on-read is banned, T7)". Unknown type spelling → `TTRP-SCH-003` (closed S23 list; message enumerates the vocabulary).
- [ ] Replace Stage 1.2's `DeclaredSchemaSource` seam: `ResolvedSchemaSource` feeds the typechecker real column lists — model objects from ttr-metadata, ad-hoc from declared schemas. The hero's expressions must now typecheck with NO hand-fed schemas.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpSchemaPrecedenceSpec*'` green — inline beats program beats world (three-layer fixture); `SCH-001/002/003` negatives green; `TtrpHeroExpressionsSpec` (Stage 1.2) still green with hand-fed schemas removed.

### T1.3.7 · `ttrp check` CLI (thin) + Phase-1 DONE sweep

- [ ] `packages/kotlin/ttrp-cli/build.gradle.kts`: add `application` plugin, `mainClass = "org.tatrman.ttrp.cli.MainKt"`. `Main.kt`: subcommand dispatch by hand (`args[0] == "check"` — no CLI framework yet; the full `ttrp` CLI with build/run/explain/conform lands P3 per S2, and the framework choice belongs there).
- [ ] `ttrp check <file>.ttrp`: manifest walk-up from the file's directory → parse → expressions → resolve → er-rewrite → schema+world position checks; print diagnostics one-per-line `FILE:LINE:COL <ID> <message>` + `  ↳ suggested: <alternative>` when present; exit 0 (no ERRORs) / 1 (ERRORs). No graph construction, no emit — front-half only.
- [ ] `TtrpCheckCliSpec.kt` (component test, no process spawn — call the same entry function): check(hero.ttrp) → exit 0, zero ERRORs; check(hero_er.ttrp) → exit 0; check(each negative fixture) → exit 1 with the expected id in output.
- [ ] Phase-1 DONE sweep: run the full matrix below, fix stragglers, count the negative corpus (`ls resolution/negative/*.ttrp expr/negative/* negative/*.ttrp | wc -l` ≥ 25 resolution + the Stage 1.1/1.2 sets).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-cli:test --tests '*TtrpCheckCliSpec*'` green; `./gradlew :packages:kotlin:ttrp-cli:run --args="check packages/kotlin/ttrp-frontend/src/test/resources/resolution/project/programs/hero.ttrp"` → exit 0 by hand.

## Definition of DONE (stage = Phase 1 DONE bar)

- [ ] `ttrp check` passes `hero.ttrp` AND `hero_er.ttrp` (exit 0, zero ERROR diagnostics).
- [ ] ≥25 curated resolution negative fixtures each produce exactly their named diagnostic with suggested alternative.
- [ ] Position typing enforced at `target`/`load`/`store`/`schema:`/`uses world`/`relation` positions with kind-naming messages.
- [ ] Every er-rewritten ref carries provenance; er-spelled diagnostics proven by spec.
- [ ] Schema precedence inline > program > world with same-level conflict = error; S23 type vocabulary enforced.
- [ ] `./gradlew build` green across all modules; whole-repo diagnostics catalogue has no id collisions (add a one-liner spec asserting `TtrpDiagnosticId` id-string uniqueness if not already present).

## Blockers

_(empty — coder records here)_

## References

- **D-b** qname + import mechanism shared with world names, PLUS position typing (each position checks its kind) · **D-b-iii** model objects legal in `load` position · **D-a (γ)** v1 = db + er tiers, ref kind package-derived (sub-1), er depth = names + relation-joins (sub-2).
- **D-c (δ)** schemas in both homes, inline > named-in-program > world-declared, same-level conflict = error · **S23** schema types = TTR db-schema attribute types verbatim (`typeValue`, `TTR.g4` ~line 489) · **T7** schema-on-read banned.
- **D-d (α)** world = TTR `schema world` kind; storage `hosts:` model packages (D-d-i) · **S22** world docs live in the model repo; `[ttrp] world = <qname>` · **D-f** one `staging: true`, >1 = error (`via` override + feasibility = P2 scope).
- **D-g** compiler embeds ttr-metadata; model repo + world read directly via project-default paths, offline, no service.
- **E-d (γ)** er rewrite EARLY with MANDATORY provenance; diagnostics/graphical/lineage render through it.
- **S5/S18** `[ttrp]` manifest table, all-optional keys; `default-imports` = bare-fragment implicit prelude ONLY · contracts §2 (key roster + world precedence), §8 (diagnostics convention).
- **S2** full `ttrp` CLI (build/run/explain/conform) is P3 — `check` here stays a thin dispatch, no framework.
- er-hero source: `docs/ttr-p/design/06-model-binding-options.md` §"The er-flavored hero variant" (fixture adapts `right: sales_txn` → explicit `load`; noted in the fixture header).
- **Known repo gap (pre-flight):** no `ttr-metadata` Gradle module exists in tatrman as of 2026-07-05; architecture §6 says "exists (shared with Ariadne)", contracts §10 lists it as a published artifact, and plan.md makes it the Phase-1 pre-flight. Creating/extracting it is TTR-M-side work OUTSIDE this stage — blocker protocol applies, do not improvise a substitute.
- TOML: `org.tomlj:tomlj` (TOML 1.0-spec parser, pure JVM) — verify current version on Maven Central before pinning in `gradle/libs.versions.toml`.
