# ttr-translator extraction arc — Architecture

> **Status:** planned 2026-07-06 (session with Bora; decisions TR-1…TR-8 below). Executes the cross-repo consequence of TTR-P decision **E-a α′** (2026-07-03) and finalizes **S25** (the plan.v1 call). Companions: [`contracts.md`](./contracts.md) (artifacts, APIs, proto contract) · [`../implementation/v1/plan.md`](../implementation/v1/plan.md) (phased plan + task lists). Kantheon-side pointer + checklists: `Collite/kantheon` → `docs/architecture/fork/ttr-translator-extraction.md` + `docs/implementation/v1/ttr-translator/`.
>
> **Why this arc exists:** TTR-P **Phase 3 is gated** on `org.tatrman:ttr-translator` being resolvable (`docs/ttr-p/implementation/v1/plan.md`, Phase 3 pre-flight). The translator core today lives in kantheon as the in-repo lib `shared/libs/kotlin/query-translator`; this arc moves it here and publishes it — the ttr-metadata/Ariadne pattern, again.

## 1. What moves, from where

Source of truth for the copy: **`Collite/kantheon` @ `f2e2efb02fe9a2d6c243d467ed5725cb50521eec`** (clean tree, surveyed 2026-07-06). If kantheon moves ahead before Phase A executes, re-pin the hash in the A2 pre-flight.

| From (kantheon) | To (tatrman) | Notes |
|---|---|---|
| `shared/libs/kotlin/query-translator` (73 main files, 35 test files + resources) | `packages/kotlin/ttr-translator` | whole lib, incl. TransDSL/DFDSL codecs (TR-1); package rename (TR-2) |
| `shared/proto/src/main/proto/org/tatrman/plan/v1/{plan,context,parameters}.proto` | `packages/kotlin/ttr-plan-proto` | ownership transfer (TR-3), files byte-identical incl. options |
| `shared/proto/src/main/proto/org/tatrman/{transdsl,dfdsl}/v1/*.proto` | `packages/kotlin/ttr-plan-proto` | codec input formats; no other kantheon proto imports them |

What does **not** move: Proteus itself (`services/proteus` — gRPC wrapper, `SnapshotModelHandle`, Ktor/OTel wiring), Ariadne's `QueryParseWorker` (a consumer), the `proteus.v1` service proto, all other kantheon protos (they *import* plan.v1 but stay home).

## 2. Component picture

### Before

```
kantheon
  shared/proto ──(generates org.tatrman.plan.v1 + transdsl.v1 + dfdsl.v1, Kotlin+Python)──┐
  shared/libs/kotlin/query-translator ──(api :shared:proto, api Calcite)                   │
        ▲                    ▲                                                             │
  services/proteus     services/ariadne (QueryParseWorker)          theseus/argos/kyklop/workers
                                                                    (plan.v1 consumers) ◄──┘
tatrman
  ttrp-emit ── Phase 3 BLOCKED: no ttr-translator artifact
```

### After

```
tatrman (upstream — owns the wire formats and the translation core)
  packages/kotlin/ttr-plan-proto ──► org.tatrman:ttr-plan-proto (Maven) + ttr-plan-proto (PyPI wheel)
  packages/kotlin/ttr-translator ──► org.tatrman:ttr-translator (Maven)
        │ api                                     ▲ consumed by
  ttrp-emit (TTR-P Phase 3: island → RelNode → SQL / plan.v1 payloads)
                                                  │
kantheon (downstream — consumes published artifacts, never the reverse)
  shared/proto: plan/transdsl/dfdsl .proto files DELETED; api(org.tatrman:ttr-plan-proto);
                imports resolve from the artifact's bundled .proto files (extracted include path)
  services/proteus + services/ariadne: implementation(org.tatrman:ttr-translator)
  shared/libs/kotlin/query-translator: DELETED
  workers (Python): ttr-plan-proto wheel replaces in-repo plan/v1 generation
```

The repo dependency arrow stays strictly one-way (TTR-P architecture §: kantheon consumes `org.tatrman:*`; tatrman never depends on kantheon artifacts). This arc *strengthens* that: the previously-vendorable wire format now has a single canonical source, here.

## 3. Decisions

- **TR-1 · Whole-lib scope.** All of `query-translator` moves — including the TransDSL and DFDSL codecs and therefore their protos. Proteus stays the thinnest possible wrapper; no API seam is invented mid-library to split codecs out. *(Rejected: core-only split — forces a public SPI boundary that exists only to leave two legacy codecs behind.)*
- **TR-2 · Root package `org.tatrman.translator.*`.** Renamed at extraction from `org.tatrman.query.shared.translator.*`; source dirs normalized to match packages (kantheon kept sources under `src/main/kotlin/shared/translator/` — we don't carry that quirk). Subpackage structure preserved (`codec/`, `detect/`, `dialects/`, `framework/`, `joiner/`, `orchestrator/`, `params/`, `schema/`, `suggest/`, `wire/`). Kantheon's switch is a mechanical import rewrite. *(Matches `ttrp-emit`'s already-written expectation: `TranslatorFacade` is speced as "the ONLY class importing `org.tatrman.translator.*`", tasks-p3-s3.1.)*
- **TR-3 · S25 FINAL CALL = ownership transfer (option C).** The protos are not vendored copies — **tatrman becomes the canonical owner** of `plan.v1`, `transdsl.v1`, `dfdsl.v1`, published as `org.tatrman:ttr-plan-proto` (+ Python wheel). Proto `package` and `java_package` stay **exactly `org.tatrman.plan.v1`** etc., so generated FQCNs are unchanged and every kantheon consumer compiles without edits; kantheon simply stops generating and starts consuming. This is the TTR.g4 model applied to the wire format: one canonical source upstream, published per-language artifacts downstream, no sync discipline. *(Rejected: A — relocated vendored copy with bytes-at-the-boundary [leaves two sources of truth, contradicts the producer-owns-format principle]; B — same-package copy + Gradle exclude discipline [silent duplicate-class failure mode].)*
  - **Guardrails:** (i) language coverage is **demand-driven** — Kotlin/Java + Python wheel now (workers consume plan.v1 in Python); no TS/JS artifact until a consumer exists. (ii) **Governance:** proto changes driven by kantheon runtime needs (e.g. new `PipelineContext` fields for Argos RLS) arrive as PRs to this repo; tatrman commits to prompt lockstep `kotlin-translator/v*` patch/minor releases for them — tatrman is the *custodian*, kantheon remains a first-class *author* of plan.v1 evolution.
- **TR-4 · `context.proto` transfers too.** The separability check (2026-07-06) came back negative on all counts: it shares proto package + directory with `plan.proto`, imports both `plan.proto` and `parameters.proto`, and the translator itself compiles against `PipelineContext` (`Translator.kt`, both codecs, `SchemaVersionVerifier.kt`). The governance rule in TR-3 exists precisely because `PipelineContext` semantics (user, roles, SchemaCode) are kantheon-runtime-owned.
- **TR-5 · Publish = lockstep pair under `kotlin-translator/v<x.y.z>`.** One tag publishes `ttr-plan-proto` + `ttr-translator` at the same version (the `kotlin-metadata` two-artifact lockstep pattern). Not in the `kotlin/v*` bundle — the translator revs on Calcite/wire cadence, not grammar cadence. Python wheel rides its own `python-plan/v<x.y.z>` tag (mirrors `python/v*` for ttr-parser), same version number as the Kotlin pair by convention.
- **TR-6 · First release `0.1.0`;** semver discipline per `PUBLISHING.md` (breaking API/proto change ⇒ minor pre-1.0).
- **TR-7 · Tests move with the code, first.** The 35 Kotest spec files + golden/fixture resources are the extraction's safety net: they are ported and made red *before* main sources land (TDD shape of Phase A.2). No behavioral changes ride the move — the diff vs. kantheon `f2e2efb` is package names + build wiring only.
- **TR-8 · Gate mapping.** TTR-P Phase 3 unblocks at the end of **Phase A** (artifact published / Maven-Local resolvable). Kantheon's **Phase B** (adoption + switch + deletion) is *off* TTR-P's critical path and proceeds on kantheon's own cadence.

## 4. Tech stack (aligned, verified 2026-07-06)

| Concern | tatrman | kantheon | Action |
|---|---|---|---|
| Kotlin | 2.3.0 | 2.3.0 | none |
| JDK toolchain | 21 | 21 | none |
| Kotest / mockk | 6.1.2 / 1.14.9 | 6.1.2 / mockk | none |
| Calcite | — | 1.41.0 | add `calcite = "1.41.0"` to catalog (A1) |
| protobuf | — | 4.33.4, gradle plugin 0.9.5 | add runtime + plugin to catalog (A1) |
| grpc | — (not needed) | 1.78.0 | **not added** — the 5 transferred protos are message-only, no `service` blocks |
| Python wheel machinery | `publish-python.yml` (ttr-parser, PyPI trusted publishing) | `preparePythonPackage` | new `packages/python/ttr-plan-proto` + tag trigger (A3) |

## 5. Flow after the arc (runtime + compile-time)

- **TTR-P compile (offline, in-process):** `ttrp-emit` → `TranslatorFacade` → `org.tatrman.translator` (fresh Calcite planner per island) → dialect SQL, or — when the world's target is a Kantheon engine — `plan.v1` payloads written to `bundle/plans/*.pb` (world-driven, E-a α′).
- **Kantheon runtime:** Proteus's `TranslatorServiceImpl` delegates to the same published library; plan payloads flow Theseus → Argos → Kyklop → workers exactly as today, over unchanged `org.tatrman.plan.v1` classes (now artifact-sourced).
- **Drift guard:** the library's own wire specs (`RoundTripSpec`, `RoundTripMatrixSpec`, `PlanNodeEncoderSpec` — moved with it) + kantheon's integration suite + TTR-P's `ttrp-conform` (Q9) at the far end. No conformance harness *between* copies is needed — there are no copies anymore.

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| kantheon `shared:proto` import resolution breaks when the 5 files are deleted | protobuf-gradle-plugin resolves imports from `.proto` files bundled in dependency jars (extracted include path, no codegen for them); B1 verifies with `just proto` before anything else moves |
| Python codegen of `worker.proto` etc. needs plan.proto on the include path | same extracted-include mechanism feeds all builtins; runtime import satisfied by the wheel; B1 gates on steropes tests |
| Hidden translator dependency on ttr-metadata / ttr-parser types (kantheon CLAUDE.md §7.3 hints "transitively") | A2 pre-flight audits `:shared:libs:kotlin:query-translator:dependencies`; if the SPI leaks such types, add the explicit `api(project(":packages:kotlin:ttr-…"))` dep — they're in-repo here |
| plan.v1 evolution friction after transfer | TR-3 governance rule + lockstep patch releases; recorded in kantheon CLAUDE.md §7.3 during B3 |
| Two repos changing in lockstep | they don't have to: Phase A is self-contained (kantheon untouched); Phase B is kantheon-only against a published artifact; Maven Local (`0.0.1-LOCAL`) for iteration |
