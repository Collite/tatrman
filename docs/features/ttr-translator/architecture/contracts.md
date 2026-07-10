# ttr-translator extraction arc — Contracts

> Normative companion to [`architecture.md`](./architecture.md). Everything a consumer (kantheon, ttrp-emit) or the publisher may rely on. Change discipline: append a changelog entry (§7) for any edit after Phase A starts.

## 1. Published artifacts

| Artifact | Gradle module | Content | Direct deps (api) |
|---|---|---|---|
| `org.tatrman:ttr-plan-proto` | `:packages:kotlin:ttr-plan-proto` | generated Kotlin/Java classes for `plan.v1` (3 files), `transdsl.v1`, `dfdsl.v1`, and the `proteus.v1` enum stub `translator.proto` (blocker A2-1), the hand-written `plan.v1.SchemaCodes` helpers, **plus the 6 `.proto` files themselves as jar resources** (import-path contract, §4.1) | `com.google.protobuf:protobuf-kotlin` |
| `org.tatrman:ttr-translator` | `:packages:kotlin:ttr-translator` | the translation core: orchestrator, framework/SPI, schema adapters, joiner, codecs (SQL, TransDSL, DFDSL), wire encode/decode, dialects, params, detect, suggest | `org.tatrman:ttr-plan-proto`, `org.apache.calcite:calcite-core` |
| `ttr-plan-proto` (PyPI) | `packages/python/ttr-plan-proto` | pre-generated `*_pb2.py` at module paths `org/tatrman/{plan,transdsl,dfdsl}/v1/` | `protobuf` |

Versions: the two Maven artifacts are **lockstep** (one version, one tag). The wheel carries the same version by convention. First release: `0.8.0`.

## 2. Proto contract (the transferred wire formats)

Files, byte-identical to kantheon `f2e2efb` at transfer (including all `option` lines — this is what makes the switch invisible):

| File | proto package | java_package | Key messages |
|---|---|---|---|
| `org/tatrman/plan/v1/plan.proto` | `org.tatrman.plan.v1` | `org.tatrman.plan.v1` | `PlanNode`, node kinds, `Expression`, `AggregateCall`, `JoinType`, `SchemaCode` |
| `org/tatrman/plan/v1/context.proto` | `org.tatrman.plan.v1` | `org.tatrman.plan.v1` | `PipelineContext` (imports plan + parameters) |
| `org/tatrman/plan/v1/parameters.proto` | `org.tatrman.plan.v1` | `org.tatrman.plan.v1` | parameter binding shapes |
| `org/tatrman/transdsl/v1/transdsl.proto` | `org.tatrman.transdsl.v1` | (as in source) | TransDSL query AST |
| `org/tatrman/dfdsl/v1/dfdsl.proto` | `org.tatrman.dfdsl.v1` | (as in source) | `Pipeline`, `Operation`, op messages |
| `org/tatrman/proteus/v1/translator.proto` | `org.tatrman.proteus.v1` | `org.tatrman.proteus.v1` | `Language`, `SqlDialect` enums (blocker A2-1; message-only stub — the `proteus.v1` *service* proto stays in kantheon and imports this) |

Hard rules:

- **FQCN stability:** generated class names never change within a major version. `org.tatrman.plan.v1.PlanNode` is the same class name kantheon generated in-repo — consumers switch classpath source, not imports.
- **No `service` blocks** in these files, ever. Service protos (e.g. `proteus.v1`) stay with their services; they *import* these files.
- **Wire compatibility** per protobuf rules: field numbers/types are append-only within `v1`; breaking wire changes require a `v2` package, a new decision, and a major-version artifact bump.
- **Governance (TR-3):** kantheon-driven changes (typically `PipelineContext`) come as PRs here; the publisher cuts a lockstep `kotlin-translator/v*` release promptly. Each proto change gets a changelog entry in §7.

## 3. `ttr-translator` public API surface

Package root `org.tatrman.translator` (TR-2). The API is **the moved API** — no redesign rides the extraction. Entry points (subpackage → role):

| Package | Public surface |
|---|---|
| `orchestrator` | `Translator` (primary entry: parse/translate/unparse across languages), `Optimizer` |
| `framework` | `TranslatorFramework`, **`ModelHandle` SPI** (consumers provide model access — Proteus: `SnapshotModelHandle`; ttrp-emit: world-backed handle), `SchemaVersionVerifier`, `CalciteCharset` |
| `codec.sql` | `SqlParser`, `SqlValidator`, `RelToSqlUnparser` |
| `codec.transdsl` / `codec.dfdsl` | `TransDslCodec` / `DfDslCodec` |
| `wire` | `PlanNodeEncoder`, `PlanNodeDecoder`, `Expressions` (RelNode ↔ `plan.v1`) |
| `dialects` | `Dialects` registry, `DuckDbSqlDialect`, `MssqlSqlDialectWithFloatCast` |
| `schema` | `DbSchemaAdapter`, `ErSchemaAdapter`, `ObjSchemaAdapter`, `SchemaPlusAdapter`, `Resolve`, `Unfold`, `MapToPhysical`, `SavedQueryCalciteTable` |
| `params` | `ParameterBridge`, `ParameterTyper`, `PositionalParameters`, `SurfaceTypeMapping` |
| `detect` / `suggest` | `SchemaDetector`, `TableIdentifierExtractor` / `IdentifierSuggester`, corpus |

Compatibility notes:

- **Binary compatibility with the old in-repo lib is NOT provided** — the package rename is the one deliberate break. Migration = mechanical import rewrite `shared.translator` → `org.tatrman.translator` (§4.2 gives the command). (The in-repo lib's root package was `shared.translator`, **not** `org.tatrman.query.shared.translator` — the source dirs were under `src/main/kotlin/shared/translator/`.)
- Calcite is an `api` dependency (RelNode types appear in signatures). Consumers that must stay Calcite-free (e.g. `ttrp-emit` outside its facade) enforce that on their side (`NoCalciteOutsideFacadeTest`, tasks-p3-s3.1 T3.1.7).
- Test fixtures: `InMemoryModelHandle` ships via `java-test-fixtures` so consumers can test against the SPI without a real model.

## 4. Consumption contracts

### 4.1 kantheon `:shared:proto` (import-path, no codegen)

```kotlin
// shared/proto/build.gradle.kts — after deleting the 5 transferred .proto files
dependencies {
    api(libs.tatrman.ttr.plan.proto) // classes for consumers + .proto files for the protoc include path
    // ... existing deps unchanged
}
```

The protobuf-gradle-plugin extracts `.proto` files found in dependency jars onto the protoc include path **without generating code for them** (see the plugin README, "Protos in dependencies"; local reference: kantheon `shared/proto/build.gradle.kts` already uses the plugin). `import "org/tatrman/plan/v1/plan.proto"` in `proteus.proto`, `theseus.proto`, `argos.proto`, `kyklop.proto`, `worker.proto`, `ariadne.proto`, `security.proto` keeps resolving; generated Kotlin for those services references artifact classes (same FQCNs).

```toml
# gradle/libs.versions.toml
tatrman-translator = "0.8.0"
tatrman-ttr-plan-proto = { module = "org.tatrman:ttr-plan-proto", version.ref = "tatrman-translator" }
tatrman-ttr-translator = { module = "org.tatrman:ttr-translator", version.ref = "tatrman-translator" }
```

Python: the shared-proto Python package stops shipping `plan/transdsl/dfdsl` modules (they vanish with the deleted files); services/workers that import `org.tatrman.plan.v1.plan_pb2` add the `ttr-plan-proto` wheel.

### 4.2 kantheon services (Proteus, Ariadne)

```kotlin
// services/{proteus,ariadne}/build.gradle.kts
- implementation(project(":shared:libs:kotlin:query-translator"))
+ implementation(libs.tatrman.ttr.translator)
```

Import rewrite (repo-wide, then compile):

```bash
grep -rl "shared\.translator" services shared | \
  xargs sed -i '' 's/shared\.translator/org.tatrman.translator/g'
```

(The in-repo root package is `shared.translator` — verified at `f2e2efb`: **89** files match, zero match `org.tatrman.query.shared.translator`. Escaped `.` keeps the match tight; `org.tatrman.translator` does not contain the substring `shared.translator`, so the rewrite is idempotent.)

### 4.3 tatrman `ttrp-emit` (TTR-P Phase 3)

```kotlin
dependencies {
    implementation(project(":packages:kotlin:ttr-translator")) // in-repo — no Maven hop inside the monorepo
}
```

External consumers use the Maven coordinate; the Maven-Local iteration pattern from `PUBLISHING.md` applies (`-Pversion=0.0.1-LOCAL … publishToMavenLocal`).

## 5. Publish contract

| Tag | Publishes |
|---|---|
| `kotlin-translator/v<x.y.z>` | `org.tatrman:ttr-plan-proto` + `org.tatrman:ttr-translator` (lockstep) |
| `python-plan/v<x.y.z>` | `ttr-plan-proto` wheel to PyPI (trusted publishing, mirrors `python/v*`) |

Wired in `.github/workflows/publish.yml` (new tag branch), `justfile` `package` recipe (new prefix row), `PUBLISHING.md` (artifact rows + tag table). `just package kotlin-translator <level|set x.y.z>` is the human entry point.

## 6. Quality gates (both repos)

- tatrman Phase A DONE bar: `./gradlew build` repo-green; `:packages:kotlin:ttr-translator:test` green with the full moved suite (30+ specs); ktlint clean; `kotlin-translator/v0.8.0` visible on GitHub Packages.
- kantheon Phase B DONE bar: `just proto` + `just test-all && just lint-all` green with zero `shared.translator` references remaining (all rewritten to `org.tatrman.translator`) and `shared/libs/kotlin/query-translator` deleted; steropes (Python) tests green against the wheel.

## 7. Changelog

- **v1 · 2026-07-06** — initial contracts (TR-1…TR-8; S25 finalized as ownership transfer).
- **v2 · 2026-07-06** — blocker A2-1 (surfaced during A2 execution, Option A confirmed by Bora): `ttr-plan-proto` also carries `proteus/v1/translator.proto` (the `Language`/`SqlDialect` enum stub, 6th proto) and the hand-written `plan.v1.SchemaCodes` helpers — both wire-adjacent, FQCNs unchanged, needed because `query-translator` compiled against them via `:shared:proto`. The `verifyProtosInJar` guard count is 6. The `proteus.v1` *service* proto stays in kantheon and imports the transferred stub.
- **v3 · 2026-07-06** — review-064 (pre-publish) + A3. (a) **Rename-token correction (F1):** the in-repo lib's root package is `shared.translator`, not `org.tatrman.query.shared.translator` — the §4.2 migration command, §4.2 prose, TR-2, and the §6 Phase B DONE-bar were fixed to the real token (the old command matched 0 files; the real one matches 89). (b) **First release is `0.8.0`** (not the originally-planned `0.1.0` in TR-6/§1 — plan.md already carried 0.8.0; aligned everywhere). (c) A3 publish plumbing landed: `kotlin-translator/v*` (lockstep pair) in `publish.yml`/`justfile`/`PUBLISHING.md`, `python-plan/v*` in `publish-python.yml`.
