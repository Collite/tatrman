# Tasks · Extraction arc · Stage A1 — Proto module + toolchain

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §1–2, §5.
> **Coder rules:** top-to-bottom; check `[x]` immediately after verification; blocked ⇒ STOP + §Blockers.

## Stage deliverable

`:packages:kotlin:ttr-plan-proto` builds and tests green: generated Kotlin/Java classes for `plan.v1` + `transdsl.v1` + `dfdsl.v1` with **unchanged FQCNs**, `.proto` files bundled as jar resources, wire round-trip proven; Python wheel package scaffolded and building. Protobuf/Calcite toolchain entries land in the version catalog (Calcite is consumed in A2 but pinned here so the catalog change is one commit).

## Pre-flight (all must pass before T-A1.1)

- [ ] `git -C ~/Dev/collite-gh/tatrman status --porcelain` → empty (commit pending work first; the A3 `just package` refuses a dirty tree anyway).
- [ ] `git -C ~/Dev/collite-gh/kantheon rev-parse HEAD` → `f2e2efb02fe9a2d6c243d467ed5725cb50521eec`. If it moved: re-verify the 5 proto files are unchanged (`git -C ~/Dev/collite-gh/kantheon log --oneline -- shared/proto/src/main/proto/org/tatrman/plan shared/proto/src/main/proto/org/tatrman/transdsl shared/proto/src/main/proto/org/tatrman/dfdsl`) and record the new pin under §Blockers-resolved note.
- [ ] `grep -c "service " ~/Dev/collite-gh/kantheon/shared/proto/src/main/proto/org/tatrman/{plan/v1/*.proto,transdsl/v1/*.proto,dfdsl/v1/*.proto}` → all zeros (message-only; confirms no grpc plugins needed).

## Tasks

### T-A1.1 · Version catalog + settings

- [ ] `gradle/libs.versions.toml` additions — versions: `calcite = "1.41.0"`, `protobuf = "4.33.4"`, `protobuf-plugin = "0.9.5"` (mirror kantheon's pins for behavior parity — same rationale as the ttr-metadata comment block); libraries: `calcite-core = { module = "org.apache.calcite:calcite-core", version.ref = "calcite" }`, `protobuf-kotlin = { module = "com.google.protobuf:protobuf-kotlin", version.ref = "protobuf" }`, `protobuf-java-util = { module = "com.google.protobuf:protobuf-java-util", version.ref = "protobuf" }`; plugins: `protobuf = { id = "com.google.protobuf", version.ref = "protobuf-plugin" }`.
- [ ] `settings.gradle.kts`: add `include(":packages:kotlin:ttr-plan-proto")` and `include(":packages:kotlin:ttr-translator")` beside the existing `ttr-*` includes (translator module body lands in A2; an empty dir with a minimal build file is fine now, or defer the include to A2 — pick one, note it in the commit message).
  - **Verify:** `./gradlew help` → configures without errors.

### T-A1.2 · Wire round-trip spec (TEST-FIRST)

- [ ] Create `packages/kotlin/ttr-plan-proto/src/test/kotlin/org/tatrman/plan/v1/WireRoundTripSpec.kt` (Kotest `FunSpec`) BEFORE any proto file lands: (a) build a small `PlanNode` tree programmatically (a scan + filter shape with an `Expression`), `toByteArray()` → `PlanNode.parseFrom()` → equality; (b) same for `PipelineContext` referencing a `PlanNode` and a parameter; (c) a `dfdsl.v1.Pipeline` with two `Operation`s round-trips; (d) assert `PlanNode::class.qualifiedName == "org.tatrman.plan.v1.PlanNode"` (FQCN pin — the contracts §2 stability rule as a test).
  - **Verify:** `./gradlew :packages:kotlin:ttr-plan-proto:test` → FAILS (module/classes missing), for the right reason.

### T-A1.3 · Module scaffold + proto copy

- [ ] `packages/kotlin/ttr-plan-proto/build.gradle.kts`: plugins `kotlin-jvm`, `protobuf` (alias), `ktlint`, `java-library`, `maven-publish`; `kotlin { jvmToolchain(21) }`; deps `api(libs.protobuf.kotlin)`; protobuf block: `protoc { artifact = "com.google.protobuf:protoc:4.33.4" }`, builtins `java` + `kotlin` (NO grpc plugins — pre-flight proved message-only). Reference for the block shape: kantheon `shared/proto/build.gradle.kts` (local example) + the protobuf-gradle-plugin README (`github.com/google/protobuf-gradle-plugin`). Publication block: copy the shape from `packages/kotlin/ttr-metadata/build.gradle.kts` (artifactId `ttr-plan-proto`, same POM/repo config).
- [ ] Copy the 5 files **byte-identical** from the pinned kantheon checkout into `packages/kotlin/ttr-plan-proto/src/main/proto/org/tatrman/{plan/v1,transdsl/v1,dfdsl/v1}/`:
  ```bash
  for f in plan/v1/plan.proto plan/v1/context.proto plan/v1/parameters.proto transdsl/v1/transdsl.proto dfdsl/v1/dfdsl.proto; do
    diff ~/Dev/collite-gh/kantheon/shared/proto/src/main/proto/org/tatrman/$f \
         packages/kotlin/ttr-plan-proto/src/main/proto/org/tatrman/$f || echo "DRIFT: $f"; done
  ```
  → no DRIFT lines. Do NOT edit options, packages, or comments — FQCN stability is the whole point (TR-3).
  - **Verify:** `./gradlew :packages:kotlin:ttr-plan-proto:test` → BUILD SUCCESSFUL, WireRoundTripSpec green.

### T-A1.4 · `.proto` files in the jar (import-path contract)

- [ ] Confirm the jar bundles the `.proto` files (the protobuf plugin does this by default for `java-library`; if not, add a `processResources`/`sourceSets` stanza):
  ```bash
  ./gradlew :packages:kotlin:ttr-plan-proto:jar && \
  jar tf packages/kotlin/ttr-plan-proto/build/libs/*.jar | grep -c "\.proto$"
  ```
  → `5`. This is contracts §4.1 — kantheon's protoc include path depends on it. Add a unit-shaped guard test (`JarContainsProtosSpec` reading the jar via the classpath or a Gradle check task) so a plugin upgrade can't silently drop them.
  - **Verify:** command above prints 5; guard test green.

### T-A1.5 · Python wheel package

- [ ] Scaffold `packages/python/ttr-plan-proto/` mirroring `packages/python/ttr-parser/` (pyproject with `version = "0.0.0"` placeholder for tag injection; build hook generates `*_pb2.py` from `../../kotlin/ttr-plan-proto/src/main/proto/` via `grpcio-tools`' bundled protoc — pure-Python wheel, no JVM; runtime dep `protobuf`). Module layout must satisfy the imports kantheon's generated code emits: `from org.tatrman.plan.v1 import plan_pb2` ⇒ packages `org/tatrman/plan/v1/` etc. with `__init__.py` files.
- [ ] Smoke test (`packages/python/ttr-plan-proto/tests/test_roundtrip.py`, pytest): import `plan_pb2`, build a `PlanNode`, `SerializeToString()`/`FromString` round-trip.
  - **Verify:** `cd packages/python/ttr-plan-proto && pipx run build --wheel && python -m pytest tests/` → wheel built, test green.

### T-A1.6 · Provenance + CI

- [ ] `packages/kotlin/ttr-plan-proto/README.md`: canonical-owner statement (these files are the **source of truth** as of this arc; formerly kantheon `shared/proto` — transferred at `f2e2efb`, 2026-07-06); governance rule (contracts §2); pointer to `docs/ttr-translator/`.
- [ ] Wire the module into the existing Kotlin CI job (whatever runs `./gradlew build` — confirm it picks the new module up automatically; if the workflow enumerates modules, add it).
  - **Verify:** CI config references or globs the module; `./gradlew build` repo-green locally.

## Definition of DONE (stage)

- [ ] `./gradlew :packages:kotlin:ttr-plan-proto:build` green (round-trip + FQCN pin + jar-protos guard).
- [ ] 5/5 proto files byte-identical to the kantheon pin (diff loop clean).
- [ ] Wheel builds + pytest green.
- [ ] Repo-wide `./gradlew build` and ktlint green.
- [ ] Committed as `Extraction A1: ttr-plan-proto module + toolchain`.

## Blockers

_(empty)_
