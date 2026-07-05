# Tasks · P2 · Stage 2.2 — Manifests + world binding

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

Capability knowledge wired under the graph. Plan stage line: *"engine-type manifest format + PG/Polars/bash manifests (T6 β entries); world instance `extends` overlay; capability check (node + function granularity); invocation-binding resolution table (F-c); staging feasibility check (D-f)."* At the end of this stage: the compiler ships three engine-type manifests (postgres-16, polars, bash) in a concrete serialization; the resolved world overlays instances onto types (`extends` + deltas); a `CapabilityChecker` reports every (node, engine) and (function, engine) miss over a Stage-2.1 graph; every container resolves an invocation binding from the (data engine, executor) manifest pair; and staging is resolved + feasibility-checked. Output = data structures + diagnostics that Stage 2.3's rewriter consumes (T6-d α: manifests are facts, rewrite rules are compiler knowledge — the normalizer joins them). Code lives in `packages/kotlin/ttrp-graph` under `org.tatrman.ttrp.graph.capability` / `.world`.

> **AMENDED 2026-07-05 (ttr-metadata feature approved):** world loading/overlay mechanics are library-side — `WorldResolver` delivers `ResolvedWorld` with manifest *content* transported as data on `ResolvedEngine/Executor.manifest` (ttr-metadata contracts §3; overlay rule RM7: instance wins, type fills gaps, lists replaced). This stage OWNS the type-manifest format, the shipped manifest files, their interpretation (`CapabilityChecker`, invocation-binding table) and all `TTRP-*` diagnostics — consume `ResolvedWorld` instead of re-implementing world parsing/overlay. The `extends` bare-id pass-through (`extendsRef`, RM6) is the seam: this stage resolves `extendsRef` against its shipped manifests. Pre-flight gains: ttr-metadata M2 DONE.

**Reviewable choice made by this stage (flag in the PR):** contracts.md deliberately leaves the engine-**type** manifest serialization open (T6-c: "format of ii deliberately open", world/instances are TTR-family, type manifests are "a compiler-shipped data artifact (format open)"). This stage picks **JSON via kotlinx-serialization** (already in the version catalog; same reasoning as F-f-i's JSON run-manifest: machine record, diffable, no new grammar). T2.2.2 documents the schema; a reviewer may overturn to TTR-ish text later — keep the reader behind an interface so only the loader changes.

## Pre-flight (all must pass before T2.2.1)

- [ ] Stage 2.1 DONE bar checked off; `./gradlew :packages:kotlin:ttrp-graph:test` — green.
- [ ] Stage 1.3 world resolution works: `grep -rln "schema world" packages/kotlin/ttrp-frontend/src` finds the world-doc handling + a test world fixture (`def world` with `def engine`/`def executor`/`def storage`). Record the fixture path — this stage reuses it. If Stage 1.3 shipped no reusable world fixture, creating one in T2.2.1 is in-scope (not a blocker).
- [ ] `grep -n "kotlinx-ser-json\|kotlinx.serialization" gradle/libs.versions.toml` — serialization lib present in the catalog.
- [ ] Confirm where the T5-c function-catalogue interface landed in Phase 1 (Stage 1.2 "catalogue-id function resolution"): `grep -rn "catalog" packages/kotlin/ttrp-frontend/src/main --include=*.kt -il | head`. Manifests reference functions **by catalogue id** (T6 β) — record the id type/FQN.

## Tasks

### T2.2.1 · Test corpus + spec skeletons (TEST-FIRST)

- [ ] Fixture tree `packages/kotlin/ttrp-graph/src/test/resources/fixtures/`:
  - `manifests/` — the three v1 manifests (contents pinned in T2.2.3; create as empty JSON stubs now so specs compile).
  - `world/acme_test.ttrm` — the test world (reuse/extend the Stage-1.3 fixture; shape per D-d content sketch):
    ```ttr
    schema world
    def world acme_test {
        def engine erp_pg    { extends: postgres-16 }
        def engine polars    { extends: polars }
        def executor sh      { extends: bash }
        def storage erp_db   { type: postgres, via: erp_pg, namespaces: [erp], hosts: [erp] }
        def storage files    { type: local_dir, path: "./data" }
        def storage stage    { type: local_dir, path: "./stage", staging: true }
    }
    ```
    (Exact property spellings follow the Stage-1.3 world grammar as implemented — if that grammar differs from this sketch, follow the grammar and note the delta in a fixture comment. If the Stage-1.3 grammar cannot express `extends`/`hosts:`/`staging: true` at all, STOP — §Blockers; those are D-d/D-f load-bearing.)
  - `world/neg/` negative worlds + programs, with expected diagnostics:
    - `unknown-type.ttrm` (engine `extends: mssql-2019`, no such shipped manifest) → `TTRP-WLD-001` ("unknown engine type 'mssql-2019'; shipped types: postgres-16, polars, bash").
    - `two-staging.ttrm` (two storages `staging: true`) → `TTRP-WLD-002` ("exactly one staging storage; found: stage, stage2" — D-f α).
    - `no-staging.ttrm` + a program with a cross-engine edge and no `[ttrp] staging` → `TTRP-WLD-003` ("no staging declared: mark one storage `staging: true` or set `[ttrp] staging`" — contracts §2).
    - `no-binding.ttrm` (executor whose manifest lacks a polars invocation) + polars program → `TTRP-WLD-004` ("no invocation binding for (polars, <executor>); v1 bash supports: pg, polars, display" — F-c).
    - `unreachable-staging.ttrp`/`.ttrm` (staging storage not readable/writable by one side's engine) → `TTRP-MOV-001` ("cannot stage between erp_pg and polars via 'stage': polars cannot read it" — T6-e "no transfer path").
    - `no-load-path.ttrp` (container loads from a storage its engine has no read relation to) → `TTRP-MOV-002` ("engine 'polars' cannot read storage 'erp_db'; load it in a container targeting erp_pg or let movement synthesis carry it" — T6-e "no Load path").
- [ ] Spec skeletons (Kotest `StringSpec`, failing bodies) in `.../org/tatrman/ttrp/graph/`: `capability/ManifestReaderSpec.kt`, `capability/CapabilityCheckSpec.kt`, `capability/InvocationBindingSpec.kt`, `world/WorldOverlaySpec.kt`, `world/StagingFeasibilitySpec.kt`. Case rosters in T2.2.2–T2.2.7.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test` — compiles; new specs red, Stage-2.1 specs still green.

### T2.2.2 · Engine-type manifest format + reader (T6 β)

- [ ] Define the schema as Kotlin `@Serializable` types in `org.tatrman.ttrp.graph.capability`:
  - `EngineTypeManifest { manifestVersion: Int = 1, id: String /* "postgres-16" */, kind: DATA|EXECUTION|STORAGE_TYPE, languageDetails: {dialect, identifierNormalization}?, nodes: Map<NodeKind, NodeCapability>, functions: List<CatalogueId>, controls: List<FS|SS>?, parallelism: NONE|WAVE?, invocations: List<Invocation>? }`.
  - `NodeCapability` = the T6-a β parameterized entry, one small constraint vocabulary per node kind where support actually varies (decision B-T6): `Join{types: [inner,left,right,full,semi,anti,cross]}`, `Aggregate{functions: [CatalogueId], distinct: Boolean}`, `Union{all: Boolean, distinct: Boolean}`, `Pivot{native: Boolean}`; all other kinds = the empty entry (α as degenerate β). NO predicate escape hatch in v1 code — the manifest stays pure data (the "rare named predicate" of T6-a is design headroom, not a v1 feature; leave a comment).
  - `Invocation { targetEngineType: String, delivery: String, command: String?, interpreter: String?, packages: Map<String,String>? }` — execution-engine manifests declare invocation capabilities (B-T6 invocation-capabilities decision).
- [ ] Reader behind `interface ManifestSource { fun load(id: String): EngineTypeManifest? ; fun all(): List<EngineTypeManifest> }` with a classpath implementation reading `src/main/resources/ttrp/manifests/*.json` (shipped with the compiler — T6-b type layer). Unknown fields = error (strict decode; P2 — a typo'd capability must not silently vanish).
- [ ] `ManifestReaderSpec` cases: "reads a data-engine manifest with parameterized join entry", "reads an execution-engine manifest with invocations", "unknown node kind fails strictly", "unknown top-level field fails strictly", "manifestVersion mismatch fails".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*ManifestReaderSpec'` — green.

### T2.2.3 · The three v1 manifests: postgres-16, polars, bash

- [ ] Author `packages/kotlin/ttrp-graph/src/main/resources/ttrp/manifests/{postgres-16,polars,bash}.json`. Pinned content decisions (each is a (node, engine) capability call — cite in file comments... JSON has none, so mirror the rationale in `ManifestReaderSpec` assertions):
  - **postgres-16** (kind DATA, dialect `postgres`, identifierNormalization `lower`): nodes Project, Filter, Join{all 7 types}, Aggregate{functions: catalogue ids for sum,avg,min,max,count; distinct: true}, Sort, Union{all: true, distinct: true}, Intersect, Except, Values, Limit, Pivot{native: false}, Load, Store, Index. **No Branch, no Switch, no Display** (Branch/Switch are macros for SQL — B-T10; Display is delivered by the executor binding, not the data engine).
  - **polars** (kind DATA, dialect `polars`): nodes Project, Filter, Join{inner,left,full,semi,anti,cross — no `right`: lowered by input swap, a 2.3 rewrite}, Aggregate{same core functions, distinct: true}, Sort, Union{all: true, distinct: true}, Values, Limit, Load, Store. **No Branch/Switch** (lowered to Filter in v1 — keeps the hero's Branch exercising the 2.3 lowering), **no Intersect/Except** (join-pattern lowering per B-T10 sweep), **no Pivot, no Index**.
  - **bash** (kind EXECUTION): controls [FS, SS], parallelism WAVE (F-a β — this file IS that decision as data), invocations: `{postgres → delivery "psql", command "psql -v ON_ERROR_STOP=1 --no-psqlrc -f"}`, `{polars → delivery "python3", interpreter "python3.13", packages {"polars": ">=1"}}`, `{display → delivery "file-drop"}` (F-c ratified table; contracts §6).
  - Function lists: seed each DATA manifest from the Phase-1 catalogue's v1 ids (everything the hero + Stage-1.2 golden expressions use, incl. comparison/logical/arithmetic, `coalesce`, `is null`); polars and pg lists deliberately differ somewhere benign (e.g. a regex function pg-only) so CAP-002 is testable against a real gap.
- [ ] `ManifestReaderSpec` additions: "postgres-16 lacks Branch", "polars lacks Intersect and right join", "bash declares FS SS and three invocations", "pg and polars function sets differ where expected".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*ManifestReaderSpec'` — green.

### T2.2.4 · World instance `extends` overlay → `ResolvedWorld`

- [ ] `org.tatrman.ttrp.graph.world.WorldBinder`: takes the Stage-1.3 resolved world doc (via ttr-metadata / frontend) + `ManifestSource` → `ResolvedWorld { engines: Map<qname, ResolvedEngine>, executors, storages, staging: ResolvedStorage?, hosts: Map<modelPackage, storage> }`. `ResolvedEngine` = type manifest **+ instance deltas** (T6-b: `extends` type, overlay `+functions{…}` e.g. Postgres extensions, instance-level read/write relations); relations (which engine reads/writes which storage) are instance-level, derived from the world doc (`via:`, storage types) — encode the derivation rule explicitly in KDoc (P2: no reachability guessing).
- [ ] Unknown `extends` target → `TTRP-WLD-001`. Staging cardinality: >1 → `TTRP-WLD-002`; 0 and no `[ttrp] staging` → `TTRP-WLD-003` (deferred until something needs staging — emit at feasibility time, T2.2.7, not at bind time; a single-engine program with no crossing needs none).
- [ ] `WorldOverlaySpec` cases: "instance extends type and inherits nodes", "instance function delta adds to type set", "unknown engine type yields TTRP-WLD-001", "two staging storages yield TTRP-WLD-002", "hosts maps model package to storage", "read write relations derived per rule".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*WorldOverlaySpec'` — green.

### T2.2.5 · Capability check — node + function granularity

- [ ] `org.tatrman.ttrp.graph.capability.CapabilityChecker`: for each container (author-assigned target), walk member nodes: (1) node kind native? — consult the β entry incl. parameters (join type present? aggregate function + distinct? pivot native?); (2) every expression's catalogue-id function in the engine's function set? Output = ordered `List<CapabilityMiss>` (`NodeMiss(node, engine, param?)` | `FunctionMiss(node, functionId, engine)`), document-ordered (determinism for 2.3). **This stage only reports; it never rewrites or escalates** (T6-d α: native? → rewrite exists? → escalate is the 2.3 normalizer's join).
- [ ] Diagnostics per the T6-e table, informational at this stage: `TTRP-CAP-001` "node <kind> is not native on <engine>" (info — 2.3 decides lower vs escalate), `TTRP-CAP-002` "function <id> is not supported on <engine>" (info). Severity escalation to warn/error is Stage 2.3b's split-policy job — do not encode policy here.
- [ ] `CapabilityCheckSpec` cases: "hero branch on polars reported as node miss", "join type parameter miss detected (right join on polars)", "aggregate function id miss detected", "pg-only function inside polars container reported as function miss", "fully native container reports empty", "misses are document-ordered".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*CapabilityCheckSpec'` — green.

### T2.2.6 · Invocation-binding resolution (F-c)

- [ ] `InvocationBindingResolver`: per container, resolve (container's data engine type, program executor type) → the executor manifest's matching `Invocation`; Display leaves resolve (display, executor) → file-drop. Missing pair → `TTRP-WLD-004` error with the supported list as suggested alternative. Result feeds the 2.3b island→payload map and, in P3, the bundle `manifest.json` `invocation` field (contracts §5).
- [ ] `InvocationBindingSpec` cases: "pg container binds psql with exact command string" (assert the full `psql -v ON_ERROR_STOP=1 --no-psqlrc -f` — contracts §6 verbatim), "polars container binds python3 with interpreter and packages", "display leaf binds file-drop", "missing pair yields TTRP-WLD-004".
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test --tests '*InvocationBindingSpec'` — green.

### T2.2.7 · Staging resolution + feasibility (D-f) — hero component test

- [ ] `StagingResolver`: staging storage = world `staging: true` (D-f α) else `[ttrp] staging` (contracts §2 "only if world doesn't declare") else — when a crossing exists — `TTRP-WLD-003`. **Feasibility (D-f β as check, never as picker):** for each potential cross-engine pair in the program, verify both engines reach the staging storage via the ResolvedWorld read/write relations — writer side must write it, reader side must read it — else `TTRP-MOV-001`. Per-edge/container `via <storage>` override (D-f γ, C3-d-iv): validate the named storage exists + same feasibility check; the override value is recorded on the edge for 2.3b synthesis. Also: direct load feasibility — container loads storage its engine can't read → `TTRP-MOV-002`.
- [ ] Component test closing the stage (in `StagingFeasibilitySpec` or a new `WorldBindingIntegrationSpec`): hero + `world/acme_test.ttrm` end-to-end — ResolvedWorld binds erp_pg/polars/sh; capability check reports exactly {Branch on polars} as node miss (and nothing else); invocation bindings = {acc_prep→psql, crunch→python3, display→file-drop}; staging = `stage`, feasible for the acc_prep→crunch crossing; zero error diagnostics.
- [ ] `StagingFeasibilitySpec` cases: "world staging wins over project key", "missing staging with crossing yields TTRP-WLD-003", "unreachable staging yields TTRP-MOV-001", "via override validated and recorded", "unreadable load storage yields TTRP-MOV-002", "hero binds clean end-to-end".
- [ ] Register all new `TTRP-CAP-*`, `TTRP-WLD-*`, `TTRP-MOV-*` ids + suggested alternatives in the diagnostics catalogue (contracts §8).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-graph:test` — module green; `./gradlew build` green repo-wide.

## Definition of DONE (stage)

- [ ] Three shipped manifests load strictly; contents match T2.2.3's pinned decisions (asserted, not eyeballed).
- [ ] `ResolvedWorld` overlays `extends` + deltas; hosts + relations derived by a documented rule.
- [ ] Capability checker reports node- and function-granular misses, parameter-aware (join types, aggregate functions/distinct, pivot native), document-ordered, policy-free.
- [ ] Invocation bindings resolve for pg/polars/display × bash with contracts-§6-verbatim command strings; missing pair is a named error.
- [ ] Staging: declared default + feasibility check + `via` override, all diagnosed (`TTRP-WLD-002/003`, `TTRP-MOV-001/002`).
- [ ] Hero component test green: exactly one capability miss (Branch@polars), three bindings, feasible staging.
- [ ] Manifest serialization choice (JSON) flagged as reviewable in the stage PR description.
- [ ] All new diagnostic ids in the catalogue with suggested alternatives.

## Blockers

_(empty — coder records here)_

## References

- **Decisions:** B-T6 (β parameterized entries · world = TTR doc + compile target · manifests ≠ rewrite rules (α) · two-layer type/instance `extends` · data/execution taxonomy · invocation capabilities · derived-only execution layer), B-T4 (world concepts from Tatrman's `World` — reuse relations, not representation; `getEnvForTransfer` becomes feasibility-check-only per D-f), T6-e (diagnostics table this stage implements), D-d/D-d-i (schema world; `hosts:`), D-e-α/S5 (`[ttrp]` keys incl. `staging`), D-f (declared default + feasibility + `via`), F-a β / F-c / F-d (bash manifest content = those decisions as data), S22 (worlds live in the model repo), S23 (schema types verbatim), Q8 (`rls: true` flag rides storage — tripwire itself fires at movement/emit, later phases).
- **Docs:** `architecture.md` §3 ("The world", taxonomy) · `contracts.md` §2, §5–6, §8 · `02-internal-model-options.md` §T6 (incl. the content-model α/β/γ analysis) · `06-model-binding-options.md` (world sketch, D-f) · `08-orchestration-options.md` (F-a/F-c ratifications).
- **Repo conventions:** kotlinx-serialization test-scope precedent in `ttr-parser/build.gradle.kts` (move to `implementation` scope for this module — the manifest reader is runtime); strict-JSON decode via `Json { ignoreUnknownKeys = false }`.
